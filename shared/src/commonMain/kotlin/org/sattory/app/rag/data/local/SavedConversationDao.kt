package org.sattory.app.rag.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedConversationDao {
    @Query(
        """
        SELECT
            c.id,
            c.title,
            c.collectionTitle,
            c.savedAtMillis,
            COUNT(m.id) AS messageCount
        FROM saved_conversations c
        LEFT JOIN saved_conversation_messages m ON m.conversationId = c.id
        GROUP BY c.id
        ORDER BY c.savedAtMillis DESC
        """,
    )
    fun observeSummaries(): Flow<List<SavedConversationSummaryEntity>>

    @Insert
    suspend fun insertConversation(conversation: SavedConversationEntity): Long

    @Insert
    suspend fun insertMessages(messages: List<SavedConversationMessageEntity>)

    @Query("DELETE FROM saved_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    @Transaction
    suspend fun insertConversationWithMessages(
        conversation: SavedConversationEntity,
        messages: List<Pair<Boolean, String>>,
    ): Long {
        val conversationId = insertConversation(conversation)
        insertMessages(
            messages.mapIndexed { index, message ->
                SavedConversationMessageEntity(
                    conversationId = conversationId,
                    messageIndex = index,
                    isFromUser = message.first,
                    text = message.second,
                )
            },
        )
        return conversationId
    }
}
