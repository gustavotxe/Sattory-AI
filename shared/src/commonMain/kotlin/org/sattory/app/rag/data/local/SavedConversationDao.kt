package org.sattory.app.rag.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Update
    suspend fun updateConversation(conversation: SavedConversationEntity)

    @Query("DELETE FROM saved_conversation_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)

    @Query("DELETE FROM saved_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: Long)

    @Query("SELECT * FROM saved_conversations WHERE id = :conversationId")
    suspend fun conversationById(conversationId: Long): SavedConversationEntity?

    @Query(
        """
        SELECT text, isFromUser
        FROM saved_conversation_messages
        WHERE conversationId = :conversationId
        ORDER BY messageIndex ASC
        """,
    )
    suspend fun messagesForConversation(conversationId: Long): List<SavedConversationMessageRow>

    @Transaction
    suspend fun insertConversationWithMessages(
        conversation: SavedConversationEntity,
        messages: List<Pair<Boolean, String>>,
    ): Long {
        val conversationId = insertConversation(conversation)
        insertMessagesForConversation(conversationId, messages)
        return conversationId
    }

    @Transaction
    suspend fun upsertConversationWithMessages(
        conversationId: Long?,
        conversation: SavedConversationEntity,
        messages: List<Pair<Boolean, String>>,
    ): Long {
        val existingConversation = conversationId?.let { conversationById(it) }
        return if (existingConversation == null) {
            insertConversationWithMessages(conversation, messages)
        } else {
            updateConversation(conversation.copy(id = existingConversation.id))
            deleteMessagesForConversation(existingConversation.id)
            insertMessagesForConversation(existingConversation.id, messages)
            existingConversation.id
        }
    }

    private suspend fun insertMessagesForConversation(
        conversationId: Long,
        messages: List<Pair<Boolean, String>>,
    ) {
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
    }
}
