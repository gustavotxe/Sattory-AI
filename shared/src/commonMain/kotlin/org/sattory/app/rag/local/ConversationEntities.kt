package org.sattory.app.rag.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_conversations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("collectionId"), Index("savedAtMillis")],
)
data class SavedConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val collectionId: Long?,
    val collectionTitle: String?,
    val savedAtMillis: Long,
)

@Entity(
    tableName = "saved_conversation_messages",
    foreignKeys = [
        ForeignKey(
            entity = SavedConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class SavedConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val messageIndex: Int,
    val text: String,
    val isFromUser: Boolean,
)

data class SavedConversationSummaryEntity(
    val id: Long,
    val title: String,
    val collectionTitle: String?,
    val savedAtMillis: Long,
    val messageCount: Int,
)

data class SavedConversationMessageRow(
    val text: String,
    val isFromUser: Boolean,
)
