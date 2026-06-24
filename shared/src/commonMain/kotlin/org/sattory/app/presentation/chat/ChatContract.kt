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
    val themeMode: ThemeMode = ThemeMode.Light,
    val desktopNavigationExpanded: Boolean = true,
    val isGenerating: Boolean = false,
    val isAwaitingFirstToken: Boolean = false,
    val editingMessageId: Long? = null,
    val selectedMessageId: Long? = null,
    val modelStatus: ModelStatus = ModelStatus.Checking,
)

enum class ThemeMode(val label: String) {
    Light("Claro"),
    Dark("Escuro"),
}

enum class ChatDestination(val label: String) {
    Chat("Conversa"),
    SavedConversations("Conversas salvas"),
    FileProfiles("Arquivos"),
    History("Historico"),
    Settings("Ajustes"),
}

sealed interface ChatIntent {
    data class InputChanged(val value: String) : ChatIntent
    data class DestinationSelected(val destination: ChatDestination) : ChatIntent
    data class ThemeSelected(val themeMode: ThemeMode) : ChatIntent
    data class SuggestionSelected(val text: String) : ChatIntent
    data class EditMessage(val messageId: Long) : ChatIntent
    data class ToggleMessageActions(val messageId: Long) : ChatIntent
    data object ToggleDesktopNavigation : ChatIntent
    data object SendMessage : ChatIntent
    data object CancelEditing : ChatIntent
    data object NewConversation : ChatIntent
    data object RetryModelDownload : ChatIntent
}
