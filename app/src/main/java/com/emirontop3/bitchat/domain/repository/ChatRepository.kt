package com.emirontop3.bitchat.domain.repository

import com.emirontop3.bitchat.domain.model.Conversation
import com.emirontop3.bitchat.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun conversations(search: String = ""): Flow<List<Conversation>>
    fun messages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversation: Conversation, message: String)
}
