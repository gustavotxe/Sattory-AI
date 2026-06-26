package org.sattory.app.rag.domain

import kotlinx.coroutines.flow.Flow
import org.sattory.app.llm.GenerationOptions

class ObserveCollectionsUseCase(private val repository: RagRepository) {
    operator fun invoke(): Flow<List<DocumentCollection>> = repository.observeCollections()
}

class CreateCollectionUseCase(private val repository: RagRepository) {
    suspend operator fun invoke(input: CreateCollectionInput): Long = repository.createCollection(input)
}

class GetCollectionDraftUseCase(private val repository: RagRepository) {
    suspend operator fun invoke(collectionId: Long): CollectionDraft? = repository.collectionDraft(collectionId)
}

class UpdateCollectionUseCase(private val repository: RagRepository) {
    suspend operator fun invoke(collectionId: Long, input: CreateCollectionInput) =
        repository.updateCollection(collectionId, input)
}

class DeleteCollectionUseCase(private val repository: RagRepository) {
    suspend operator fun invoke(collectionId: Long) = repository.deleteCollection(collectionId)
}

class RetrieveContextUseCase(private val repository: RagRepository) {
    suspend operator fun invoke(collectionId: Long?, query: String): List<RagSource> {
        return collectionId?.let { repository.retrieve(it, query) }.orEmpty()
    }
}

class RagPromptBuilder {
    fun build(userPrompt: String, sources: List<RagSource>): String {
        if (sources.isEmpty()) return userPrompt
        val context = sources.mapIndexed { index, source ->
            val sourceId = index + 1
            """
                [Fonte $sourceId | ${source.documentTitle} | score=${source.score.formatScore()}]
                ${source.text}
            """.trimIndent()
        }.joinToString(separator = "\n\n")
        return """
            Responda no mesmo idioma da pergunta, usando as Fontes desta mensagem como base principal.

            Regras de evidencia:
            - Responda quando a informacao estiver claramente apoiada pelas fontes, mesmo que precise resumir, combinar frases proximas ou parafrasear.
            - Voce pode interpretar relacoes diretas, sinonimos e referencias proximas quando isso for natural pelo contexto.
            - Quando a pergunta pedir uma propriedade ou relacao especifica, confirme que o sujeito e a propriedade estejam ligados pelo contexto da fonte.
            - Nao atribua a uma pessoa, coisa ou lugar uma caracteristica que a fonte declarou claramente para outra entidade.
            - Nao use fontes de mensagens anteriores da conversa para responder esta pergunta.
            - Use "O material enviado nao informa isso." apenas quando nenhuma fonte recuperada apoiar a resposta.
            - Seja natural e direto; evite repetir mecanicamente as fontes.

            Fontes:
            $context

            Pergunta:
            $userPrompt

            Resposta:
        """.trimIndent()
    }

    fun optionsFor(sources: List<RagSource>): GenerationOptions {
        return if (sources.isEmpty()) {
            GenerationOptions()
        } else {
            GenerationOptions.GroundedAnswering
        }
    }

    private fun Float.formatScore(): String = ((this * 100).toInt() / 100f).toString()
}
