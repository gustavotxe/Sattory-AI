package org.sattory.app.rag.data

import org.sattory.app.rag.domain.TextEmbedder

class UnavailableTextEmbedder(private val reason: String = "Modelo de embeddings nao configurado") : TextEmbedder {
    override suspend fun embedForDocument(text: String): FloatArray = error(reason)
    override suspend fun embedForQuery(text: String): FloatArray = error(reason)
    override fun close() = Unit
}
