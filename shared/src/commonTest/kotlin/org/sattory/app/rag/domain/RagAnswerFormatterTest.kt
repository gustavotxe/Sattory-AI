package org.sattory.app.rag.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import org.sattory.app.presentation.ChatState

class RagAnswerFormatterTest {
    @Test
    fun removesOnlyContradictoryTrailingRefusal() {
        assertEquals("Telemaco e o filho de Odisseu.", RagAnswerFormatter.format(
            "Telemaco e o filho de Odisseu. O material enviado nao informa isso.",
        ))
        assertEquals("Cila tinha seis cabecas.", RagAnswerFormatter.format(
            "Cila tinha seis cabecas.\nO material enviado não informa isso.\nO material enviado nao informa isso.",
        ))
    }

    @Test
    fun preservesStandaloneRefusalAndUnrelatedAnswers() {
        val refusal = "O material enviado nao informa isso."
        assertEquals(refusal, RagAnswerFormatter.format(refusal))
        assertEquals("Resposta sem sufixo.", RagAnswerFormatter.format("Resposta sem sufixo."))
        val explanation = "O material enviado nao informa isso. A pergunta exige outra fonte."
        assertEquals(explanation, RagAnswerFormatter.format(explanation))
    }

    @Test
    fun listsEachDocumentOnceWithoutDiscardingRetrievedChunks() {
        val state = ChatState(activeSources = listOf(
            RagSource("odisseia", "primeiro trecho", 0.9f),
            RagSource("odisseia ", "segundo trecho", 0.8f),
            RagSource("Outro documento", "outro trecho", 0.7f),
        ))
        assertEquals(listOf("odisseia", "Outro documento"), state.activeSourceDocumentTitles)
        assertEquals(3, state.activeSources.size)
    }
}
