package org.sattory.app.rag.data

object RagEmbeddingTextNormalizer {
    fun normalize(text: String): String =
        text.trim()
            .replace(WHITESPACE_REGEX, " ")
            .lowercase()

    private val WHITESPACE_REGEX = Regex("\\s+")
}
