package org.sattory.app.rag.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDao {
    @Query(
        """
        SELECT
            c.id,
            c.name,
            c.description,
            c.createdAtMillis,
            c.updatedAtMillis,
            COUNT(DISTINCT d.id) AS documentCount,
            COUNT(ch.id) AS chunkCount
        FROM document_collections c
        LEFT JOIN rag_documents d ON d.collectionId = c.id
        LEFT JOIN rag_chunks ch ON ch.collectionId = c.id
        GROUP BY c.id
        ORDER BY c.updatedAtMillis DESC
        """,
    )
    fun observeCollections(): Flow<List<DocumentCollectionStatsEntity>>

    @Query(
        """
        SELECT
            c.id,
            c.name,
            c.description,
            c.createdAtMillis,
            c.updatedAtMillis,
            COUNT(DISTINCT d.id) AS documentCount,
            COUNT(ch.id) AS chunkCount
        FROM document_collections c
        LEFT JOIN rag_documents d ON d.collectionId = c.id
        LEFT JOIN rag_chunks ch ON ch.collectionId = c.id
        WHERE c.id = :collectionId
        GROUP BY c.id
        """,
    )
    suspend fun collectionById(collectionId: Long): DocumentCollectionStatsEntity?

    @Insert
    suspend fun insertCollection(collection: DocumentCollectionEntity): Long

    @Insert
    suspend fun insertDocument(document: RagDocumentEntity): Long

    @Insert
    suspend fun insertChunks(chunks: List<RagChunkEntity>)

    @Query("DELETE FROM document_collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: Long)

    @Query(
        """
        SELECT c.id AS id, c.name AS title, d.text AS text
        FROM document_collections c
        INNER JOIN rag_documents d ON d.collectionId = c.id
        WHERE c.id = :collectionId
        ORDER BY d.createdAtMillis ASC
        LIMIT 1
        """,
    )
    suspend fun collectionDraft(collectionId: Long): CollectionDraftEntity?

    @Query("UPDATE document_collections SET name = :title, description = '', updatedAtMillis = :updatedAtMillis WHERE id = :collectionId")
    suspend fun updateCollectionTitle(collectionId: Long, title: String, updatedAtMillis: Long)

    @Query("DELETE FROM rag_documents WHERE collectionId = :collectionId")
    suspend fun deleteDocumentsForCollection(collectionId: Long)

    @Query(
        """
        SELECT
            ch.id AS chunkId,
            ch.documentId AS documentId,
            ch.chunkIndex AS chunkIndex,
            d.title AS documentTitle,
            ch.text AS text,
            ch.embedding AS embedding
        FROM rag_chunks ch
        INNER JOIN rag_documents d ON d.id = ch.documentId
        WHERE ch.collectionId = :collectionId
        ORDER BY ch.documentId ASC, ch.chunkIndex ASC
        """,
    )
    suspend fun chunksForCollection(collectionId: Long): List<RagChunkWithDocumentEntity>

    @Transaction
    suspend fun insertCollectionDocumentAndChunks(
        collection: DocumentCollectionEntity,
        documentTitle: String,
        documentText: String,
        chunks: List<Pair<String, ByteArray>>,
    ): Long {
        val collectionId = insertCollection(collection)
        val documentId = insertDocument(
            RagDocumentEntity(
                collectionId = collectionId,
                title = documentTitle,
                text = documentText,
                createdAtMillis = collection.createdAtMillis,
            ),
        )
        insertChunks(
            chunks.mapIndexed { index, chunk ->
                RagChunkEntity(
                    collectionId = collectionId,
                    documentId = documentId,
                    chunkIndex = index,
                    text = chunk.first,
                    embedding = chunk.second,
                )
            },
        )
        return collectionId
    }

    @Transaction
    suspend fun replaceCollectionDocumentAndChunks(
        collectionId: Long,
        title: String,
        documentText: String,
        updatedAtMillis: Long,
        chunks: List<Pair<String, ByteArray>>,
    ) {
        updateCollectionTitle(collectionId, title, updatedAtMillis)
        deleteDocumentsForCollection(collectionId)
        val documentId = insertDocument(
            RagDocumentEntity(
                collectionId = collectionId,
                title = title,
                text = documentText,
                createdAtMillis = updatedAtMillis,
            ),
        )
        insertChunks(
            chunks.mapIndexed { index, chunk ->
                RagChunkEntity(
                    collectionId = collectionId,
                    documentId = documentId,
                    chunkIndex = index,
                    text = chunk.first,
                    embedding = chunk.second,
                )
            },
        )
    }
}
