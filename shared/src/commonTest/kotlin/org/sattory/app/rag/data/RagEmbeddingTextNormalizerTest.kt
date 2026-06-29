package org.sattory.app.rag.data

import kotlin.test.Test
import kotlin.test.assertEquals

class RagEmbeddingTextNormalizerTest {
    @Test
    fun normalizesCaseWithoutDependingOnOriginalCasing() {
        val upper = RagEmbeddingTextNormalizer.normalize("  DOCUMENTO FALA SOBRE PRIORIDADE ALTA  ")
        val mixed = RagEmbeddingTextNormalizer.normalize("Documento fala Sobre prioridade Alta")
        val lower = RagEmbeddingTextNormalizer.normalize("documento fala sobre prioridade alta")

        assertEquals(lower, upper)
        assertEquals(lower, mixed)
    }

    @Test
    fun collapsesWhitespaceBeforeEmbedding() {
        val normalized = RagEmbeddingTextNormalizer.normalize("Texto\n\ncom\t espacos   variados")

        assertEquals("texto com espacos variados", normalized)
    }
}
