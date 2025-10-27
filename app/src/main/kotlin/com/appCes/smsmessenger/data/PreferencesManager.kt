package com.appCes.smsmessenger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences  // ✅ ESTE ES EL CORRECTO
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// ❌ ELIMINAR ESTE IMPORT:
// import java.util.prefs.Preferences

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

        // Keys
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    }

    // Flows para observar datos
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

    // Guardar datos del usuario
    suspend fun saveUserData(
        userId: String,
        email: String,
        displayName: String,
        authToken: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = userId
            preferences[KEY_EMAIL] = email
            preferences[KEY_DISPLAY_NAME] = displayName
            preferences[KEY_AUTH_TOKEN] = authToken
            preferences[KEY_IS_LOGGED_IN] = true
        }
    }

    // Limpiar datos (logout)
    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // Función síncrona para verificar login (útil para MainActivity)
    fun isLoggedInSync(): Boolean {
        return runBlocking {
            isLoggedIn.first()
        }
    }

    // Obtener email de forma síncrona
    fun getUserEmailSync(): String? {
        return runBlocking {
            userEmail.first()
        }
    }

    // Obtener nombre de forma síncrona
    fun getDisplayNameSync(): String? {
        return runBlocking {
            displayName.first()
        }
    }
}
