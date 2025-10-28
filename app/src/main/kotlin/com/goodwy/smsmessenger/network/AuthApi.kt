package com.goodwy.smsmessenger.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class GoogleLoginRequest(
    val email: String,
    val googleId: String,
    val deviceToken: String,
    val displayName: String?,
    val deviceId: String?
)

data class GoogleLoginResponse(
    val success: Boolean,
    val message: String,
    val user: UserData?,
    val settings: UserSettingsData?
)

data class UserData(
    val id: Int,
    val email: String,
    val displayName: String?,
    val isPremium: Boolean
)

data class UserSettingsData(
    val setting_1: Boolean?,
    val setting_2: Boolean?,
    val setting_3: Boolean?,
    val setting_4: Boolean?
)

interface AuthApi {
    @POST("api/auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): Response<GoogleLoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(@Body deviceToken: Map<String, String>): Response<GoogleLoginResponse>
}
