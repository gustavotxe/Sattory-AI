package org.sattory.app.rag

object RagEmbeddingTextNormalizer {
    fun normalize(text: String): String =
        text.replace("\u00AD", "")
            .replace("\u200B", "")
            .replace("ﬁ", "fi")
            .replace("ﬂ", "fl")
            .trim()
            .replace(WHITESPACE_REGEX, " ")
            .lowercase()

    private val WHITESPACE_REGEX = Regex("\\s+")
}
