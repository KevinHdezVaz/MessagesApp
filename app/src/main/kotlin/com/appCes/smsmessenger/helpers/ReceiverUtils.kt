package com.appCes.smsmessenger.helpers

import android.content.Context
import com.appCes.smsmessenger.extensions.config

object ReceiverUtils {

    fun isMessageFilteredOut(context: Context, body: String): Boolean {
        for (blockedKeyword in context.config.blockedKeywords) {
            if (body.contains(blockedKeyword, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
