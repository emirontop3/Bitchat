package com.emirontop3.bitchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.emirontop3.bitchat.ui.navigation.AppNavGraph
import com.emirontop3.bitchat.ui.theme.BitchatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BitchatTheme {
                AppNavGraph()
            }
        }
    }
}
