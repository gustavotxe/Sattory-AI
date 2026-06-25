package org.sattory.app.rag.domain

import kotlinx.coroutines.flow.Flow

interface RagRepository {
    fun observeCollections(): Flow<List<DocumentCollection>>
    suspend fun createCollection(input: CreateCollectionInput): Long
    suspend fun collectionDraft(collectionId: Long): CollectionDraft?
    suspend fun updateCollection(collectionId: Long, input: CreateCollectionInput)
    suspend fun deleteCollection(collectionId: Long)
    suspend fun retrieve(collectionId: Long, query: String, limit: Int = 5): List<RagSource>
}
