package com.appCes.smsmessenger.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.appCes.smsmessenger.models.Attachment

@Dao
interface AttachmentsDao {
    @Query("SELECT * FROM attachments")
    fun getAll(): List<Attachment>
}
