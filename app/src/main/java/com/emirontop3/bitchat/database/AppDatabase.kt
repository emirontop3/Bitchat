package com.emirontop3.bitchat.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.emirontop3.bitchat.database.dao.ConversationDao
import com.emirontop3.bitchat.database.dao.MessageDao
import com.emirontop3.bitchat.database.entity.ConversationEntity
import com.emirontop3.bitchat.database.entity.MessageEntity

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
