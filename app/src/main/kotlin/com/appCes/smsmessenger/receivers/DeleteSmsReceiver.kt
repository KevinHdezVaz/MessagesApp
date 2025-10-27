package com.appCes.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.commons.extensions.notificationManager
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.appCes.smsmessenger.extensions.conversationsDB
import com.appCes.smsmessenger.extensions.deleteMessage
import com.appCes.smsmessenger.extensions.markThreadMessagesRead
import com.appCes.smsmessenger.extensions.updateLastConversationMessage
import com.appCes.smsmessenger.helpers.IS_MMS
import com.appCes.smsmessenger.helpers.MESSAGE_ID
import com.appCes.smsmessenger.helpers.THREAD_ID
import com.appCes.smsmessenger.helpers.refreshMessages

class DeleteSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        context.notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            context.markThreadMessagesRead(threadId)
            context.conversationsDB.markRead(threadId)
            context.deleteMessage(messageId, isMms)
            context.updateLastConversationMessage(threadId)
            refreshMessages()
        }
    }
}
