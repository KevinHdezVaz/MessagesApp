package com.appCes.smsmessenger.utils



object Constants {
    // Server Configuration
    const val SERVER_BASE_URL =  ""

    // API Endpoints
    object Endpoints {
        const val DEVICE_REGISTER = "/api/devices/register"
        const val SMS_INBOUND = "/api/sms/inbound"
        const val SMS_OUTBOUND_STATUS = "/api/sms/outbound/status"
        const val SETTINGS_GET = "/api/settings"
        const val SETTINGS_PUT = "/api/settings"
        const val AUTH_GOOGLE = "/api/auth/google"
        const val AUTH_LOGOUT = "/api/auth/logout"
        const val IAP_VERIFY = "/api/iap/verify"
    }

    // Preferences Keys
    object PrefsKeys {
        const val PREFS_NAME = "sms_backend_prefs"
        const val FCM_TOKEN = "fcm_token"
        const val USER_EMAIL = "user_email"
        const val USER_ID = "user_id"
        const val IS_PREMIUM = "is_premium"
        const val IS_LOGGED_IN = "is_logged_in"

        // Settings keys
        const val SETTING_1 = "setting_1"
        const val SETTING_2 = "setting_2"
        const val SETTING_3 = "setting_3"
        const val SETTING_4 = "setting_4"
    }

    // WorkManager Tags
    object WorkTags {
        const val DEVICE_REGISTRATION = "device_registration"
        const val SMS_WEBHOOK = "sms_webhook"
        const val RETRY_UPLOAD = "retry_upload"
    }

    // Notification Channels
    object NotificationChannels {
        const val FCM_CHANNEL_ID = "fcm_notifications"
        const val FCM_CHANNEL_NAME = "Backend Notifications"
    }

    // Request Codes
    object RequestCodes {
        const val GOOGLE_SIGN_IN = 9001
    }

    // Premium Features
    object PremiumFeatures {
        const val SMS_WEBHOOK = "sms_webhook"
        const val SILENT_PUSH = "silent_push"
        const val SETTINGS_SYNC = "settings_sync"
    }

    // Timeouts
    const val NETWORK_TIMEOUT = 30L // seconds
    const val RETRY_BACKOFF_DELAY = 10L // seconds
    const val MAX_RETRY_ATTEMPTS = 3
}
