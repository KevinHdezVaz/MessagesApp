package com.goodwy.smsmessenger.interfaces


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.goodwy.smsmessenger.models.ReceivedSms
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {

    @Insert
    suspend fun insertSms(sms: ReceivedSms): Long

    @Query("SELECT * FROM received_sms ORDER BY timestamp DESC")
    fun getAllSms(): Flow<List<ReceivedSms>>

    @Query("SELECT * FROM received_sms WHERE sentToServer = 0")
    suspend fun getPendingSms(): List<ReceivedSms>

    @Update
    suspend fun updateSms(sms: ReceivedSms)

    @Query("DELETE FROM received_sms WHERE id = :smsId")
    suspend fun deleteSms(smsId: Long)
}
