package com.goodwy.smsmessenger.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

@SuppressLint("HardwareIds")
fun getDeviceId(context: Context): String {
    return try {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    } catch (e: Exception) {
        "unknown_device"
    }
}
