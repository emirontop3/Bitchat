package com.emirontop3.bitchat.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emirontop3.bitchat.domain.model.Conversation
import com.emirontop3.bitchat.domain.model.Message
import com.emirontop3.bitchat.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChatListViewModel @Inject constructor(private val repo: ChatRepository) : ViewModel() {
    val conversations: StateFlow<List<Conversation>> = repo.conversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@HiltViewModel
class ConversationViewModel @Inject constructor(private val repo: ChatRepository) : ViewModel() {
    fun messages(conversationId: String): StateFlow<List<Message>> = repo.messages(conversationId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun send(conversation: Conversation, text: String) = viewModelScope.launch { repo.sendMessage(conversation, text) }
}
