package org.sattory.app.rag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.sattory.app.rag.data.local.RagChunkWithDocumentEntity

class RagHybridRankerTest {
    private val ranker = RagHybridRanker()
    private fun chunk(id: Long, text: String, vector: FloatArray = floatArrayOf(0.8f, 0.6f)) =
        RagChunkWithDocumentEntity(id, 1, id.toInt(), "Documento", text, VectorCodec.encode(vector))

    @Test
    fun keepsShortPassagesContainingUsefulFacts() {
        val sources = ranker.rank(listOf(chunk(1, "Telemaco era filho de Odisseu.")), "filho de Odisseu", floatArrayOf(1f, 0f), 5)
        assertEquals("Telemaco era filho de Odisseu.", sources.single().text)
    }

    @Test
    fun exactNameOutranksSemanticallyCloseUnrelatedPassage() {
        val sources = ranker.rank(listOf(
            chunk(1, "Odisseu navegou pelos mares e contou suas aventuras a Alcino.", floatArrayOf(1f, 0f)),
            chunk(2, "Cila era um monstro de seis cabecas que vivia numa caverna.", floatArrayOf(0.6f, 0.8f)),
        ), "Quem era Cila?", floatArrayOf(1f, 0f), 5)
        assertTrue(sources.first().text.contains("Cila"))
    }

    @Test
    fun stableOrderingAndAccentInsensitiveSearch() {
        val chunks = listOf(chunk(2, "Telemaco era filho de Odisseu e buscou noticias do pai."), chunk(1, "Penelope aguardava em sua casa a volta de Odisseu por muitos anos."))
        val first = ranker.rank(chunks, "Quem era Telêmaco?", floatArrayOf(1f, 0f), 5)
        val second = ranker.rank(chunks.reversed(), "Quem era Telêmaco?", floatArrayOf(1f, 0f), 5)
        assertEquals(first, second)
        assertTrue(first.first().text.contains("Telemaco"))
    }

    @Test
    fun removesDuplicateBoundaryTextWithoutLosingNewEvidence() {
        val overlap = "Cila era um monstro de seis cabecas que vivia numa caverna."
        val sources = ranker.rank(listOf(
            chunk(1, "Ulisses recebeu instrucoes antes de partir. $overlap"),
            chunk(2, "$overlap Cila capturou seis marinheiros durante a travessia."),
        ), "Cila", floatArrayOf(1f, 0f), 5)
        assertEquals(1, sources.count { it.text.contains(overlap) })
        assertTrue(sources.any { it.text.contains("capturou seis") })
    }

    @Test
    fun absentTermsAndWeakSemanticMatchReturnNoEvidence() {
        assertTrue(ranker.rank(listOf(chunk(1, "O texto explica a fotossintese e a absorcao da luz solar.", floatArrayOf(0f, 1f))), "Zorblax", floatArrayOf(1f, 0f), 5).isEmpty())
        assertTrue(ranker.rank(emptyList(), "Cila", floatArrayOf(1f, 0f), 5).isEmpty())
    }
}
