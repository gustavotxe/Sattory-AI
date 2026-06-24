package org.sattory.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.module.Module
import org.koin.dsl.module
import org.sattory.app.llm.LocalLlm
import org.sattory.app.llm.UnavailableLocalLlm
import org.sattory.app.model.ModelPreparationUseCase
import org.sattory.app.model.ModelRepository
import org.sattory.app.model.ReadyModelRepository
import org.sattory.app.presentation.chat.ChatScreen
import org.sattory.app.presentation.chat.ChatViewModel

@Composable
fun App(
    localLlm: LocalLlm,
    modelRepository: ModelRepository = ReadyModelRepository(),
) {
    App(listOf(sattoryModule(localLlm, modelRepository)))
}

@Composable
fun App(module: Module) {
    App(listOf(module))
}

@Composable
fun App(modules: List<Module> = listOf(defaultSattoryModule())) {
    KoinApplication(application = { modules(modules + commonSattoryModule()) }) {
        CloseLocalLlmOnDispose()
        ChatScreen()
    }
}

fun sattoryModule(
    localLlm: LocalLlm,
    modelRepository: ModelRepository = ReadyModelRepository(),
): Module = module {
    single { localLlm }
    single { modelRepository }
}

private fun commonSattoryModule(): Module = module {
    single { ModelPreparationUseCase(get()) }
    factory { ChatViewModel(get(), get()) }
}

private fun defaultSattoryModule(): Module = sattoryModule(
    localLlm = UnavailableLocalLlm("Modelo local nao configurado"),
)

@Composable
private fun CloseLocalLlmOnDispose(localLlm: LocalLlm = koinInject()) {
    DisposableEffect(localLlm) {
        onDispose {
            localLlm.close()
        }
    }
}
