package org.sattory.app.presentation

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.sattory.app.conversation.domain.DeleteSavedConversationUseCase
import org.sattory.app.conversation.domain.GetSavedConversationUseCase
import org.sattory.app.conversation.domain.ObserveSavedConversationsUseCase
import org.sattory.app.conversation.domain.SaveConversationUseCase
import org.sattory.app.llm.LocalLlm
import org.sattory.app.model.EmbeddingModelPreparationUseCase
import org.sattory.app.model.ModelPreparationUseCase
import org.sattory.app.model.ModelStatus
import org.sattory.app.rag.domain.CreateCollectionUseCase
import org.sattory.app.rag.domain.DeleteCollectionUseCase
import org.sattory.app.rag.domain.GetCollectionDraftUseCase
import org.sattory.app.rag.domain.ImportPdfUseCase
import org.sattory.app.rag.domain.ObserveCollectionsUseCase
import org.sattory.app.rag.domain.RagPromptBuilder
import org.sattory.app.rag.domain.RagAnswerFormatter
import org.sattory.app.rag.domain.RagQueryBuilder
import org.sattory.app.llm.GenerationOptions
import org.sattory.app.rag.domain.RetrieveContextUseCase
import org.sattory.app.rag.domain.UpdateCollectionUseCase

private sealed interface ChatAction {
    data class Intent(val intent: ChatIntent) : ChatAction
    data class Event(val event: ChatEvent) : ChatAction
    data class Barrier(val completion: CompletableDeferred<Unit>) : ChatAction
}

class ChatStore(
    private val localLlm: LocalLlm,
    private val modelPreparationUseCase: ModelPreparationUseCase,
    private val embeddingModelPreparationUseCase: EmbeddingModelPreparationUseCase,
    private val observeCollectionsUseCase: ObserveCollectionsUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val getCollectionDraftUseCase: GetCollectionDraftUseCase,
    private val updateCollectionUseCase: UpdateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val retrieveContextUseCase: RetrieveContextUseCase,
    private val ragPromptBuilder: RagPromptBuilder,
    private val observeSavedConversationsUseCase: ObserveSavedConversationsUseCase,
    private val saveConversationUseCase: SaveConversationUseCase,
    private val getSavedConversationUseCase: GetSavedConversationUseCase,
    private val deleteSavedConversationUseCase: DeleteSavedConversationUseCase,
    private val importPdfUseCase: ImportPdfUseCase? = null,
    parentScope: CoroutineScope,
) {
    private val storeJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val storeScope = CoroutineScope(parentScope.coroutineContext + storeJob)
    private val actionQueue = Channel<ChatAction>(Channel.UNLIMITED)
    private var generationJob: Job? = null

    private val _state = MutableStateFlow(ChatState())
    private val savedConversationEffects by lazy {
        ChatSavedConversationEffects(
            scope = storeScope,
            state = state,
            saveUseCase = saveConversationUseCase,
            getUseCase = getSavedConversationUseCase,
            emit = ::enqueueEvent,
            apply = ::applyEvent,
            clearConversation = ::clearCurrentConversation,
            cancelGeneration = ::cancelGeneration,
            resetConversation = localLlm::resetConversation,
            nextId = ::nextId,
        )
    }
    private val collectionEffects by lazy {
        ChatCollectionEffects(
            scope = storeScope,
            state = state,
            importPdfUseCase = importPdfUseCase,
            createCollectionUseCase = createCollectionUseCase,
            getCollectionDraftUseCase = getCollectionDraftUseCase,
            updateCollectionUseCase = updateCollectionUseCase,
            deleteCollectionUseCase = deleteCollectionUseCase,
            emit = ::enqueueEvent,
            apply = ::applyEvent,
        )
    }
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        storeScope.launch {
            actionQueue.receiveAsFlow().collect { action ->
                try {
                    when (action) {
                        is ChatAction.Intent -> handleIntent(action.intent)
                        is ChatAction.Event -> applyEvent(action.event)
                        is ChatAction.Barrier -> action.completion.complete(Unit)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (action is ChatAction.Intent) {
                        applyEvent(ChatEvent.EffectFailed(action.intent, error.message ?: "Falha ao executar a ação."))
                    }
                }
            }
        }

        storeScope.launch {
            modelPreparationUseCase.observeStatus().collectLatest { modelStatus ->
                enqueueEvent(ChatEvent.ModelStatusChanged(modelStatus))
            }
        }

        storeScope.launch {
            embeddingModelPreparationUseCase.observeStatus().collectLatest { modelStatus ->
                enqueueEvent(ChatEvent.EmbeddingModelStatusChanged(modelStatus))
            }
        }

        storeScope.launch {
            observeCollectionsUseCase().collectLatest { collections ->
                enqueueEvent(ChatEvent.CollectionsObserved(collections))
            }
        }

        storeScope.launch {
            observeSavedConversationsUseCase().collectLatest { conversations ->
                enqueueEvent(ChatEvent.SavedConversationsObserved(conversations))
            }
        }
    }

    /** Entry point used by the UI: intents travel upward, state is observed separately. */
    fun dispatch(intent: ChatIntent) {
        actionQueue.trySend(ChatAction.Intent(intent))
    }

    /** Effects report results through the same serialized loop that handles UI intents. */
    private fun enqueueEvent(event: ChatEvent) {
        actionQueue.trySend(ChatAction.Event(event))
    }

    /** Waits until all events sent by this effect have reached the reducer. */
    private suspend fun awaitPendingEvents() {
        val completed = CompletableDeferred<Unit>()
        actionQueue.send(ChatAction.Barrier(completed))
        completed.await()
    }

    /** Applies a synchronous transition while the action loop is handling its intent. */
    private fun applyEvent(event: ChatEvent) {
        _state.value = ChatReducer.reduce(_state.value, event)
    }

    /** Cancels ongoing effects when the owning UI leaves composition. */
    fun close() {
        actionQueue.close()
        storeScope.cancel()
    }

    private suspend fun handleIntent(intent: ChatIntent) {
        when (val reduction = ChatReducer.reduce(state.value, intent)) {
            is ChatIntentReduction.StateUpdated -> {
                _state.value = reduction.state
                return
            }
            ChatIntentReduction.EffectRequired -> Unit
        }
        when (intent) {
            is ChatIntent.EditMessage -> editMessage(intent.messageId)
            is ChatIntent.ToggleMessageActions -> toggleMessageActions(intent.messageId)
            is ChatIntent.PdfSelected -> collectionEffects.importPdf(intent.reference)
            is ChatIntent.EditCollection -> launchEffect(intent) { collectionEffects.editCollection(intent.collectionId) }
            is ChatIntent.DeleteCollection -> launchEffect(intent) { collectionEffects.deleteCollection(intent.collectionId) }
            is ChatIntent.DeleteSavedConversation -> deleteSavedConversation(intent.conversationId)
            is ChatIntent.OpenSavedConversation -> openSavedConversation(intent.conversationId)
            ChatIntent.NewConversation -> launchEffect(intent) { newConversation() }
            ChatIntent.SendMessage -> sendMessage()
            ChatIntent.ContinueLastResponse -> continueLastResponse()
            ChatIntent.RequestSaveConversation -> requestSaveConversation()
            ChatIntent.ConfirmSaveConversation -> savedConversationEffects.saveCurrentConversation()
            ChatIntent.ConfirmOpenSavedConversation -> openPendingSavedConversation(saveCurrentFirst = false)
            ChatIntent.SaveAndOpenSavedConversation -> openPendingSavedConversation(saveCurrentFirst = true)
            ChatIntent.ConfirmNewConversation -> launchEffect(intent) { clearCurrentConversation() }
            ChatIntent.SaveAndNewConversation -> savedConversationEffects.saveCurrentConversation(newAfterSave = true)
            ChatIntent.CreateCollection -> collectionEffects.createCollection()
            ChatIntent.SaveCollection -> collectionEffects.saveCollection()
            ChatIntent.CancelCollectionEditing -> collectionEffects.cancelCollectionEditing()
            ChatIntent.RetryModelDownload -> {
                enqueueEvent(ChatEvent.ModelStatusChanged(ModelStatus.Queued))
                modelPreparationUseCase.retryDownload()
            }
            ChatIntent.RetryEmbeddingModelDownload -> {
                enqueueEvent(ChatEvent.EmbeddingModelStatusChanged(ModelStatus.Queued))
                embeddingModelPreparationUseCase.retryDownload()
            }
            is ChatIntent.InputChanged,
            is ChatIntent.DestinationSelected,
            is ChatIntent.ThemeSelected,
            is ChatIntent.SuggestionSelected,
            is ChatIntent.CollectionSelected,
            is ChatIntent.CollectionTitleChanged,
            is ChatIntent.CollectionTextChanged,
            ChatIntent.ToggleDesktopNavigation,
            ChatIntent.DismissSaveConversation,
            ChatIntent.DismissOpenSavedConversation,
            ChatIntent.DismissNewConversation,
            ChatIntent.CancelEditing -> error("State intent was not reduced: $intent")
        }
    }

    private fun launchEffect(intent: ChatIntent, effect: suspend () -> Unit) {
        storeScope.launch {
            try {
                effect()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                enqueueEvent(ChatEvent.EffectFailed(intent, error.message ?: "Falha ao executar a ação."))
            }
        }
    }

    private suspend fun sendMessage(
        promptOverride: String? = null,
        retrieveSources: Boolean = true,
    ) {
        val currentState = state.first()
        val prompt = promptOverride ?: currentState.input.trim()
        if (
            prompt.isEmpty() || currentState.isGenerating || generationJob?.isActive == true ||
            currentState.modelStatus !is ModelStatus.Ready
        ) return

        val editingMessageId = if (promptOverride == null) currentState.editingMessageId else null
        val editingIndex = editingMessageId?.let { id ->
            currentState.messages.indexOfFirst { it.id == id && it.isFromUser }.takeIf { it >= 0 }
        }
        if (editingMessageId != null && editingIndex == null) return

        val userMessage = ChatMessage(
            id = editingMessageId ?: nextId(),
            text = prompt,
            isFromUser = true,
        )
        applyEvent(ChatEvent.GenerationStarted(userMessage, editingIndex))

        generationJob = storeScope.launch {
            val assistantMessageId = nextId()
            val generatedText = StringBuilder()
            var hasReceivedResponseToken = false
            var generationCompletedNormally = false
            var shouldAutosaveConversation = false
            var shouldUseLanguageModel = false
            try {
                val isDocumentGroundedQuery = retrieveSources && currentState.selectedCollectionId != null
                val previousUserQuestion = currentState.messages.take(editingIndex ?: currentState.messages.size)
                    .lastOrNull { it.isFromUser }?.text
                val retrievalQuery = if (isDocumentGroundedQuery) RagQueryBuilder().build(prompt, previousUserQuestion) else prompt
                val retrievedSources = if (retrieveSources) {
                    retrieveContextUseCase(currentState.selectedCollectionId, retrievalQuery)
                } else {
                    emptyList()
                }
                val preparedRagPrompt = if (isDocumentGroundedQuery && retrievedSources.isNotEmpty()) {
                    ragPromptBuilder.prepare(retrievalQuery, retrievedSources, localLlm::countPromptTokens)
                } else null
                if (retrieveSources) enqueueEvent(ChatEvent.SourcesRetrieved(preparedRagPrompt?.sources ?: retrievedSources))
                val promptForGeneration = preparedRagPrompt?.prompt ?: prompt
                val generationOptions = when {
                    isDocumentGroundedQuery -> GenerationOptions.GroundedAnswering
                    !retrieveSources && currentState.selectedCollectionId != null -> GenerationOptions.GroundedAnswering.copy(isolatedContext = false)
                    else -> GenerationOptions()
                }
                editingIndex?.let { localLlm.truncateConversation(it) }
                shouldUseLanguageModel = !isDocumentGroundedQuery || preparedRagPrompt?.sources?.isNotEmpty() == true
                val responseTokens = if (shouldUseLanguageModel) localLlm.generate(promptForGeneration, generationOptions)
                    else flowOf("O material enviado nao informa isso.")
                responseTokens.collect { token ->
                    generatedText.append(token)
                    val message = ChatMessage(assistantMessageId, generatedText.toString(), isFromUser = false)
                    if (hasReceivedResponseToken) {
                        enqueueEvent(ChatEvent.AssistantTokenReceived(message, isFirst = false))
                    } else {
                        hasReceivedResponseToken = true
                        enqueueEvent(ChatEvent.AssistantTokenReceived(message, isFirst = true))
                    }
                }
                if (!hasReceivedResponseToken) {
                    enqueueEvent(ChatEvent.AssistantMessageReplaced(
                        ChatMessage(assistantMessageId, "O modelo não produziu resposta.", false),
                        alreadyPresent = false,
                    ))
                }
                if (hasReceivedResponseToken && retrievedSources.isNotEmpty()) {
                    val formattedAnswer = RagAnswerFormatter.format(generatedText.toString())
                    enqueueEvent(ChatEvent.AssistantAnswerFormatted(assistantMessageId, formattedAnswer))
                }
                generationCompletedNormally = true
                shouldAutosaveConversation = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = ChatMessage(
                    assistantMessageId,
                    "Não foi possível executar o modelo local: ${error.message ?: error::class.simpleName}",
                    isFromUser = false,
                )
                enqueueEvent(ChatEvent.AssistantMessageReplaced(message, alreadyPresent = hasReceivedResponseToken))
            } finally {
                var cancellation: CancellationException? = null
                val responseStoppedAtTokenLimit = if (generationCompletedNormally && shouldUseLanguageModel && hasReceivedResponseToken) {
                    try {
                        localLlm.wasLastGenerationTokenLimited()
                    } catch (cancelled: CancellationException) {
                        cancellation = cancelled
                        false
                    } catch (_: Exception) {
                        false
                    }
                } else false
                enqueueEvent(ChatEvent.GenerationFinished(responseStoppedAtTokenLimit))
                if (cancellation != null) throw cancellation
                if (shouldAutosaveConversation) {
                    awaitPendingEvents()
                    savedConversationEffects.autosaveCurrentConversation()
                }
            }
        }
    }

    private suspend fun continueLastResponse() {
        val currentState = state.first()
        if (!currentState.canContinueLastResponse || currentState.isGenerating) return
        sendMessage(promptOverride = ContinuationPrompt, retrieveSources = false)
    }

    private suspend fun editMessage(messageId: Long) {
        val currentState = state.first()
        if (currentState.isGenerating || generationJob?.isActive == true) return
        val message = currentState.messages.firstOrNull { it.id == messageId && it.isFromUser } ?: return
        applyEvent(ChatEvent.MessageEditingStarted(messageId, message.text))
    }

    private suspend fun toggleMessageActions(messageId: Long) {
        val currentState = state.first()
        if (currentState.isGenerating) return
        val hasMessage = currentState.messages.any { it.id == messageId }
        if (!hasMessage) return
        applyEvent(ChatEvent.MessageActionsToggled(messageId))
    }

    private suspend fun newConversation() {
        val conversationState = state.first()
        if (conversationState.messages.isNotEmpty() && !conversationState.currentConversationSaved) {
            applyEvent(ChatEvent.NewConversationNeedsSave)
            return
        }
        clearCurrentConversation()
    }

    private suspend fun clearCurrentConversation() {
        cancelGeneration()
        localLlm.resetConversation()
        enqueueEvent(ChatEvent.ConversationCleared)
    }

    private suspend fun cancelGeneration() {
        val previousJob = generationJob
        generationJob = null
        previousJob?.cancelAndJoin()
    }

    private var nextMessageId = 0L
    private fun nextId(): Long = ++nextMessageId

    private fun requestSaveConversation() {
        val conversationState = state.value
        if (conversationState.messages.isEmpty() || conversationState.isGenerating || conversationState.isSavingConversation) return
        applyEvent(ChatEvent.SaveDialogRequested)
    }

    private fun deleteSavedConversation(conversationId: Long) {
        launchEffect(ChatIntent.DeleteSavedConversation(conversationId)) {
            deleteSavedConversationUseCase(conversationId)
            enqueueEvent(ChatEvent.SavedConversationDeleted(conversationId))
        }
    }

    private fun openSavedConversation(conversationId: Long) {
        val conversationState = state.value
        if (conversationState.messages.isNotEmpty() && !conversationState.currentConversationSaved) {
            applyEvent(ChatEvent.OpenSavedConversationPending(conversationId))
        } else {
            launchEffect(ChatIntent.OpenSavedConversation(conversationId)) {
                savedConversationEffects.loadSavedConversation(conversationId)
            }
        }
    }

    private fun openPendingSavedConversation(saveCurrentFirst: Boolean) {
        val pendingId = state.value.pendingSavedConversationId ?: return
        if (saveCurrentFirst) {
            savedConversationEffects.saveCurrentConversation(openAfterSave = true)
        } else {
            launchEffect(ChatIntent.ConfirmOpenSavedConversation) {
                savedConversationEffects.loadSavedConversation(pendingId)
            }
        }
    }

    private companion object {
        const val ContinuationPrompt = "continue"
    }
}

