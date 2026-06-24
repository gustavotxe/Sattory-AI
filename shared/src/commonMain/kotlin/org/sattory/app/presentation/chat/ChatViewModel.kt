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
import org.sattory.app.llm.LocalLlm
import org.sattory.app.model.ModelPreparationUseCase
import org.sattory.app.model.ModelStatus

class ChatViewModel(
    private val localLlm: LocalLlm,
    private val modelPreparationUseCase: ModelPreparationUseCase,
) : ViewModel() {
    private val intents = Channel<ChatIntent>(Channel.UNLIMITED)
    private var generationJob: Job? = null

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        modelPreparationUseCase.ensureModel()

        viewModelScope.launch {
            intents.receiveAsFlow().collect { intent -> processIntent(intent) }
        }

        viewModelScope.launch {
            modelPreparationUseCase.observeStatus().collectLatest { modelStatus ->
                _state.update { it.copy(modelStatus = modelStatus) }
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
            ChatIntent.ToggleDesktopNavigation -> _state.update {
                it.copy(desktopNavigationExpanded = !it.desktopNavigationExpanded)
            }
            ChatIntent.NewConversation -> newConversation()
            ChatIntent.SendMessage -> sendMessage()
            ChatIntent.CancelEditing -> _state.update {
                it.copy(input = "", editingMessageId = null, selectedMessageId = null)
            }
            ChatIntent.RetryModelDownload -> {
                _state.update { it.copy(modelStatus = ModelStatus.Queued) }
                modelPreparationUseCase.retryDownload()
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
                editingIndex?.let { localLlm.truncateConversation(it) }
                localLlm.generate(prompt).collect { token ->
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
            )
        }
        generationJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            localLlm.resetConversation()
        }
    }

    private var id = 0L
    private fun nextId(): Long = ++id
}
