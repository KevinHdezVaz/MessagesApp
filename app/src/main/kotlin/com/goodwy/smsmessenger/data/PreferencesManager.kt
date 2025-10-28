package com.goodwy.smsmessenger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class PreferencesManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // ✅ Keys
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val KEY_IS_PREMIUM = booleanPreferencesKey("is_premium")  // ✅ NUEVO
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val KEY_DEVICE_REGISTERED = booleanPreferencesKey("device_registered")
        private val KEY_SERVER_BASE_URL = stringPreferencesKey("server_base_url")
    }

    // ========== USER AUTH ==========

    val userId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_ID]
    }

    val userEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_EMAIL]
    }

    val displayName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISPLAY_NAME]
    }

    val authToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTH_TOKEN]
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_LOGGED_IN] ?: false
    }

    // ✅ NUEVO: isPremium
    val isPremium: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_PREMIUM] ?: false
    }

    suspend fun saveUserData(
        userId: String,
        email: String,
        displayName: String,
        authToken: String,
        isPremium: Boolean = false  // ✅ NUEVO parámetro
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = userId
            preferences[KEY_EMAIL] = email
            preferences[KEY_DISPLAY_NAME] = displayName
            preferences[KEY_AUTH_TOKEN] = authToken
            preferences[KEY_IS_LOGGED_IN] = true
            preferences[KEY_IS_PREMIUM] = isPremium  // ✅ NUEVO
        }
    }

    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    fun isLoggedInSync(): Boolean {
        return runBlocking {
            isLoggedIn.first()
        }
    }

    fun getUserEmailSync(): String? {
        return runBlocking {
            userEmail.first()
        }
    }

    fun getDisplayNameSync(): String? {
        return runBlocking {
            displayName.first()
        }
    }

    // ✅ NUEVO: isPremiumSync
    fun isPremiumSync(): Boolean {
        return runBlocking {
            isPremium.first()
        }
    }

    // ========== FCM ==========

    val fcmToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_FCM_TOKEN]
    }

    val isDeviceRegistered: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEVICE_REGISTERED] ?: false
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FCM_TOKEN] = token
        }
    }

    suspend fun setDeviceRegistered(registered: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_REGISTERED] = registered
        }
    }

    fun getFcmTokenSync(): String? {
        return runBlocking {
            fcmToken.first()
        }
    }

    fun isDeviceRegisteredSync(): Boolean {
        return runBlocking {
            isDeviceRegistered.first()
        }
    }

    // ========== SERVER URL ==========

    val serverBaseUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SERVER_BASE_URL]
    }

    suspend fun saveServerBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_BASE_URL] = url
        }
    }

    fun getServerBaseUrlSync(): String {
        return runBlocking {
            serverBaseUrl.first() ?: "https://appmessage.picklebracket.pro/"
        }
    }
}
