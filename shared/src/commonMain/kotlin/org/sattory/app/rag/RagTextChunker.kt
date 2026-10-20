package org.sattory.app.rag

class RagTextChunker(
    private val maxChunkChars: Int = 900,
    private val overlapChars: Int = 240,
) {
    init {
        require(maxChunkChars >= 2 && maxChunkChars > overlapChars && overlapChars >= 0)
    }

    suspend fun chunkForEmbedding(text: String, countTokens: suspend (String) -> Int): List<String> {
        val chunksWithinTokenLimit = mutableListOf<String>()

        suspend fun addWithinTokenLimit(chunk: String) {
            if (countTokens(chunk) <= 512) {
                chunksWithinTokenLimit += chunk
                return
            }
            require(chunk.length > 1) { "Nao foi possivel dividir o texto para embeddings" }
            var splitIndex = chunk.length / 2
            val naturalBreakIndex = chunk.lastIndexOfAny(charArrayOf('\n', '.', '!', '?', ' '), splitIndex)
            if (naturalBreakIndex >= splitIndex / 2) splitIndex = naturalBreakIndex + 1
            if (splitIndex < chunk.length && chunk[splitIndex - 1].isHighSurrogate()) splitIndex--
            require(splitIndex in 1 until chunk.length) { "Caractere nao suportado pelo modelo de embeddings" }
            addWithinTokenLimit(chunk.take(splitIndex).trim())
            addWithinTokenLimit(chunk.drop(splitIndex).trim())
        }

        for (chunk in chunk(text)) addWithinTokenLimit(chunk)
        return chunksWithinTokenLimit.filter { it.isNotBlank() }
    }
    fun chunk(text: String): List<String> {
        val normalized = text.lineSequence()
            .map { it.trim() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= maxChunkChars) return listOf(normalized)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < normalized.length) {
            var hardEnd = (start + maxChunkChars).coerceAtMost(normalized.length)
            if (hardEnd < normalized.length && normalized[hardEnd - 1].isHighSurrogate()) hardEnd--
            val end = findNaturalBreak(normalized, start, hardEnd)
            chunks += normalized.substring(start, end).trim()
            if (end == normalized.length) break
            val minimumStart = start + 1
            val overlapStart = (end - overlapChars).coerceAtLeast(minimumStart)
            val sentenceStart = normalized.lastIndexOfAny(charArrayOf('\n', '.', '!', '?'), overlapStart - 1) + 1
            val wordStart = normalized.lastIndexOf(' ', overlapStart - 1) + 1
            start = when {
                sentenceStart >= minimumStart && sentenceStart >= overlapStart - 80 -> sentenceStart
                wordStart >= minimumStart && wordStart >= overlapStart - 40 -> wordStart
                else -> overlapStart
            }
            if (normalized[start].isLowSurrogate()) start++
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun findNaturalBreak(text: String, start: Int, hardEnd: Int): Int {
        if (hardEnd == text.length) return hardEnd
        val windowStart = (hardEnd - NATURAL_BREAK_WINDOW_CHARS).coerceAtLeast(start)
        val paragraph = text.lastIndexOf("\n\n", hardEnd - 2)
        if (paragraph >= windowStart) return paragraph + 2
        val breakIndex = text.lastIndexOfAny(charArrayOf('\n', '.', '!', '?', ';', ':'), hardEnd - 1)
        if (breakIndex >= windowStart) return breakIndex + 1
        val space = text.lastIndexOf(' ', hardEnd - 1)
        return if (space >= windowStart) space + 1 else hardEnd
    }

    private companion object {
        const val NATURAL_BREAK_WINDOW_CHARS = 260
    }
}
