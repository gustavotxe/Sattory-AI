package org.sattory.app.rag.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.sattory.app.rag.domain.CreateCollectionInput
import org.sattory.app.rag.domain.CollectionDraft
import org.sattory.app.rag.domain.DocumentCollection
import org.sattory.app.rag.domain.RagRepository
import org.sattory.app.rag.domain.RagSource

class UnavailableRagRepository(
    private val reason: String = "RAG local nao configurado",
) : RagRepository {
    override fun observeCollections(): Flow<List<DocumentCollection>> = flowOf(emptyList())
    override suspend fun createCollection(input: CreateCollectionInput): Long = error(reason)
    override suspend fun collectionDraft(collectionId: Long): CollectionDraft? = null
    override suspend fun updateCollection(collectionId: Long, input: CreateCollectionInput) = error(reason)
    override suspend fun deleteCollection(collectionId: Long) = Unit
    override suspend fun retrieve(collectionId: Long, query: String, limit: Int): List<RagSource> = emptyList()
}
