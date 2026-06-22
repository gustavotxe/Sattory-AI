package org.sattory.app

import androidx.compose.runtime.Composable
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import org.sattory.app.presentation.chat.ChatScreen
import org.sattory.app.presentation.chat.ChatViewModel
import org.sattory.app.ui.theme.SattoryTheme

private val appModule = module {
    factory { ChatViewModel() }
}

@Composable
fun App() {
    KoinApplication(application = { modules(appModule) }) {
        SattoryTheme {
            ChatScreen()
        }
    }
}
