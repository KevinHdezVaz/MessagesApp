package com.goodwy.smsmessenger.models


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "received_sms")
data class ReceivedSms(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromNumber: String,
    val toNumber: String,
    val message: String,
    val timestamp: Long,
    val deviceId: String,
    val sentToServer: Boolean = false
)
