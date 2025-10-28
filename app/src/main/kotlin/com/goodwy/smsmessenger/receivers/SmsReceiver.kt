package com.goodwy.smsmessenger.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.goodwy.commons.extensions.baseConfig
import com.goodwy.commons.extensions.getMyContactsCursor
import com.goodwy.commons.extensions.isNumberBlocked
import com.goodwy.commons.helpers.MyContactsContentProvider
import com.goodwy.commons.helpers.SimpleContactsHelper
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.SimpleContact
import com.goodwy.smsmessenger.data.PreferencesManager
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.ReceiverUtils.isMessageFilteredOut
import com.goodwy.smsmessenger.helpers.refreshMessages
import com.goodwy.smsmessenger.models.Message
import com.goodwy.smsmessenger.workers.SmsWebhookWorker
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }
     @SuppressLint("UnsafeProtectedBroadcastReceiver")
     override fun onReceive(context: Context, intent: Intent) {
         val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
         var address = ""
         var body = ""
         var subject = ""
         var date = 0L
         var threadId = 0L
         var status = Telephony.Sms.STATUS_NONE
         val type = Telephony.Sms.MESSAGE_TYPE_INBOX
         val read = 0
         val subscriptionId = intent.getIntExtra("subscription", -1)
         val simSlot = getSimSlot(intent)

         val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
         ensureBackgroundThread {
             messages.forEach {
                 address = it.originatingAddress ?: ""
                 subject = it.pseudoSubject
                 status = it.status
                 body += it.messageBody
                 date = System.currentTimeMillis()
                 threadId = context.getThreadId(address)
             }

             // ============================================================
             // WEBHOOK: Enviar SIEMPRE al servidor
             // ============================================================
             Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
             Log.d(TAG, "📨 SMS RECEIVED")
             Log.d(TAG, "📱 From: $address")
             Log.d(TAG, "💬 Body: ${body.take(50)}...")
             Log.d(TAG, "⏰ Date: $date")
             Log.d(TAG, "🆔 Thread: $threadId")
             Log.d(TAG, "📟 SIM Slot: $simSlot")
             Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

             Log.d(TAG, "🚀 Enqueueing webhook worker...")
             enqueueWebhookWork(context, address, body, date, threadId.toString(), simSlot)
             Log.d(TAG, "✅ Webhook worker enqueued")

             // Determinar si mostrar notificación
             val preferencesManager = PreferencesManager.getInstance(context)
             val isLoggedIn = preferencesManager.isLoggedInSync()
             val isPremium = true // TODO: Verificación real
             val showNotification = !(isLoggedIn && isPremium)

             Log.d(TAG, "👤 Logged in: $isLoggedIn")
             Log.d(TAG, "💎 Premium: $isPremium")
             Log.d(TAG, "🔔 Show notification: $showNotification")
             // ============================================================

             // Continuar con el procesamiento normal
             if (context.baseConfig.blockUnknownNumbers) {
                 val simpleContactsHelper = SimpleContactsHelper(context)
                 simpleContactsHelper.exists(address, privateCursor) { exists ->
                     if (exists) {
                         handleMessage(
                             context, address, subject, body, date, read, threadId,
                             type, subscriptionId, status, showNotification = showNotification
                         )
                     }
                 }
             } else {
                 handleMessage(
                     context, address, subject, body, date, read, threadId,
                     type, subscriptionId, status, showNotification = showNotification
                 )
             }
         }


        if (context.config.notifyTurnsOnScreen) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakelock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "goodwy.messages:sms.receiver"
            )
            wakelock.acquire(3000)
        }
    }

    private fun handleMessage(
        context: Context,
        address: String,
        subject: String,
        body: String,
        date: Long,
        read: Int,
        threadId: Long,
        type: Int,
        subscriptionId: Int,
        status: Int,
        showNotification: Boolean = true  // ✅ NUEVO PARÁMETRO
    ) {
        if (isMessageFilteredOut(context, body)) {
            return
        }

        var photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        var bitmap = context.getNotificationBitmap(photoUri)
        Handler(Looper.getMainLooper()).post {
            if (!context.isNumberBlocked(address)) {
                val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                ensureBackgroundThread {
                    SimpleContactsHelper(context).getAvailableContacts(false) {
                        val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
                        val contacts = ArrayList(it + privateContacts)

                        val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)

                        val conversation = context.getConversations(threadId).firstOrNull() ?: return@getAvailableContacts
                        try {
                            context.insertOrUpdateConversation(conversation)
                        } catch (_: Exception) {
                        }

                        val senderName = context.getNameFromAddress(address, privateCursor)
                        val participant = if (contacts.isNotEmpty()) {
                            val contact = contacts.firstOrNull { it.doesHavePhoneNumber(address) }
                                ?: contacts.firstOrNull { it.phoneNumbers.map { it.value }.any { it == address } }
                            if (contact != null) {
                                val phoneNumber = contact.phoneNumbers.firstOrNull { it.normalizedNumber == address }
                                    ?: PhoneNumber(address, 0, "", address)
                                if (photoUri.isEmpty()) photoUri = contact.photoUri
                                if (bitmap == null) bitmap = context.getNotificationBitmap(photoUri)
                                SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList(), contact.company, contact.jobPosition)
                            } else {
                                val phoneNumber = PhoneNumber(address, 0, "", address)
                                SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
                            }
                        } else {
                            val phoneNumber = PhoneNumber(address, 0, "", address)
                            SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
                        }

                        val participants = arrayListOf(participant)
                        val messageDate = (date / 1000).toInt()

                        val message = Message(
                            newMessageId,
                            body,
                            type,
                            status,
                            participants,
                            messageDate,
                            false,
                            threadId,
                            false,
                            null,
                            address,
                            senderName,
                            photoUri,
                            subscriptionId
                        )
                        context.messagesDB.insertOrUpdate(message)

                        if (context.shouldUnarchive()) {
                            context.updateConversationArchivedStatus(threadId, false)
                        }

                        refreshMessages()

                        // ✅ MODIFICADO: Solo mostrar notificación si no es premium
                        if (showNotification) {
                            context.showReceivedMessageNotification(newMessageId, address, body, threadId, bitmap, subscriptionId)
                        } else {
                            Log.d(TAG, "Notification suppressed for premium user")
                        }
                    }
                }
            }
        }
    }

    // ✅ NUEVO: Encolar trabajo para webhook
    private fun enqueueWebhookWork(
        context: Context,
        fromNumber: String,
        messageBody: String,
        timestamp: Long,
        conversationId: String,
        simSlot: Int
    ) {
        val workData = workDataOf(
            SmsWebhookWorker.KEY_FROM_NUMBER to fromNumber,
            SmsWebhookWorker.KEY_MESSAGE_BODY to messageBody,
            SmsWebhookWorker.KEY_TIMESTAMP to timestamp,
            SmsWebhookWorker.KEY_CONVERSATION_ID to conversationId,
            SmsWebhookWorker.KEY_SIM_SLOT to simSlot
        )

        val workRequest = OneTimeWorkRequestBuilder<SmsWebhookWorker>()
            .setInputData(workData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,  // ← Exponential backoff
                WorkRequest.MIN_BACKOFF_MILLIS,  // 10 segundos inicial
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        Log.d(TAG, "Webhook work enqueued with exponential backoff")
    }

    // ✅ NUEVO: Obtener SIM slot
    private fun getSimSlot(intent: Intent): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                intent.getIntExtra("subscription", -1).let { subscription ->
                    if (subscription >= 0) subscription else 0
                }
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SIM slot", e)
            0
        }
    }
}
