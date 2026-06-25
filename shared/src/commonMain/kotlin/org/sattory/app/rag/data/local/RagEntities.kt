package org.sattory.app.rag.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_collections",
    indices = [Index(value = ["name"], unique = true)],
)
data class DocumentCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "rag_documents",
    foreignKeys = [
        ForeignKey(
            entity = DocumentCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId")],
)
data class RagDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val collectionId: Long,
    val title: String,
    val text: String,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "rag_chunks",
    foreignKeys = [
        ForeignKey(
            entity = RagDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DocumentCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId"), Index("documentId")],
)
data class RagChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val collectionId: Long,
    val documentId: Long,
    val chunkIndex: Int,
    val text: String,
    val embedding: ByteArray,
)

data class DocumentCollectionStatsEntity(
    val id: Long,
    val name: String,
    val description: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val documentCount: Int,
    val chunkCount: Int,
)

data class RagChunkWithDocumentEntity(
    val chunkId: Long,
    val documentTitle: String,
    val text: String,
    val embedding: ByteArray,
)

data class CollectionDraftEntity(
    val id: Long,
    val title: String,
    val text: String,
)
