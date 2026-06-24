package org.sattory.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.sattory.app.llm.DesktopLocalLlm
import org.sattory.app.llm.DesktopNativeLibraryResolver
import org.sattory.app.llm.GenerationOptions
import org.sattory.app.llm.LocalLlm
import org.sattory.app.model.DesktopModelFiles
import org.sattory.app.model.DesktopModelRepository
import org.sattory.app.model.ModelRepository
import org.sattory.app.platform.DesktopAppDirectories
import org.sattory.app.platform.DesktopStorageRegistry

fun main(args: Array<String>) {
    val directories = DesktopAppDirectories()
    DesktopStorageRegistry(directories).registerStorageRoot()
    val platformModule = desktopPlatformModule(directories)

    if ("--smoke-test" in args) {
        runSmokeTest(platformModule)
        return
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Sattory",
        ) {
            App(platformModule)
        }
    }
}

private fun desktopPlatformModule(directories: DesktopAppDirectories = DesktopAppDirectories()): Module = module {
    single { directories }
    single { DesktopStorageRegistry(get()) }
    single { DesktopModelFiles(get()) }
    single { DesktopNativeLibraryResolver() }
    single<LocalLlm> {
        DesktopLocalLlm(
            modelPath = get<DesktopModelFiles>().modelFile.absolutePath,
            nativeLibraryPath = get<DesktopNativeLibraryResolver>().resolve().absolutePath,
        )
    }
    single<ModelRepository> {
        DesktopModelRepository(get())
    }
}

private fun runSmokeTest(platformModule: Module) {
    val koinApplication = koinApplication {
        modules(platformModule)
    }
    val llm = koinApplication.koin.get<LocalLlm>()

    try {
        val (firstChunks, secondChunks) = runBlocking {
            val options = GenerationOptions(maxTokens = 32, contextSize = 2048, temperature = 0.1f)
            val first = llm.generate(
                "Guarde a palavra ABACAXI para a proxima mensagem e responda somente OK.", options,
            ).toList()
            val second = llm.generate(
                "Qual foi a palavra que pedi para guardar? Responda somente a palavra.", options,
            ).toList()
            first to second
        }
        val firstAnswer = firstChunks.joinToString("")
        val secondAnswer = secondChunks.joinToString("")
        check(firstAnswer.isNotBlank() && secondAnswer.contains("ABACAXI", ignoreCase = true)) {
            "O modelo nao preservou o contexto entre os turnos: $secondAnswer"
        }
        check('\uFFFD' !in firstAnswer && '\uFFFD' !in secondAnswer) { "O streaming produziu UTF-8 invalido" }
        check(firstChunks.size + secondChunks.size > 2) { "A resposta nao foi entregue incrementalmente" }
        println("LLM_SMOKE_TEST_FIRST=$firstAnswer (${firstChunks.size} chunks)")
        println("LLM_SMOKE_TEST_CONTEXT=$secondAnswer (${secondChunks.size} chunks)")
    } finally {
        llm.close()
        koinApplication.close()
    }
}
