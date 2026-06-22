package org.sattory.app.presentation.chat

import org.sattory.app.model.ModelStatus

data class ChatMessage(
    val id: Long,
    val text: String,
    val isFromUser: Boolean,
)

data class ChatState(
    val input: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val selectedDestination: ChatDestination = ChatDestination.Chat,
    val isGenerating: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.Checking,
)

enum class ChatDestination(val label: String) {
    Chat("Chat"),
    Library("Biblioteca"),
    History("Histórico"),
    Settings("Ajustes"),
}

sealed interface ChatIntent {
    data class InputChanged(val value: String) : ChatIntent
    data class DestinationSelected(val destination: ChatDestination) : ChatIntent
    data class SuggestionSelected(val text: String) : ChatIntent
    data object SendMessage : ChatIntent
    data object NewConversation : ChatIntent
    data object RetryModelDownload : ChatIntent
}
