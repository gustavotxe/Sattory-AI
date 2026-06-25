package org.sattory.app.llm

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThinkingContentFilterTest {
    @Test
    fun removesThinkingBlockSplitAcrossTokens() = runTest {
        val output = flowOf("<thi", "nk>segredo", " interno</thi", "nk>\n\nOlá", " mundo")
            .withoutThinkingContent()
            .toList()
            .joinToString("")

        assertEquals("Olá mundo", output)
    }

    @Test
    fun preservesModelsWithoutThinkingTags() = runTest {
        val output = flowOf("Resposta", " normal")
            .withoutThinkingContent()
            .toList()
            .joinToString("")

        assertEquals("Resposta normal", output)
    }
}
