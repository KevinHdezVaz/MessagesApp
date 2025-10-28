package com.goodwy.smsmessenger

import com.goodwy.commons.RightApp
import com.goodwy.commons.extensions.isRuStoreInstalled
import com.goodwy.commons.helpers.rustore.RuStoreModule
import com.google.firebase.FirebaseApp
import android.util.Log

class App : RightApp() {

    override fun onCreate() {
        super.onCreate()

        try {
            FirebaseApp.initializeApp(this)
            Log.d("App", "✅ Firebase inicializado correctamente en App.onCreate()")
        } catch (e: Exception) {
            Log.e("App", "❌ Error al inicializar Firebase en App.onCreate()", e)
        }

        if (isRuStoreInstalled()) RuStoreModule.install(this, "685530047")
    }

    override val isAppLockFeatureAvailable = true
}
