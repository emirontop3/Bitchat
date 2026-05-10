package com.emirontop3.bitchat.di

import android.content.Context
import androidx.room.Room
import com.emirontop3.bitchat.data.repository.ChatRepositoryImpl
import com.emirontop3.bitchat.database.AppDatabase
import com.emirontop3.bitchat.domain.repository.ChatRepository
import com.emirontop3.bitchat.messaging.encryption.AesEncryptionHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "bitchat.db").build()

    @Provides fun provideConversationDao(db: AppDatabase) = db.conversationDao()
    @Provides fun provideMessageDao(db: AppDatabase) = db.messageDao()
    @Provides @Singleton fun provideEncryptor() = AesEncryptionHelper()
    @Provides @Singleton fun provideRepo(impl: ChatRepositoryImpl): ChatRepository = impl
}
