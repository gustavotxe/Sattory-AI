package org.sattory.app.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class DesktopLocalLlm(
    private val modelPath: String,
    nativeLibraryPath: String,
) : LocalLlm {
    @Volatile
    private var loaded = false

    private val inferenceDispatcher = Dispatchers.Default.limitedParallelism(1)

    init {
        val library = File(nativeLibraryPath)
        require(library.isFile) { "Biblioteca nativa nao encontrada em ${library.absolutePath}" }
        System.load(library.absolutePath)
    }

    override fun generate(prompt: String, options: GenerationOptions): Flow<String> = flow {
        ensureLoaded()
        NativeLlmBridge.nativeBeginGeneration(
            prompt.encodeToByteArray(),
            options.maxTokens,
            options.contextSize,
            options.temperature,
            options.topP,
            options.repeatPenalty,
        )
        var completed = false
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val token = NativeLlmBridge.nativeNextToken() ?: break
                if (token.isNotEmpty()) emit(token.decodeToString())
            }
            completed = true
        } finally {
            if (!completed) NativeLlmBridge.nativeCancelGeneration()
        }
    }.flowOn(inferenceDispatcher).withoutThinkingContent()

    private fun ensureLoaded() {
        if (loaded) return
        require(File(modelPath).isFile) { "Modelo nao encontrado em $modelPath" }
        NativeLlmBridge.nativeLoad(modelPath)
        loaded = true
    }

    override suspend fun resetConversation() = withContext(inferenceDispatcher) {
        if (loaded) NativeLlmBridge.nativeResetConversation()
    }

    override suspend fun wasLastGenerationTokenLimited(): Boolean = withContext(inferenceDispatcher) {
        loaded && NativeLlmBridge.nativeWasLastGenerationTokenLimited()
    }

    override suspend fun truncateConversation(messageCount: Int) = withContext(inferenceDispatcher) {
        require(messageCount >= 0)
        if (loaded) NativeLlmBridge.nativeTruncateConversation(messageCount)
    }

    override fun close() {
        if (loaded) NativeLlmBridge.nativeClose()
        loaded = false
    }
}

internal object NativeLlmBridge {
    external fun nativeLoad(modelPath: String)
    external fun nativeBeginGeneration(
        prompt: ByteArray,
        maxTokens: Int,
        contextSize: Int,
        temperature: Float,
        topP: Float,
        repeatPenalty: Float,
    )
    external fun nativeNextToken(): ByteArray?
    external fun nativeCancelGeneration()
    external fun nativeWasLastGenerationTokenLimited(): Boolean
    external fun nativeResetConversation()
    external fun nativeTruncateConversation(messageCount: Int)
    external fun nativeClose()
}
