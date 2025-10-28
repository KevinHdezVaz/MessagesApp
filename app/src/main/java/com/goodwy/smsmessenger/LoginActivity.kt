package com.goodwy.smsmessenger.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.LOWER_ALPHA
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.data.PreferencesManager
import com.goodwy.smsmessenger.databinding.ActivityLoginBinding
import com.goodwy.smsmessenger.network.GoogleLoginRequest
import com.goodwy.smsmessenger.network.RetrofitClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityLoginBinding::inflate)
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val TAG = "LoginActivity"

        // ✅ Si no quieres usar strings.xml, pon tu Web Client ID aquí directamente
        private const val WEB_CLIENT_ID = "TU_WEB_CLIENT_ID.apps.googleusercontent.com"
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        preferencesManager = PreferencesManager.getInstance(this)

        setupGoogleSignIn()
        setupUI()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateColors()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))  // ✅ Usa la constante o getString(R.string.default_web_client_id)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupUI() {
        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.root,
            nestedView = null,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
    }

    private fun setupClickListeners() {
        binding.apply {
            btnGoogleSignIn.setOnClickListener {
                handleGoogleSignIn()
            }

            btnContinueWithoutAccount.setOnClickListener {
                handleContinueWithoutAccount()
            }
        }
    }

    private fun updateColors() {
        val backgroundColor = getProperBackgroundColor()
        val textColor = getProperTextColor()
        val primaryColor = getProperPrimaryColor()

        binding.apply {
            root.setBackgroundColor(backgroundColor)
            updateTextColors(root)

            btnGoogleSignIn.apply {
                setTextColor(textColor)
                strokeColor = android.content.res.ColorStateList.valueOf(textColor.adjustAlpha(0.3f))
            }

            btnContinueWithoutAccount.apply {
                setTextColor(primaryColor)
                iconTint = android.content.res.ColorStateList.valueOf(primaryColor)
            }

            loginProgress.setIndicatorColor(primaryColor)
            loginProgress.trackColor = primaryColor.adjustAlpha(LOWER_ALPHA)
        }
    }

    private fun handleGoogleSignIn() {
        showLoading(true)

        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d(TAG, "✅ Google Sign-In successful: ${account.email}")

            sendLoginToServer(account)

        } catch (e: ApiException) {
            Log.e(TAG, "❌ Google Sign-In failed", e)
            showLoading(false)
            toast("Error en inicio de sesión: ${e.message}")
        }
    }

    private fun sendLoginToServer(account: GoogleSignInAccount) {
        lifecycleScope.launch {
            try {
                // Obtener FCM token
                val fcmToken = FirebaseMessaging.getInstance().token.await()
                val deviceId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )

                Log.d(TAG, "📤 Sending login to server...")
                Log.d(TAG, "   Email: ${account.email}")
                Log.d(TAG, "   Google ID: ${account.id}")
                Log.d(TAG, "   FCM Token: $fcmToken")

                val request = GoogleLoginRequest(
                    email = account.email ?: "",
                    googleId = account.id ?: "",
                    deviceToken = fcmToken,
                    displayName = account.displayName,
                    deviceId = deviceId
                )

                // ✅ Usar RetrofitClient.getAuthService()
                val response = RetrofitClient.getAuthService(this@LoginActivity).googleLogin(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    val userData = response.body()!!.user!!

                    Log.d(TAG, "✅ Server login successful")
                    Log.d(TAG, "   User ID: ${userData.id}")
                    Log.d(TAG, "   Premium: ${userData.isPremium}")

                    // Guardar datos localmente
                    preferencesManager.saveUserData(
                        userId = userData.id.toString(),
                        email = userData.email,
                        displayName = userData.displayName ?: "",
                        authToken = account.idToken ?: "",
                        isPremium = userData.isPremium
                    )

                    showLoading(false)
                    toast("¡Bienvenido ${userData.displayName}!")
                    navigateToMain()
                } else {
                    Log.e(TAG, "❌ Server error: ${response.body()?.message}")
                    showLoading(false)
                    toast("Error del servidor: ${response.body()?.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Login error", e)
                showLoading(false)
                toast("Error de conexión: ${e.message}")
            }
        }
    }

    private fun handleContinueWithoutAccount() {
        navigateToMain()
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            if (show) {
                btnGoogleSignIn.isEnabled = false
                btnContinueWithoutAccount.isEnabled = false
                loginProgress.beVisible()
            } else {
                btnGoogleSignIn.isEnabled = true
                btnContinueWithoutAccount.isEnabled = true
                loginProgress.beGone()
            }
        }
    }

    private fun navigateToMain() {
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
        }
        finish()
    }
}
