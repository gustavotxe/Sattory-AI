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
