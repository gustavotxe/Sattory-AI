package org.sattory.app.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InputChanged -> _state.update { it.copy(input = intent.value) }
            is ChatIntent.DestinationSelected -> _state.update { it.copy(selectedDestination = intent.destination) }
            is ChatIntent.SuggestionSelected -> _state.update { it.copy(input = intent.text) }
            ChatIntent.NewConversation -> _state.update { ChatState(selectedDestination = it.selectedDestination) }
            ChatIntent.SendMessage -> sendMessage()
        }
    }

    private fun sendMessage() {
        val prompt = state.value.input.trim()
        if (prompt.isEmpty() || state.value.isGenerating) return

        val userMessage = ChatMessage(id = nextId(), text = prompt, isFromUser = true)
        _state.update { it.copy(input = "", messages = it.messages + userMessage, isGenerating = true) }

        viewModelScope.launch {
            delay(450)
            val response = ChatMessage(
                id = nextId(),
                text = "A base da conversa está pronta. Quando conectarmos seus documentos e o modelo local, responderei usando apenas o contexto encontrado neles.",
                isFromUser = false,
            )
            _state.update { it.copy(messages = it.messages + response, isGenerating = false) }
        }
    }

    private var id = 0L
    private fun nextId(): Long = ++id
}
