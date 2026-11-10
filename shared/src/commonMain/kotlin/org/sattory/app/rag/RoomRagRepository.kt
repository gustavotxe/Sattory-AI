package org.sattory.app.rag

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sattory.app.rag.local.DocumentCollectionEntity
import org.sattory.app.rag.local.RagDao
import org.sattory.app.rag.domain.CollectionDraft
import org.sattory.app.rag.domain.CreateCollectionInput
import org.sattory.app.rag.domain.DocumentCollection
import org.sattory.app.rag.domain.RagDocumentLimits
import org.sattory.app.rag.domain.RagRepository
import org.sattory.app.rag.domain.RagSource
import org.sattory.app.rag.domain.TextEmbedder

class RoomRagRepository(
    private val dao: RagDao,
    private val embedder: TextEmbedder,
    private val chunker: RagTextChunker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ranker: RagHybridRanker = RagHybridRanker(),
) : RagRepository {
    override fun observeCollections(): Flow<List<DocumentCollection>> =
        dao.observeCollections().map { rows ->
            rows.map {
                DocumentCollection(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    documentCount = it.documentCount,
                    chunkCount = it.chunkCount,
                    createdAtMillis = it.createdAtMillis,
                    updatedAtMillis = it.updatedAtMillis,
                )
            }
        }

    override suspend fun createCollection(input: CreateCollectionInput): Long = withContext(ioDispatcher) {
        val name = input.name.trim()
        val documentText = input.text.trim()
        require(name.isNotEmpty()) { "Informe um nome para a colecao" }
        require(documentText.length >= MIN_TEXT_LENGTH) { "Cole um texto com pelo menos $MIN_TEXT_LENGTH caracteres" }

        RagDocumentLimits.validate(documentText)
        val chunks = chunker.chunkForEmbedding(documentText) { text ->
            embedder.countDocumentTokens(RagEmbeddingTextNormalizer.normalize(text))
        }
        require(chunks.isNotEmpty()) { "Nao foi possivel criar chunks para o texto" }
        val embeddedChunks = chunks.map { chunk ->
            chunk to VectorCodec.encode(
                VectorCodec.normalized(embedder.embedForDocument(RagEmbeddingTextNormalizer.normalize(chunk))),
            )
        }
        val now = currentEpochMillis()
        dao.insertCollectionDocumentAndChunks(
            collection = DocumentCollectionEntity(
                name = name,
                description = "",
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
            documentTitle = name,
            documentText = documentText,
            chunks = embeddedChunks,
        )
    }

    override suspend fun collectionDraft(collectionId: Long): CollectionDraft? = withContext(ioDispatcher) {
        dao.collectionDraft(collectionId)?.let {
            CollectionDraft(id = it.id, title = it.title, text = it.text)
        }
    }

    override suspend fun updateCollection(collectionId: Long, input: CreateCollectionInput) = withContext(ioDispatcher) {
        val name = input.name.trim()
        val documentText = input.text.trim()
        require(collectionId > 0L) { "Colecao invalida" }
        require(name.isNotEmpty()) { "Informe um titulo para a colecao" }
        require(documentText.length >= MIN_TEXT_LENGTH) { "Cole um texto com pelo menos $MIN_TEXT_LENGTH caracteres" }
        RagDocumentLimits.validate(documentText)
        val chunks = chunker.chunkForEmbedding(documentText) { text ->
            embedder.countDocumentTokens(RagEmbeddingTextNormalizer.normalize(text))
        }
        require(chunks.isNotEmpty()) { "Nao foi possivel criar chunks para o texto" }
        val embeddedChunks = chunks.map { chunk ->
            chunk to VectorCodec.encode(
                VectorCodec.normalized(embedder.embedForDocument(RagEmbeddingTextNormalizer.normalize(chunk))),
            )
        }
        dao.replaceCollectionDocumentAndChunks(
            collectionId = collectionId,
            title = name,
            documentText = documentText,
            updatedAtMillis = currentEpochMillis(),
            chunks = embeddedChunks,
        )
    }

    override suspend fun deleteCollection(collectionId: Long) = withContext(ioDispatcher) {
        dao.deleteCollection(collectionId)
    }

    override suspend fun retrieve(collectionId: Long, query: String, limit: Int): List<RagSource> = withContext(ioDispatcher) {
        val normalizedQuery = query.trim()
        if (collectionId <= 0L || normalizedQuery.isEmpty() || limit <= 0) return@withContext emptyList()
        val chunks = dao.chunksForCollection(collectionId)
        if (chunks.isEmpty()) return@withContext emptyList()
        val queryEmbedding = VectorCodec.normalized(
            embedder.embedForQuery(RagEmbeddingTextNormalizer.normalize(normalizedQuery)),
        )
        ranker.rank(chunks, normalizedQuery, queryEmbedding, limit)
    }

    private companion object {
        const val MIN_TEXT_LENGTH = 40
    }
}
