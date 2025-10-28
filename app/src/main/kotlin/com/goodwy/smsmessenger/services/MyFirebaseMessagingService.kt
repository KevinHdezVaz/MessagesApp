package com.goodwy.smsmessenger.services

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.goodwy.smsmessenger.data.PreferencesManager
import com.goodwy.smsmessenger.workers.DeviceRegistrationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Guardar token localmente
        serviceScope.launch {
            val preferencesManager = PreferencesManager.getInstance(applicationContext)
            preferencesManager.saveFcmToken(token)
            preferencesManager.setDeviceRegistered(false) // Requiere re-registro
        }

        // Encolar trabajo para registrar en servidor
        enqueueRegistrationWork(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        // TODO: Procesar push notifications (Feature 3)
        message.data.let { data ->
            Log.d(TAG, "Message data: $data")

            when (data["action"]) {
                "send_sms" -> {
                    Log.d(TAG, "Action: send_sms")
                    // TODO: Implementar en Feature 3
                }
                "prefill_draft" -> {
                    Log.d(TAG, "Action: prefill_draft")
                    // TODO: Implementar en Feature 3
                }
                "alert_unread" -> {
                    Log.d(TAG, "Action: alert_unread")
                    // TODO: Implementar en Feature 3
                }
                else -> {
                    Log.w(TAG, "Unknown action: ${data["action"]}")
                }
            }
        }
    }

    private fun enqueueRegistrationWork(token: String) {
        val workData = workDataOf(
            DeviceRegistrationWorker.KEY_FCM_TOKEN to token
        )

        val workRequest = OneTimeWorkRequestBuilder<DeviceRegistrationWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(workRequest)

        Log.d(TAG, "Device registration work enqueued")
    }
}
