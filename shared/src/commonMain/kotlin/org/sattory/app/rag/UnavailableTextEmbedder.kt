package org.sattory.app.rag

import org.sattory.app.rag.domain.TextEmbedder

class UnavailableTextEmbedder(private val reason: String = "Modelo de embeddings não configurado") : TextEmbedder {
    override suspend fun countDocumentTokens(text: String): Int = error(reason)
    override suspend fun embedForDocument(text: String): FloatArray = error(reason)
    override suspend fun embedForQuery(text: String): FloatArray = error(reason)
    override fun close() = Unit
}
