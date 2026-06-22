package org.sattory.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
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
            val (firstChunks, secondChunks) = runBlocking {
                val options = GenerationOptions(maxTokens = 32, contextSize = 2048, temperature = 0.1f)
                val first = llm.generate(
                    "Guarde a palavra ABACAXI para a próxima mensagem e responda somente OK.", options,
                ).toList()
                val second = llm.generate(
                    "Qual foi a palavra que pedi para guardar? Responda somente a palavra.", options,
                ).toList()
                first to second
            }
            val firstAnswer = firstChunks.joinToString("")
            val secondAnswer = secondChunks.joinToString("")
            check(firstAnswer.isNotBlank() && secondAnswer.contains("ABACAXI", ignoreCase = true)) {
                "O modelo não preservou o contexto entre os turnos: $secondAnswer"
            }
            check('\uFFFD' !in firstAnswer && '\uFFFD' !in secondAnswer) { "O streaming produziu UTF-8 inválido" }
            check(firstChunks.size + secondChunks.size > 2) { "A resposta não foi entregue incrementalmente" }
            println("LLM_SMOKE_TEST_FIRST=$firstAnswer (${firstChunks.size} chunks)")
            println("LLM_SMOKE_TEST_CONTEXT=$secondAnswer (${secondChunks.size} chunks)")
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
