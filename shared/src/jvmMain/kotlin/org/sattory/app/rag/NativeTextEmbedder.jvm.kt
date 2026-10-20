package org.sattory.app.rag

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sattory.app.rag.domain.TextEmbedder
import java.io.File

class NativeTextEmbedder(
    private val modelPath: String,
    nativeLibraryPath: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : TextEmbedder {
    @Volatile
    private var loaded = false

    init {
        val library = File(nativeLibraryPath)
        require(library.isFile) { "Biblioteca nativa nao encontrada em ${library.absolutePath}" }
        runCatching { System.load(library.absolutePath) }
    }

    override suspend fun countDocumentTokens(text: String): Int = withContext(dispatcher) {
        ensureLoaded()
        NativeEmbeddingBridge.nativeCountEmbeddingTokens("passage: $text".encodeToByteArray())
    }

    override suspend fun embedForDocument(text: String): FloatArray = embed("passage: $text")

    override suspend fun embedForQuery(text: String): FloatArray = embed("query: $text")

    private suspend fun embed(text: String): FloatArray = withContext(dispatcher) {
        ensureLoaded()
        NativeEmbeddingBridge.nativeEmbed(text.encodeToByteArray())
    }

    private fun ensureLoaded() {
        if (loaded) return
        require(File(modelPath).isFile) { "Modelo de embeddings nao encontrado em $modelPath" }
        NativeEmbeddingBridge.nativeLoadEmbedding(modelPath)
        loaded = true
    }

    override fun close() {
        if (loaded) NativeEmbeddingBridge.nativeCloseEmbedding()
        loaded = false
    }
}

internal object NativeEmbeddingBridge {
    external fun nativeLoadEmbedding(modelPath: String)
    external fun nativeCountEmbeddingTokens(text: ByteArray): Int
    external fun nativeEmbed(text: ByteArray): FloatArray
    external fun nativeCloseEmbedding()
}
