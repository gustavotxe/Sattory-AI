package org.sattory.app.rag.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.sattory.app.llm.GenerationOptions

class PdfImportTest {
    @Test
    fun importsEntireTextAtLimit() = runTest {
        val text = "a".repeat(RagDocumentLimits.MAX_CHARACTERS)
        val useCase = ImportPdfUseCase(object : PdfRepository {
            override suspend fun extract(reference: String) = PdfDocument("PDF", text)
        })
        assertEquals(text, useCase("reference").text)
    }

    @Test
    fun rejectsOversizedAndEmptyDocuments() {
        assertFailsWith<IllegalArgumentException> { RagDocumentLimits.validate("a".repeat(RagDocumentLimits.MAX_CHARACTERS + 1)) }
        assertFailsWith<IllegalArgumentException> { RagDocumentLimits.validate(" ".repeat(100)) }
    }

    @Test
    fun boundsRetrievedContextForUnicodeDocuments() = runTest {
        val sources = List(5) { RagSource("Documento", "Informacao acentuada 日本語 😀 ".repeat(300), 0.9f) }
        val prompt = RagPromptBuilder().prepare("O que diz o documento?", sources) { it.encodeToByteArray().size }.prompt
        val options = GenerationOptions.GroundedAnswering
        assertTrue(prompt.encodeToByteArray().size <= options.contextSize - options.maxTokens - 64)
        assertTrue(prompt.contains("Informacao"))
    }

    @Test
    fun rejectsQuestionThatLeavesNoRoomForSources() = runTest {
        assertFailsWith<IllegalArgumentException> {
            RagPromptBuilder().prepare("a".repeat(4096), listOf(RagSource("PDF", "a".repeat(100), 1f))) { it.encodeToByteArray().size }
        }
    }
}
