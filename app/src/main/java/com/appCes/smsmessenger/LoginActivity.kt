package com.appCes.smsmessenger.activities

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.extensions.*
import com.appCes.smsmessenger.R
import com.appCes.smsmessenger.data.PreferencesManager
import com.appCes.smsmessenger.databinding.ActivityLoginBinding
import com.goodwy.commons.helpers.LOWER_ALPHA
import kotlinx.coroutines.launch

class LoginActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityLoginBinding::inflate)
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        preferencesManager = PreferencesManager.getInstance(this)

        setupUI()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updateColors()
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

        // TODO: Implementar Google Sign-In real en la siguiente fase
        // Por ahora simulamos el proceso
        binding.root.postDelayed({
            lifecycleScope.launch {
                preferencesManager.saveUserData(
                    userId = "temp_user_${System.currentTimeMillis()}",
                    email = "user@example.com",
                    displayName = "Test User",
                    authToken = "temp_token_${System.currentTimeMillis()}"
                )

                showLoading(false)
                toast("Registro exitoso")
                navigateToMain()
            }
        }, 1500)
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
