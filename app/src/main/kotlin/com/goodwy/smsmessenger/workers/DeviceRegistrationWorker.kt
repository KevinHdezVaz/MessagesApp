package com.goodwy.smsmessenger.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goodwy.smsmessenger.data.PreferencesManager
import com.goodwy.smsmessenger.helpers.getDeviceId
import com.goodwy.smsmessenger.models.DeviceRegistrationRequest
import com.goodwy.smsmessenger.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DeviceRegistrationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DeviceRegistrationWorker"
        const val KEY_FCM_TOKEN = "fcm_token"
        private const val MAX_RETRY_ATTEMPTS = 5
    }

    private val apiService = RetrofitClient.getApiService(context)
    private val preferencesManager = PreferencesManager.getInstance(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val fcmToken = inputData.getString(KEY_FCM_TOKEN)
                ?: preferencesManager.getFcmTokenSync()
                ?: return@withContext Result.failure()

            Log.d(TAG, "Registering device with FCM token")

            // Obtener datos del usuario (si está logueado)
            val email = preferencesManager.getUserEmailSync()
            val displayName = preferencesManager.getDisplayNameSync()

            // Obtener número de teléfono (si está disponible)
            val phoneNumber = getMyPhoneNumber()

            val request = DeviceRegistrationRequest(
                deviceToken = fcmToken,
                deviceId = getDeviceId(applicationContext),
                email = email,
                googleId = displayName, // TODO: Usar googleId real cuando implementes login
                phoneNumber = phoneNumber
            )

            val response = apiService.registerDevice(request)

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Device registered successfully")

                // Marcar como registrado
                preferencesManager.setDeviceRegistered(true)

                Result.success()
            } else {
                Log.e(TAG, "Server error: ${response.code()} - ${response.message()}")

                // Retry con exponential backoff
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Log.d(TAG, "Retry attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS")
                    Result.retry()
                } else {
                    Log.e(TAG, "Max retries reached, giving up")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering device", e)

            // Retry con exponential backoff
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Retry attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS")
                Result.retry()
            } else {
                Log.e(TAG, "Max retries reached, giving up")
                Result.failure()
            }
        }
    }

    private fun getMyPhoneNumber(): String? {
        return try {
            val telephonyManager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE)
                as android.telephony.TelephonyManager
            telephonyManager.line1Number
        } catch (e: Exception) {
            Log.e(TAG, "Error getting phone number", e)
            null
        }
    }
}
