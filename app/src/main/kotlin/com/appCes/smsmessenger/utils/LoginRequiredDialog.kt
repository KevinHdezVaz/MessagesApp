package com.appCes.smsmessenger.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.goodwy.commons.extensions.getAlertDialogBuilder
import com.goodwy.commons.extensions.setupDialogStuff
import com.appCes.smsmessenger.R
import com.appCes.smsmessenger.databinding.DialogLoginRequiredBinding

class LoginRequiredDialog(
    val activity: Activity,
    val onSignInClick: () -> Unit,
    val onSkipClick: (() -> Unit)? = null
) {

    private var dialog: AlertDialog? = null

    init {
        val binding = DialogLoginRequiredBinding.inflate(activity.layoutInflater)

        binding.apply {
            btnSignIn.setOnClickListener {
                dialog?.dismiss()
                onSignInClick()
            }

            btnSkip.setOnClickListener {
                dialog?.dismiss()
                onSkipClick?.invoke()
            }

            // Si no hay callback para skip, ocultar el botón
            if (onSkipClick == null) {
                btnSkip.visibility = android.view.View.GONE
            }
        }

        activity.getAlertDialogBuilder()
            .setOnDismissListener { dialog = null }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.login_required) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}
