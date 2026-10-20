package org.sattory.app.rag.domain

interface TextEmbedder {
    suspend fun countDocumentTokens(text: String): Int
    suspend fun embedForDocument(text: String): FloatArray
    suspend fun embedForQuery(text: String): FloatArray
    fun close()
}
