package com.appCes.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.appCes.smsmessenger.extensions.conversationsDB
import com.appCes.smsmessenger.extensions.markThreadMessagesRead
import com.appCes.smsmessenger.helpers.MARK_AS_READ
import com.appCes.smsmessenger.helpers.THREAD_ID
import com.appCes.smsmessenger.helpers.refreshMessages

class MarkAsReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MARK_AS_READ -> {
                val threadId = intent.getLongExtra(THREAD_ID, 0L)
                context.notificationManager.cancel(threadId.hashCode())
                ensureBackgroundThread {
                    context.markThreadMessagesRead(threadId)
                    context.conversationsDB.markRead(threadId)
                    refreshMessages()
                }
            }
        }
    }
}
