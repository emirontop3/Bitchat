package com.emirontop3.bitchat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.emirontop3.bitchat.ui.screens.ChatListScreen
import com.emirontop3.bitchat.ui.screens.ConversationScreen
import com.emirontop3.bitchat.ui.screens.ProfileScreen
import com.emirontop3.bitchat.ui.screens.SettingsScreen

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "chat_list") {
        composable("chat_list") { ChatListScreen(onOpenChat = { nav.navigate("chat/$it") }, onProfile = { nav.navigate("profile") }) }
        composable("chat/{conversationId}") { backStack -> ConversationScreen(conversationId = backStack.arguments?.getString("conversationId").orEmpty()) }
        composable("profile") { ProfileScreen(onSettings = { nav.navigate("settings") }) }
        composable("settings") { SettingsScreen() }
    }
}
