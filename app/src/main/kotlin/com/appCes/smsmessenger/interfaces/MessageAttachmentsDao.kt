package com.appCes.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.appCes.smsmessenger.models.MessageAttachment

@Dao
interface MessageAttachmentsDao {
    @Query("SELECT * FROM message_attachments")
    fun getAll(): List<MessageAttachment>
}
