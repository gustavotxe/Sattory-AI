package org.sattory.app.conversation.domain

import kotlinx.coroutines.flow.Flow

interface SavedConversationRepository {
    fun observeSummaries(): Flow<List<SavedConversationSummary>>
    suspend fun save(input: SaveConversationInput): Long
    suspend fun delete(conversationId: Long)
}
