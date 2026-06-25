package org.sattory.app.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.sattory.app.conversation.domain.ConversationMessageDraft
import org.sattory.app.conversation.domain.DeleteSavedConversationUseCase
import org.sattory.app.conversation.domain.ObserveSavedConversationsUseCase
import org.sattory.app.conversation.domain.SaveConversationInput
import org.sattory.app.conversation.domain.SaveConversationUseCase
import org.sattory.app.llm.LocalLlm
import org.sattory.app.model.EmbeddingModelPreparationUseCase
import org.sattory.app.model.ModelPreparationUseCase
import org.sattory.app.model.ModelStatus
import org.sattory.app.rag.domain.CreateCollectionInput
import org.sattory.app.rag.domain.CreateCollectionUseCase
import org.sattory.app.rag.domain.DeleteCollectionUseCase
import org.sattory.app.rag.domain.GetCollectionDraftUseCase
import org.sattory.app.rag.domain.ObserveCollectionsUseCase
import org.sattory.app.rag.domain.RagIndexState
import org.sattory.app.rag.domain.RagPromptBuilder
import org.sattory.app.rag.domain.RetrieveContextUseCase
import org.sattory.app.rag.domain.UpdateCollectionUseCase

class ChatViewModel(
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
    private val deleteSavedConversationUseCase: DeleteSavedConversationUseCase,
) : ViewModel() {
    private val intents = Channel<ChatIntent>(Channel.UNLIMITED)
    private var generationJob: Job? = null

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        modelPreparationUseCase.ensureModel()
        embeddingModelPreparationUseCase.ensureModel()

        viewModelScope.launch {
            intents.receiveAsFlow().collect { intent -> processIntent(intent) }
        }

        viewModelScope.launch {
            modelPreparationUseCase.observeStatus().collectLatest { modelStatus ->
                _state.update { it.copy(modelStatus = modelStatus) }
            }
        }

        viewModelScope.launch {
            embeddingModelPreparationUseCase.observeStatus().collectLatest { modelStatus ->
                _state.update { it.copy(embeddingModelStatus = modelStatus) }
            }
        }

        viewModelScope.launch {
            observeCollectionsUseCase().collectLatest { collections ->
                _state.update { current ->
                    current.copy(
                        collections = collections,
                        selectedCollectionId = current.selectedCollectionId?.takeIf { selectedId ->
                            collections.any { it.id == selectedId }
                        },
                    )
                }
            }
        }

        viewModelScope.launch {
            observeSavedConversationsUseCase().collectLatest { conversations ->
                _state.update { it.copy(savedConversations = conversations) }
            }
        }
    }

    fun onIntent(intent: ChatIntent) {
        intents.trySend(intent)
    }

    override fun onCleared() {
        intents.close()
        super.onCleared()
    }

    private suspend fun processIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InputChanged -> _state.update { it.copy(input = intent.value) }
            is ChatIntent.DestinationSelected -> _state.update { it.copy(selectedDestination = intent.destination) }
            is ChatIntent.ThemeSelected -> _state.update { it.copy(themeMode = intent.themeMode) }
            is ChatIntent.SuggestionSelected -> _state.update { it.copy(input = intent.text) }
            is ChatIntent.EditMessage -> editMessage(intent.messageId)
            is ChatIntent.ToggleMessageActions -> toggleMessageActions(intent.messageId)
            is ChatIntent.CollectionSelected -> _state.update { it.copy(selectedCollectionId = intent.collectionId) }
            is ChatIntent.CollectionTitleChanged -> _state.update { it.copy(collectionTitleInput = intent.value) }
            is ChatIntent.CollectionTextChanged -> _state.update { it.copy(collectionTextInput = intent.value) }
            is ChatIntent.EditCollection -> editCollection(intent.collectionId)
            is ChatIntent.DeleteCollection -> deleteCollection(intent.collectionId)
            is ChatIntent.DeleteSavedConversation -> deleteSavedConversation(intent.conversationId)
            ChatIntent.ToggleDesktopNavigation -> _state.update {
                it.copy(desktopNavigationExpanded = !it.desktopNavigationExpanded)
            }
            ChatIntent.NewConversation -> newConversation()
            ChatIntent.SendMessage -> sendMessage()
            ChatIntent.RequestSaveConversation -> requestSaveConversation()
            ChatIntent.ConfirmSaveConversation -> saveCurrentConversation()
            ChatIntent.DismissSaveConversation -> _state.update {
                it.copy(showSaveConversationDialog = false, saveConversationMessage = null)
            }
            ChatIntent.CreateCollection -> createCollection()
            ChatIntent.SaveCollection -> saveCollection()
            ChatIntent.CancelCollectionEditing -> cancelCollectionEditing()
            ChatIntent.CancelEditing -> _state.update {
                it.copy(input = "", editingMessageId = null, selectedMessageId = null)
            }
            ChatIntent.RetryModelDownload -> {
                _state.update { it.copy(modelStatus = ModelStatus.Queued) }
                modelPreparationUseCase.retryDownload()
            }
            ChatIntent.RetryEmbeddingModelDownload -> {
                _state.update { it.copy(embeddingModelStatus = ModelStatus.Queued) }
                embeddingModelPreparationUseCase.retryDownload()
            }
        }
    }

    private suspend fun sendMessage() {
        val currentState = state.first()
        val prompt = currentState.input.trim()
        if (
            prompt.isEmpty() || currentState.isGenerating || generationJob?.isActive == true ||
            currentState.modelStatus !is ModelStatus.Ready
        ) return

        val editingIndex = currentState.editingMessageId?.let { id ->
            currentState.messages.indexOfFirst { it.id == id && it.isFromUser }.takeIf { it >= 0 }
        }
        if (currentState.editingMessageId != null && editingIndex == null) return

        val userMessage = ChatMessage(
            id = currentState.editingMessageId ?: nextId(),
            text = prompt,
            isFromUser = true,
        )
        _state.update {
            it.copy(
                input = "",
                messages = editingIndex?.let { index -> it.messages.take(index) + userMessage }
                    ?: (it.messages + userMessage),
                isGenerating = true,
                isAwaitingFirstToken = true,
                editingMessageId = null,
                selectedMessageId = null,
            )
        }

        generationJob = viewModelScope.launch {
            val responseId = nextId()
            val response = StringBuilder()
            var responseStarted = false
            try {
                val sources = retrieveContextUseCase(currentState.selectedCollectionId, prompt)
                _state.update { it.copy(activeSources = sources) }
                val generationPrompt = ragPromptBuilder.build(prompt, sources)
                editingIndex?.let { localLlm.truncateConversation(it) }
                localLlm.generate(generationPrompt).collect { token ->
                    response.append(token)
                    val message = ChatMessage(responseId, response.toString(), isFromUser = false)
                    if (responseStarted) {
                        _state.update { current ->
                            current.copy(messages = current.messages.map { if (it.id == responseId) message else it })
                        }
                    } else {
                        responseStarted = true
                        _state.update { current ->
                            current.copy(
                                messages = current.messages + message,
                                isAwaitingFirstToken = false,
                            )
                        }
                    }
                }
                if (!responseStarted) {
                    _state.update {
                        it.copy(messages = it.messages + ChatMessage(responseId, "O modelo não produziu resposta.", false))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = ChatMessage(
                    responseId,
                    "Não foi possível executar o modelo local: ${error.message ?: error::class.simpleName}",
                    isFromUser = false,
                )
                _state.update { current ->
                    current.copy(
                        messages = if (responseStarted) {
                            current.messages.map { if (it.id == responseId) message else it }
                        } else {
                            current.messages + message
                        },
                    )
                }
            } finally {
                _state.update { it.copy(isGenerating = false, isAwaitingFirstToken = false) }
            }
        }
    }

    private suspend fun editMessage(messageId: Long) {
        val currentState = state.first()
        if (currentState.isGenerating || generationJob?.isActive == true) return
        val message = currentState.messages.firstOrNull { it.id == messageId && it.isFromUser } ?: return
        _state.update {
            it.copy(input = message.text, editingMessageId = messageId, selectedMessageId = null)
        }
    }

    private suspend fun toggleMessageActions(messageId: Long) {
        val currentState = state.first()
        if (currentState.isGenerating) return
        val isUserMessage = currentState.messages.any { it.id == messageId && it.isFromUser }
        if (!isUserMessage) return
        _state.update {
            it.copy(selectedMessageId = if (it.selectedMessageId == messageId) null else messageId)
        }
    }

    private suspend fun newConversation() {
        val previousJob = generationJob
        _state.update {
            ChatState(
                selectedDestination = it.selectedDestination,
                themeMode = it.themeMode,
                desktopNavigationExpanded = it.desktopNavigationExpanded,
                modelStatus = it.modelStatus,
                embeddingModelStatus = it.embeddingModelStatus,
                collections = it.collections,
                selectedCollectionId = it.selectedCollectionId,
                editingCollectionId = it.editingCollectionId,
                savedConversations = it.savedConversations,
            )
        }
        generationJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            localLlm.resetConversation()
        }
    }

    private var id = 0L
    private fun nextId(): Long = ++id

    private fun createCollection() {
        val current = state.value
        if (current.ragIndexState == RagIndexState.Indexing) return
        viewModelScope.launch {
            try {
                _state.update { it.copy(ragIndexState = RagIndexState.Indexing) }
                val collectionId = createCollectionUseCase(
                    CreateCollectionInput(
                        name = current.collectionTitleInput,
                        text = current.collectionTextInput,
                    ),
                )
                _state.update {
                    it.copy(
                        selectedCollectionId = collectionId,
                        collectionTitleInput = "",
                        collectionTextInput = "",
                        editingCollectionId = null,
                        ragIndexState = RagIndexState.Ready,
                        selectedDestination = ChatDestination.Chat,
                    )
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(ragIndexState = RagIndexState.Failed(error.message ?: "Nao foi possivel indexar a colecao"))
                }
            }
        }
    }

    private fun deleteCollection(collectionId: Long) {
        viewModelScope.launch {
            deleteCollectionUseCase(collectionId)
            _state.update {
                it.copy(
                    selectedCollectionId = it.selectedCollectionId.takeUnless { selected -> selected == collectionId },
                    editingCollectionId = it.editingCollectionId.takeUnless { editing -> editing == collectionId },
                )
            }
        }
    }

    private fun editCollection(collectionId: Long) {
        viewModelScope.launch {
            val draft = getCollectionDraftUseCase(collectionId) ?: return@launch
            _state.update {
                it.copy(
                    selectedDestination = ChatDestination.FileProfiles,
                    editingCollectionId = draft.id,
                    collectionTitleInput = draft.title,
                    collectionTextInput = draft.text,
                    ragIndexState = RagIndexState.Idle,
                )
            }
        }
    }

    private fun saveCollection() {
        val current = state.value
        val editingId = current.editingCollectionId ?: return
        if (current.ragIndexState == RagIndexState.Indexing) return
        viewModelScope.launch {
            try {
                _state.update { it.copy(ragIndexState = RagIndexState.Indexing) }
                updateCollectionUseCase(
                    editingId,
                    CreateCollectionInput(
                        name = current.collectionTitleInput,
                        text = current.collectionTextInput,
                    ),
                )
                _state.update {
                    it.copy(
                        selectedCollectionId = editingId,
                        collectionTitleInput = "",
                        collectionTextInput = "",
                        editingCollectionId = null,
                        ragIndexState = RagIndexState.Ready,
                    )
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(ragIndexState = RagIndexState.Failed(error.message ?: "Nao foi possivel salvar a colecao"))
                }
            }
        }
    }

    private fun cancelCollectionEditing() {
        _state.update {
            it.copy(
                collectionTitleInput = "",
                collectionTextInput = "",
                editingCollectionId = null,
                ragIndexState = RagIndexState.Idle,
            )
        }
    }

    private fun requestSaveConversation() {
        val current = state.value
        if (current.messages.isEmpty() || current.isGenerating || current.isSavingConversation) return
        _state.update {
            it.copy(showSaveConversationDialog = true, saveConversationMessage = null)
        }
    }

    private fun saveCurrentConversation() {
        val current = state.value
        if (current.messages.isEmpty() || current.isSavingConversation) return
        viewModelScope.launch {
            try {
                _state.update { it.copy(isSavingConversation = true) }
                saveConversationUseCase(
                    SaveConversationInput(
                        title = current.conversationTitle(),
                        collectionId = current.selectedCollectionId,
                        collectionTitle = current.selectedCollection?.name,
                        messages = current.messages.map {
                            ConversationMessageDraft(text = it.text, isFromUser = it.isFromUser)
                        },
                    ),
                )
                _state.update {
                    it.copy(
                        showSaveConversationDialog = false,
                        isSavingConversation = false,
                        saveConversationMessage = "Conversa salva localmente",
                    )
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isSavingConversation = false,
                        saveConversationMessage = error.message ?: "Nao foi possivel salvar a conversa",
                    )
                }
            }
        }
    }

    private fun deleteSavedConversation(conversationId: Long) {
        viewModelScope.launch {
            deleteSavedConversationUseCase(conversationId)
        }
    }

    private fun ChatState.conversationTitle(): String =
        messages.firstOrNull { it.isFromUser }?.text
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.take(64)
            ?.ifBlank { null }
            ?: "Conversa local"
}
