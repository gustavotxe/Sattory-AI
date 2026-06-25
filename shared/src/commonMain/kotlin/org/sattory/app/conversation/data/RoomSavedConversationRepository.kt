package org.sattory.app.conversation.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sattory.app.conversation.domain.SaveConversationInput
import org.sattory.app.conversation.domain.SavedConversationRepository
import org.sattory.app.conversation.domain.SavedConversationSummary
import org.sattory.app.rag.data.currentEpochMillis
import org.sattory.app.rag.data.local.SavedConversationDao
import org.sattory.app.rag.data.local.SavedConversationEntity

class RoomSavedConversationRepository(
    private val dao: SavedConversationDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SavedConversationRepository {
    override fun observeSummaries(): Flow<List<SavedConversationSummary>> =
        dao.observeSummaries().map { rows ->
            rows.map {
                SavedConversationSummary(
                    id = it.id,
                    title = it.title,
                    collectionTitle = it.collectionTitle,
                    savedAtMillis = it.savedAtMillis,
                    messageCount = it.messageCount,
                )
            }
        }

    override suspend fun save(input: SaveConversationInput): Long = withContext(ioDispatcher) {
        val messages = input.messages.filter { it.text.isNotBlank() }
        require(messages.isNotEmpty()) { "Nao ha mensagens para salvar" }
        dao.insertConversationWithMessages(
            conversation = SavedConversationEntity(
                title = input.title.trim().ifBlank { "Conversa sem titulo" },
                collectionId = input.collectionId,
                collectionTitle = input.collectionTitle,
                savedAtMillis = currentEpochMillis(),
            ),
            messages = messages.map { it.isFromUser to it.text },
        )
    }

    override suspend fun delete(conversationId: Long) = withContext(ioDispatcher) {
        dao.deleteConversation(conversationId)
    }
}
