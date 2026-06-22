package org.sattory.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.runBlocking
import org.sattory.app.llm.DesktopLocalLlm
import org.sattory.app.llm.GenerationOptions
import java.io.File

fun main(args: Array<String>) {
    val projectDir = File(System.getProperty("user.dir"))
    val modelPath = System.getProperty("sattory.model.path")
        ?: projectDir.resolve("local-models/qwen/qwen3-1.7b-q4_k_m.gguf").absolutePath
    val nativeLibraryPath = requireNotNull(System.getProperty("sattory.native.library")) {
        "A propriedade sattory.native.library não foi configurada. Execute pelo Gradle (:desktopApp:run)."
    }
    val llm = DesktopLocalLlm(modelPath, nativeLibraryPath)

    if ("--smoke-test" in args) {
        try {
            val answer = runBlocking {
                llm.generate(
                    "Responda somente com a palavra FUNCIONANDO.",
                    GenerationOptions(maxTokens = 24, contextSize = 1024, temperature = 0.1f),
                )
            }
            check(answer.isNotBlank()) { "O modelo terminou sem produzir texto" }
            println("LLM_SMOKE_TEST_RESPONSE=$answer")
        } finally {
            llm.close()
        }
        return
    }

    application {
        Window(
            onCloseRequest = {
                llm.close()
                exitApplication()
            },
            title = "SattoryApp",
        ) {
            App(llm)
        }
    }
}
