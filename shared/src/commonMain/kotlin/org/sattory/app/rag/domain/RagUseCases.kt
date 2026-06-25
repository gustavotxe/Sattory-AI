package org.sattory.app.rag.domain

import kotlinx.coroutines.flow.Flow

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
        val context = sources.joinToString(separator = "\n\n") { source ->
            "[${source.documentTitle} | score=${source.score.formatScore()}]\n${source.text}"
        }
        return """
            Use exclusivamente as fontes abaixo quando elas forem relevantes. Se as fontes nao responderem a pergunta, diga isso com clareza e responda somente com o que for seguro inferir.

            Fontes:
            $context

            Pergunta do usuario:
            $userPrompt
        """.trimIndent()
    }

    private fun Float.formatScore(): String = ((this * 100).toInt() / 100f).toString()
}
