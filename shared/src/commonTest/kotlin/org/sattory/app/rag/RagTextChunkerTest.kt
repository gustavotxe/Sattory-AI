package org.sattory.app.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RagTextChunkerTest {
    @Test
    fun splitsByEmbeddingTokensWithoutDiscardingDocumentText() = runTest {
        val text = (1..200).joinToString(" ") { "Palavra$it" }
        val chunks = RagTextChunker().chunkForEmbedding(text) { it.length + 12 }
        assertTrue(chunks.all { it.length + 12 <= 512 })
        for (index in 1..200) assertTrue(chunks.any { it.contains("Palavra$index") })
    }

    @Test
    fun preservesParagraphBreaks() {
        assertTrue(RagTextChunker().chunk("Primeiro paragrafo.\n\nSegundo paragrafo.").single().contains("\n\n"))
    }
    @Test
    fun keepsShortTextsInOneChunk() {
        val chunks = RagTextChunker().chunk(
            "Perfil: A colecao informa que a resposta esta em um texto curto e direto.",
        )

        assertEquals(1, chunks.size)
        assertTrue(chunks.single().contains("texto curto"))
    }

    @Test
    fun overlapsLongChunksToPreserveLocalContext() {
        val paragraph = "Este paragrafo descreve um detalhe importante da colecao e mantem contexto suficiente. "
        val text = buildString {
            repeat(25) { append(paragraph) }
        }

        val chunks = RagTextChunker().chunk(text)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.zipWithNext().any { (left, right) ->
            val leftTail = left.takeLast(80)
            right.contains(leftTail.takeLast(40))
        })
    }
}
