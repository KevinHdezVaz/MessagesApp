package com.goodwy.smsmessenger.workers

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goodwy.smsmessenger.databases.MessagesDatabase
import com.goodwy.smsmessenger.helpers.getDeviceId
import com.goodwy.smsmessenger.models.ReceivedSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SmsProcessingWorker"

        const val KEY_FROM_NUMBER = "from_number"
        const val KEY_MESSAGE_BODY = "message_body"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_SIM_SLOT = "sim_slot"
    }

    private val database = MessagesDatabase.getInstance(context)
    private val smsDao = database.SmsDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val fromNumber = inputData.getString(KEY_FROM_NUMBER) ?: return@withContext Result.failure()
            val messageBody = inputData.getString(KEY_MESSAGE_BODY) ?: return@withContext Result.failure()
            val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
            val simSlot = inputData.getInt(KEY_SIM_SLOT, 0)

            Log.d(TAG, "Processing SMS from $fromNumber")

            // 1. Guardar en base de datos local
            val receivedSms = ReceivedSms(
                fromNumber = fromNumber,
                toNumber = getMyPhoneNumber(),
                message = messageBody,
                timestamp = timestamp,
                deviceId = getDeviceId(applicationContext),
                sentToServer = false
            )

            smsDao.insertSms(receivedSms)
            Log.d(TAG, "SMS saved to local database")

            // 2. Marcar mensaje como LEÍDO
            markSmsAsRead(fromNumber, messageBody, timestamp)
            Log.d(TAG, "SMS marked as read")

            // 3. TODO: Enviar webhook al servidor (Fase 2)
            // sendWebhookToServer(receivedSms)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)

            // Reintentar en caso de error (máximo 3 veces)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.READ_PHONE_STATE])
    private fun getMyPhoneNumber(): String {
        // Obtener el número del dispositivo (si está disponible)
        return try {
            val telephonyManager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.line1Number ?: "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number", e)
            "Unknown"
        }
    }

    private fun markSmsAsRead(fromNumber: String, messageBody: String, timestamp: Long) {
        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id")
            val selection = "address = ? AND body = ? AND date = ?"
            val selectionArgs = arrayOf(fromNumber, messageBody, timestamp.toString())

            val cursor = applicationContext.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow("_id"))

                    // Marcar como leído
                    val values = android.content.ContentValues().apply {
                        put(Telephony.Sms.READ, 1)
                    }

                    val smsUri = Uri.parse("content://sms/$id")
                    applicationContext.contentResolver.update(smsUri, values, null, null)

                    Log.d(TAG, "SMS ID $id marked as read")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking SMS as read", e)
        }
    }
}
