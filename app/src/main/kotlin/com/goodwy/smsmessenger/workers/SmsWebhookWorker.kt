package com.goodwy.smsmessenger.workers

import android.Manifest
import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goodwy.smsmessenger.databases.MessagesDatabase
import com.goodwy.smsmessenger.helpers.getDeviceId
import com.goodwy.smsmessenger.models.ReceivedSms
import com.goodwy.smsmessenger.models.SmsWebhookPayload
import com.goodwy.smsmessenger.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsWebhookWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SmsWebhookWorker"

        const val KEY_FROM_NUMBER = "from_number"
        const val KEY_MESSAGE_BODY = "message_body"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_SIM_SLOT = "sim_slot"
    }

    private val database = MessagesDatabase.getInstance(context)
    private val smsDao = database.SmsDao()
    private val apiService = RetrofitClient.getApiService(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val fromNumber = inputData.getString(KEY_FROM_NUMBER)
                ?: return@withContext Result.failure().also {
                    Log.e(TAG, "❌ Missing from_number")
                }

            val messageBody = inputData.getString(KEY_MESSAGE_BODY)
                ?: return@withContext Result.failure().also {
                    Log.e(TAG, "❌ Missing message_body")
                }

            val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
            val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: "unknown"
            val simSlot = inputData.getInt(KEY_SIM_SLOT, 0)

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🔄 Processing webhook for SMS")
            Log.d(TAG, "📱 From: $fromNumber")
            Log.d(TAG, "💬 Body: ${messageBody.take(50)}...")
            Log.d(TAG, "⏰ Timestamp: $timestamp")
            Log.d(TAG, "📍 Conversation ID: $conversationId")
            Log.d(TAG, "📟 SIM Slot: $simSlot")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // 1. Guardar en base de datos local
            val deviceId = getDeviceId(applicationContext)
            Log.d(TAG, "🆔 Device ID: $deviceId")

            val receivedSms = ReceivedSms(
                fromNumber = fromNumber,
                toNumber = getMyPhoneNumber(),
                message = messageBody,
                timestamp = timestamp,
                deviceId = deviceId,
                sentToServer = false
            )

            val smsId = smsDao.insertSms(receivedSms)
            Log.d(TAG, "💾 SMS saved to local database with ID: $smsId")

            // 2. Enviar webhook al servidor
            val webhookPayload = SmsWebhookPayload(
                from = fromNumber,
                body = messageBody,
                timestamp = timestamp / 1000, // Convertir a segundos
                simSlot = simSlot,
                conversationId = conversationId,
                deviceId = deviceId
            )

            Log.d(TAG, "📤 Sending to server...")
            val success = sendWebhookToServer(webhookPayload)

            return@withContext if (success) {
                // Marcar como enviado en la BD
                val updatedSms = receivedSms.copy(id = smsId, sentToServer = true)
                smsDao.updateSms(updatedSms)
                Log.d(TAG, "✅ Webhook sent successfully - marked as sent in DB")
                Result.success()
            } else {
                Log.w(TAG, "⚠️ Failed to send webhook, will retry")

                // Retry con exponential backoff (WorkManager lo maneja automáticamente)
                if (runAttemptCount < 3) {
                    Log.d(TAG, "🔄 Retry attempt ${runAttemptCount + 1}/3")
                    Result.retry()
                } else {
                    Log.e(TAG, "❌ Max retries (3) reached, giving up")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error processing webhook", e)
            Log.e(TAG, "💥 Error message: ${e.message}")
            Log.e(TAG, "💥 Stack trace: ${e.stackTraceToString()}")

            // Reintentar hasta 3 veces
            return@withContext if (runAttemptCount < 3) {
                Log.d(TAG, "🔄 Retry attempt ${runAttemptCount + 1}/3 due to exception")
                Result.retry()
            } else {
                Log.e(TAG, "❌ Max retries (3) reached after exception, giving up")
                Result.failure()
            }
        }
    }

    private suspend fun sendWebhookToServer(payload: SmsWebhookPayload): Boolean {
        return try {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🚀 Sending webhook to server")
            Log.d(TAG, "📍 Payload: $payload")

            val response = apiService.sendInboundSms(payload)

            Log.d(TAG, "📨 Response received")
            Log.d(TAG, "📨 HTTP Code: ${response.code()}")
            Log.d(TAG, "📨 HTTP Message: ${response.message()}")

            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "✅ Success: ${body?.success}")
                Log.d(TAG, "✅ Message: ${body?.message}")
                Log.d(TAG, "✅ SMS ID: ${body?.smsId}")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                body?.success ?: false
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ Server returned error")
                Log.e(TAG, "❌ HTTP Code: ${response.code()}")
                Log.e(TAG, "❌ HTTP Message: ${response.message()}")
                Log.e(TAG, "❌ Error body: $errorBody")
                Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Network error sending webhook")
            Log.e(TAG, "💥 Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "💥 Error message: ${e.message}")
            Log.e(TAG, "💥 Stack trace: ${e.stackTraceToString()}")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            false
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.READ_PHONE_STATE])
    private fun getMyPhoneNumber(): String {
        return try {
            val telephonyManager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE)
                as TelephonyManager
            val number = telephonyManager.line1Number
            Log.d(TAG, "📞 My phone number: ${number ?: "Unknown"}")
            number ?: "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting phone number", e)
            "Unknown"
        }
    }
}
