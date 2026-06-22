package org.sattory.app

import androidx.compose.runtime.Composable
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import org.sattory.app.llm.LocalLlm
import org.sattory.app.model.ModelRepository
import org.sattory.app.model.ReadyModelRepository
import org.sattory.app.presentation.chat.ChatScreen
import org.sattory.app.presentation.chat.ChatViewModel
import org.sattory.app.ui.theme.SattoryTheme

@Composable
fun App(
    localLlm: LocalLlm,
    modelRepository: ModelRepository = ReadyModelRepository(),
) {
    KoinApplication(application = {
        modules(module {
            single { localLlm }
            single { modelRepository }
            factory { ChatViewModel(get(), get()) }
        })
    }) {
        SattoryTheme {
            ChatScreen()
        }
    }
}
