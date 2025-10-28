package com.goodwy.smsmessenger.network

import android.content.Context
import com.goodwy.smsmessenger.BuildConfig
 import com.goodwy.smsmessenger.data.PreferencesManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val DEFAULT_BASE_URL = "https://appmessage.picklebracket.pro/"

    private var retrofit: Retrofit? = null

    fun getInstance(context: Context): Retrofit {
        if (retrofit == null) {
            val baseUrl = getServerBaseUrl(context)

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        return retrofit!!
    }

    // ✅ Servicio para SMS (ya existe)
    fun getApiService(context: Context): SmsApiService {
        return getInstance(context).create(SmsApiService::class.java)
    }

    // ✅ NUEVO: Servicio para autenticación
    fun getAuthService(context: Context): AuthApi {
        return getInstance(context).create(AuthApi::class.java)
    }

    private fun getServerBaseUrl(context: Context): String {
        val preferencesManager = PreferencesManager.getInstance(context)
        val savedUrl = preferencesManager.getServerBaseUrlSync()

        return if (!savedUrl.isNullOrEmpty()) {
            savedUrl
        } else {
            DEFAULT_BASE_URL
        }
    }

    fun resetInstance() {
        retrofit = null
    }
}
