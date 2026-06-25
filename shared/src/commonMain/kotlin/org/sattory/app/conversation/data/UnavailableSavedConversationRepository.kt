package org.sattory.app.conversation.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.sattory.app.conversation.domain.SaveConversationInput
import org.sattory.app.conversation.domain.SavedConversationRepository
import org.sattory.app.conversation.domain.SavedConversationSummary

class UnavailableSavedConversationRepository(
    private val reason: String = "Persistencia de conversas nao configurada",
) : SavedConversationRepository {
    override fun observeSummaries(): Flow<List<SavedConversationSummary>> = flowOf(emptyList())
    override suspend fun save(input: SaveConversationInput): Long = error(reason)
    override suspend fun delete(conversationId: Long) = Unit
}
