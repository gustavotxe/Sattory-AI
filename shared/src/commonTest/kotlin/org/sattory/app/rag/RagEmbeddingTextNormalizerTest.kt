package org.sattory.app.rag

import kotlin.test.Test
import kotlin.test.assertEquals

class RagEmbeddingTextNormalizerTest {
    @Test
    fun normalizesPdfLigaturesAndInvisibleFormatting() {
        assertEquals("fotossintese final fluxo", RagEmbeddingTextNormalizer.normalize("FOTOS\u00ADSINTESE ﬁnal ﬂuxo\u200B"))
    }
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
