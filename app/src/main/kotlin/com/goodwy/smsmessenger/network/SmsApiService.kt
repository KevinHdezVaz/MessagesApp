package com.goodwy.smsmessenger.network

import com.goodwy.smsmessenger.models.DeviceRegistrationRequest
import com.goodwy.smsmessenger.models.DeviceRegistrationResponse
import com.goodwy.smsmessenger.models.SmsWebhookPayload
import com.goodwy.smsmessenger.models.WebhookResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SmsApiService {

    @POST("api/sms/inbound")
    suspend fun sendInboundSms(
        @Body payload: SmsWebhookPayload
    ): Response<WebhookResponse>

    // ✅ NUEVO
    @POST("api/devices/register")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>
}
