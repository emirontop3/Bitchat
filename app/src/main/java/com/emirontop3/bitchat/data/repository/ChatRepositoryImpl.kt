package com.emirontop3.bitchat.data.repository

import com.emirontop3.bitchat.database.dao.ConversationDao
import com.emirontop3.bitchat.database.dao.MessageDao
import com.emirontop3.bitchat.database.entity.ConversationEntity
import com.emirontop3.bitchat.database.entity.MessageEntity
import com.emirontop3.bitchat.domain.model.Conversation
import com.emirontop3.bitchat.domain.model.Message
import com.emirontop3.bitchat.domain.model.MessageType
import com.emirontop3.bitchat.domain.repository.ChatRepository
import com.emirontop3.bitchat.messaging.encryption.AesEncryptionHelper
import com.emirontop3.bitchat.messaging.sms.SmsManagerWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val encryptor: AesEncryptionHelper,
    private val smsManager: SmsManagerWrapper
) : ChatRepository {
    override fun conversations(search: String): Flow<List<Conversation>> =
        (if (search.isBlank()) conversationDao.conversations() else conversationDao.search(search)).map { list ->
            list.map { Conversation(it.id, it.contactName, it.phoneNumber, it.lastMessage, it.unreadCount, it.timestamp) }
        }

    override fun messages(conversationId: String): Flow<List<Message>> = messageDao.messages(conversationId).map { list ->
        list.map {
            Message(it.id, it.conversationId, encryptor.decrypt(it.encryptedBody), it.sender, it.timestamp, it.read, MessageType.TEXT, it.reaction)
        }
    }

    override suspend fun sendMessage(conversation: Conversation, message: String) {
        smsManager.sendSms(conversation.phoneNumber, message)
        messageDao.upsert(MessageEntity(UUID.randomUUID().toString(), conversation.id, encryptor.encrypt(message), "me", System.currentTimeMillis(), true, null))
        conversationDao.upsert(ConversationEntity(conversation.id, conversation.contactName, conversation.phoneNumber, message, 0, System.currentTimeMillis()))
    }
}
