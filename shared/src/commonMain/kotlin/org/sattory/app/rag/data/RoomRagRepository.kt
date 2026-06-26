package org.sattory.app.rag.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sattory.app.rag.data.local.DocumentCollectionEntity
import org.sattory.app.rag.data.local.RagDao
import org.sattory.app.rag.data.local.RagChunkWithDocumentEntity
import org.sattory.app.rag.domain.CollectionDraft
import org.sattory.app.rag.domain.CreateCollectionInput
import org.sattory.app.rag.domain.DocumentCollection
import org.sattory.app.rag.domain.RagRepository
import org.sattory.app.rag.domain.RagSource
import org.sattory.app.rag.domain.TextEmbedder

class RoomRagRepository(
    private val dao: RagDao,
    private val embedder: TextEmbedder,
    private val chunker: RagTextChunker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
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

        val chunks = chunker.chunk(documentText)
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
        val title = input.name.trim()
        val documentText = input.text.trim()
        require(collectionId > 0L) { "Colecao invalida" }
        require(title.isNotEmpty()) { "Informe um titulo para a colecao" }
        require(documentText.length >= MIN_TEXT_LENGTH) { "Cole um texto com pelo menos $MIN_TEXT_LENGTH caracteres" }
        val chunks = chunker.chunk(documentText)
        require(chunks.isNotEmpty()) { "Nao foi possivel criar chunks para o texto" }
        val embeddedChunks = chunks.map { chunk ->
            chunk to VectorCodec.encode(
                VectorCodec.normalized(embedder.embedForDocument(RagEmbeddingTextNormalizer.normalize(chunk))),
            )
        }
        dao.replaceCollectionDocumentAndChunks(
            collectionId = collectionId,
            title = title,
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
        if (collectionId <= 0L || normalizedQuery.isEmpty()) return@withContext emptyList()
        val queryEmbedding = VectorCodec.normalized(
            embedder.embedForQuery(RagEmbeddingTextNormalizer.normalize(normalizedQuery)),
        )
        val chunks = dao.chunksForCollection(collectionId)
        val queryTerms = normalizedQuery.toSearchTerms()
        val scoredChunks = chunks
            .map { it.toScoredChunk(queryEmbedding, queryTerms) }
            .filter { it.isRelevant() }
            .sortedByDescending { it.score }
            .take(limit)
        val selectedChunks = if (scoredChunks.isNotEmpty()) {
            scoredChunks
        } else {
            chunks
                .map { it.toScoredChunk(queryEmbedding, queryTerms) }
                .maxByOrNull { it.score }
                ?.takeIf { it.semanticScore >= FALLBACK_SEMANTIC_SCORE || it.lexicalScore >= FALLBACK_LEXICAL_SCORE }
                ?.let(::listOf)
                .orEmpty()
        }
        selectedChunks
            .map { it.toContextualSource(chunks) }
            .distinctBy { it.text }
            .take(limit)
            .toList()
    }

    private fun RagChunkWithDocumentEntity.toScoredChunk(
        queryEmbedding: FloatArray,
        queryTerms: Set<String>,
    ): ScoredChunk {
        val semanticScore = VectorCodec.cosine(queryEmbedding, VectorCodec.decode(embedding))
        val lexicalScore = text.lexicalOverlap(queryTerms)
        val score = (semanticScore * SEMANTIC_WEIGHT) + (lexicalScore * LEXICAL_WEIGHT)
        return ScoredChunk(chunk = this, score = score, semanticScore = semanticScore, lexicalScore = lexicalScore)
    }

    private fun ScoredChunk.isRelevant(): Boolean =
        score >= MIN_HYBRID_SCORE ||
            semanticScore >= MIN_SEMANTIC_SCORE ||
            lexicalScore >= MIN_LEXICAL_SCORE

    private fun ScoredChunk.toContextualSource(chunks: List<RagChunkWithDocumentEntity>): RagSource {
        val neighbors = chunks
            .asSequence()
            .filter { candidate ->
                candidate.documentId == chunk.documentId &&
                    candidate.chunkIndex in (chunk.chunkIndex - CONTEXT_RADIUS)..(chunk.chunkIndex + CONTEXT_RADIUS)
            }
            .sortedBy { it.chunkIndex }
            .map { it.text }
            .toList()
            .joinToString(separator = "\n...\n")
            .trim()
            .take(CONTEXT_SOURCE_MAX_CHARS)
        return RagSource(
            documentTitle = chunk.documentTitle,
            text = neighbors,
            score = score,
        )
    }

    private fun String.lexicalOverlap(queryTerms: Set<String>): Float {
        if (queryTerms.isEmpty()) return 0f
        val textTerms = toSearchTerms()
        if (textTerms.isEmpty()) return 0f
        val matches = queryTerms.count { it in textTerms }
        return matches.toFloat() / queryTerms.size
    }

    private fun String.toSearchTerms(): Set<String> =
        lowercase()
            .split(SEARCH_SPLIT_REGEX)
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= MIN_TERM_LENGTH && it !in STOP_WORDS }
            .toSet()

    private data class ScoredChunk(
        val chunk: RagChunkWithDocumentEntity,
        val score: Float,
        val semanticScore: Float,
        val lexicalScore: Float,
    )

    private companion object {
        const val MIN_TEXT_LENGTH = 40
        const val SEMANTIC_WEIGHT = 0.82f
        const val LEXICAL_WEIGHT = 0.18f
        const val MIN_HYBRID_SCORE = 0.08f
        const val MIN_SEMANTIC_SCORE = 0.09f
        const val MIN_LEXICAL_SCORE = 0.22f
        const val FALLBACK_SEMANTIC_SCORE = 0.045f
        const val FALLBACK_LEXICAL_SCORE = 0.14f
        const val CONTEXT_RADIUS = 1
        const val CONTEXT_SOURCE_MAX_CHARS = 2_400
        const val MIN_TERM_LENGTH = 3
        val SEARCH_SPLIT_REGEX = Regex("[^\\p{L}\\p{N}]+")
        val STOP_WORDS = setOf(
            "que",
            "qual",
            "quais",
            "como",
            "para",
            "por",
            "uma",
            "das",
            "dos",
            "com",
            "sobre",
            "isso",
            "esta",
            "esse",
            "essa",
            "ele",
            "ela",
            "the",
            "and",
            "for",
            "with",
            "what",
            "which",
            "this",
            "that",
        )
    }
}
