package com.emirontop3.bitchat.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val contactName: String,
    val phoneNumber: String,
    val lastMessage: String,
    val unreadCount: Int,
    val timestamp: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val encryptedBody: String,
    val sender: String,
    val timestamp: Long,
    val read: Boolean,
    val reaction: String?
)
