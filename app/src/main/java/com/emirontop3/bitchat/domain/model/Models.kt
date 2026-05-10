package com.emirontop3.bitchat.domain.model

import java.util.UUID

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val contactName: String,
    val phoneNumber: String,
    val lastMessage: String,
    val unreadCount: Int,
    val timestamp: Long
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val body: String,
    val sender: String,
    val timestamp: Long,
    val read: Boolean,
    val type: MessageType = MessageType.TEXT,
    val reaction: String? = null
)

enum class MessageType { TEXT, IMAGE_PLACEHOLDER, VIDEO_PLACEHOLDER }
