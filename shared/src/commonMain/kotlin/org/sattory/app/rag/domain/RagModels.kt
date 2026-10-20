package org.sattory.app.rag.domain

data class DocumentCollection(
    val id: Long,
    val name: String,
    val description: String,
    val documentCount: Int,
    val chunkCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class CollectionDraft(
    val id: Long,
    val title: String,
    val text: String,
)

data class RagSource(
    val documentTitle: String,
    val text: String,
    val score: Float,
    val chunkIndex: Int? = null,
    val documentId: Long? = null,
)

sealed interface RagIndexState {
    data object Idle : RagIndexState
    data object PreparingEmbeddingModel : RagIndexState
    data object Indexing : RagIndexState
    data object Ready : RagIndexState
    data class Failed(val message: String) : RagIndexState
}

data class CreateCollectionInput(
    val name: String,
    val text: String,
)
