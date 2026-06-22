package org.sattory.app.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.sattory.app.llm.LocalLlm
import org.sattory.app.model.ModelRepository
import org.sattory.app.model.ModelStatus

class ChatViewModel(
    private val localLlm: LocalLlm,
    private val modelRepository: ModelRepository,
) : ViewModel() {
    private var generationJob: Job? = null

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        modelRepository.ensureModel()
        viewModelScope.launch {
            modelRepository.status.collect { modelStatus ->
                _state.update { it.copy(modelStatus = modelStatus) }
            }
        }
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InputChanged -> _state.update { it.copy(input = intent.value) }
            is ChatIntent.DestinationSelected -> _state.update { it.copy(selectedDestination = intent.destination) }
            is ChatIntent.SuggestionSelected -> _state.update { it.copy(input = intent.text) }
            ChatIntent.NewConversation -> newConversation()
            ChatIntent.SendMessage -> sendMessage()
            ChatIntent.RetryModelDownload -> modelRepository.retryDownload()
        }
    }

    private fun sendMessage() {
        val prompt = state.value.input.trim()
        if (
            prompt.isEmpty() || state.value.isGenerating || generationJob?.isActive == true ||
            state.value.modelStatus !is ModelStatus.Ready
        ) return

        val userMessage = ChatMessage(id = nextId(), text = prompt, isFromUser = true)
        _state.update { it.copy(input = "", messages = it.messages + userMessage, isGenerating = true) }

        generationJob = viewModelScope.launch {
            val responseId = nextId()
            val response = StringBuilder()
            var responseStarted = false
            try {
                localLlm.generate(prompt).collect { token ->
                    response.append(token)
                    val message = ChatMessage(responseId, response.toString(), isFromUser = false)
                    if (responseStarted) {
                        _state.update { current ->
                            current.copy(messages = current.messages.map { if (it.id == responseId) message else it })
                        }
                    } else {
                        responseStarted = true
                        _state.update { current -> current.copy(messages = current.messages + message) }
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
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun newConversation() {
        val previousJob = generationJob
        _state.update { ChatState(selectedDestination = it.selectedDestination, modelStatus = it.modelStatus) }
        generationJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            localLlm.resetConversation()
        }
    }

    private var id = 0L
    private fun nextId(): Long = ++id
}
