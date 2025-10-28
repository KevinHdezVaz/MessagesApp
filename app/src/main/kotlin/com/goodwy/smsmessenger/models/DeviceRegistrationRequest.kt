package com.goodwy.smsmessenger.models

import com.google.gson.annotations.SerializedName

data class DeviceRegistrationRequest(
    @SerializedName("deviceToken")
    val deviceToken: String,

    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("email")
    val email: String? = null,

    @SerializedName("googleId")
    val googleId: String? = null,

    @SerializedName("phoneNumber")
    val phoneNumber: String? = null
)

data class DeviceRegistrationResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("deviceId")
    val deviceId: String? = null
)
