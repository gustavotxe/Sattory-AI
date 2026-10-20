package org.sattory.app.rag.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.sattory.app.llm.GenerationOptions

class RagPromptBuilderTest {
    @Test
    fun usesTokenCounterRatherThanByteLengthAndSharesContext() = runTest {
        val sources = List(5) { RagSource("Livro", "Evidencia${it}: " + "日本語 ".repeat(500), 1f, it, 1) }
        val prepared = RagPromptBuilder().prepare("Explique o texto", sources) { it.length / 4 + 100 }
        assertEquals(5, prepared.sources.size)
        assertTrue(prepared.prompt.encodeToByteArray().size > 4096)
        assertTrue(prepared.inputTokens + GenerationOptions.GroundedAnswering.maxTokens + 64 <= 4096)
        for (index in 0..4) assertTrue(prepared.prompt.contains("Evidencia$index"))
        assertFalse(prepared.sources.any { it.text.lastOrNull()?.isHighSurrogate() == true })
    }

    @Test
    fun resolvesReferencesOnlyFromPreviousUserQuestion() {
        val builder = RagQueryBuilder()
        assertEquals("Quem era Cila?", builder.build("Quem era Cila?", "Quem era Penelope?"))
        val resolved = builder.build("Onde ela vivia?", "Quem era Cila?")
        assertTrue(resolved.contains("Quem era Cila?"))
        assertTrue(resolved.contains("Onde ela vivia?"))
    }
}
