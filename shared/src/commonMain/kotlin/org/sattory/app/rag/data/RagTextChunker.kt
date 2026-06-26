package org.sattory.app.rag.data

class RagTextChunker(
    private val maxChunkChars: Int = 900,
    private val overlapChars: Int = 240,
) {
    fun chunk(text: String): List<String> {
        val normalized = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= maxChunkChars) return listOf(normalized)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < normalized.length) {
            val hardEnd = (start + maxChunkChars).coerceAtMost(normalized.length)
            val end = findNaturalBreak(normalized, start, hardEnd)
            chunks += normalized.substring(start, end).trim()
            if (end == normalized.length) break
            start = (end - overlapChars).coerceAtLeast(start + 1)
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun findNaturalBreak(text: String, start: Int, hardEnd: Int): Int {
        if (hardEnd == text.length) return hardEnd
        val windowStart = (hardEnd - NATURAL_BREAK_WINDOW_CHARS).coerceAtLeast(start)
        val breakIndex = text.lastIndexOfAny(charArrayOf('\n', '.', '!', '?', ';', ':'), hardEnd - 1)
        return if (breakIndex >= windowStart) breakIndex + 1 else hardEnd
    }

    private companion object {
        const val NATURAL_BREAK_WINDOW_CHARS = 260
    }
}
