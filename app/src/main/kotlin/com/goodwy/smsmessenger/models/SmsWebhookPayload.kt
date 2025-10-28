package com.goodwy.smsmessenger.models

import com.google.gson.annotations.SerializedName

data class SmsWebhookPayload(
    @SerializedName("from")
    val from: String,

    @SerializedName("body")
    val body: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("simSlot")
    val simSlot: Int,

    @SerializedName("conversationId")
    val conversationId: String,

    @SerializedName("deviceId")
    val deviceId: String
)

data class WebhookResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("smsId")  // ✅ Agregado
    val smsId: Long? = null   // ✅ Nullable porque puede no venir en errores
)
