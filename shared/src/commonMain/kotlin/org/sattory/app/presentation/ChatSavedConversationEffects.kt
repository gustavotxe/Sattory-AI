package org.sattory.app.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.sattory.app.conversation.domain.ConversationMessageDraft
import org.sattory.app.conversation.domain.GetSavedConversationUseCase
import org.sattory.app.conversation.domain.SaveConversationInput
import org.sattory.app.conversation.domain.SaveConversationUseCase

internal class ChatSavedConversationEffects(
    private val scope: CoroutineScope,
    private val state: StateFlow<ChatState>,
    private val saveUseCase: SaveConversationUseCase,
    private val getUseCase: GetSavedConversationUseCase,
    private val emit: (ChatEvent) -> Unit,
    private val apply: (ChatEvent) -> Unit,
    private val clearConversation: suspend () -> Unit,
    private val cancelGeneration: suspend () -> Unit,
    private val resetConversation: suspend () -> Unit,
    private val nextId: () -> Long,
) {
    fun saveCurrentConversation(openAfterSave: Boolean = false, newAfterSave: Boolean = false) {
        val current = state.value
        if (current.messages.isEmpty() || current.isSavingConversation) return
        val title = current.conversationTitle()
        apply(ChatEvent.SaveStarted)
        scope.launch {
            try {
                val savedId = saveUseCase(current.toSaveInput(title))
                val pendingId = state.value.pendingSavedConversationId
                emit(ChatEvent.ConversationSaved(id = savedId, title = title, message = "Conversa salva localmente"))
                if (openAfterSave && pendingId != null) {
                    loadSavedConversation(pendingId)
                } else if (newAfterSave) {
                    clearConversation()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(ChatEvent.ConversationSaveFailed(error.message ?: "Nao foi possivel salvar a conversa"))
            }
        }
    }

    suspend fun autosaveCurrentConversation() {
        val current = state.value
        val id = current.currentSavedConversationId ?: return
        if (current.messages.isEmpty()) return
        val title = current.currentConversationTitle ?: current.conversationTitle()
        try {
            val savedId = saveUseCase(current.toSaveInput(title, id))
            emit(ChatEvent.ConversationAutosaved(id = savedId, title = title))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            emit(ChatEvent.ConversationAutosaveFailed(error.message ?: "Nao foi possivel atualizar a conversa salva"))
        }
    }

    suspend fun loadSavedConversation(conversationId: Long) {
        val saved = getUseCase(conversationId) ?: return
        cancelGeneration()
        resetConversation()
        emit(ChatEvent.SavedConversationLoaded(
            id = saved.id,
            title = saved.title,
            collectionId = saved.collectionId,
            messages = saved.messages.map { message ->
                ChatMessage(id = nextId(), text = message.text, isFromUser = message.isFromUser)
            },
        ))
    }

    private fun ChatState.toSaveInput(title: String, conversationId: Long? = currentSavedConversationId) =
        SaveConversationInput(
            conversationId = conversationId,
            title = title,
            collectionId = selectedCollectionId,
            collectionTitle = selectedCollection?.name,
            messages = messages.map { ConversationMessageDraft(it.text, it.isFromUser) },
        )

    private fun ChatState.conversationTitle(): String =
        messages.firstOrNull { it.isFromUser }?.text
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.take(64)
            ?.ifBlank { null }
            ?: "Conversa local"
}
