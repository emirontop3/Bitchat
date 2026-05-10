package com.emirontop3.bitchat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit, onProfile: () -> Unit, vm: ChatListViewModel = hiltViewModel()) {
    Scaffold(topBar = { TopAppBar(title = { Text("Bitchat") }, actions = { TextButton(onClick = onProfile) { Text("Profile") } }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(vm.conversations.value) { convo ->
                ListItem(headlineContent = { Text(convo.contactName) }, supportingContent = { Text(convo.lastMessage) }, overlineContent = { Text(if (convo.unreadCount > 0) "${convo.unreadCount} unread" else "Read") }, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ConversationScreen(conversationId: String, vm: ConversationViewModel = hiltViewModel()) {
    var input by remember { mutableStateOf("") }
    val messages = vm.messages(conversationId).value
    Scaffold(bottomBar = {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = { input = "" }) { Text("Send") }
        }
    }) { padding ->
        Column(Modifier.padding(padding)) {
            AnimatedVisibility(visible = input.isNotBlank()) { Text("Typing…", modifier = Modifier.padding(8.dp)) }
            LazyColumn(modifier = Modifier.fillMaxSize()) { items(messages) { Text(it.body, modifier = Modifier.padding(8.dp)) } }
        }
    }
}

@Composable fun ProfileScreen(onSettings: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Profile"); Button(onClick = onSettings) { Text("Settings") } } }
@Composable fun SettingsScreen() { Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Settings: SMS fallback, theme, notifications") } }
