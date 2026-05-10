package com.emirontop3.bitchat.models

import com.emirontop3.bitchat.domain.model.Conversation

object FakeData {
    val conversations = listOf(
        Conversation(contactName = "Alex", phoneNumber = "+155510001", lastMessage = "See you soon", unreadCount = 2, timestamp = System.currentTimeMillis()),
        Conversation(contactName = "Taylor", phoneNumber = "+155510002", lastMessage = "Let's ship it", unreadCount = 0, timestamp = System.currentTimeMillis() - 60_000)
    )
}
