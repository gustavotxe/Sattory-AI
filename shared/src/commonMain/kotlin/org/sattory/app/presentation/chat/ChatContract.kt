package org.sattory.app.presentation.chat

import org.sattory.app.conversation.domain.SavedConversationSummary
import org.sattory.app.model.ModelStatus
import org.sattory.app.rag.domain.DocumentCollection
import org.sattory.app.rag.domain.RagIndexState
import org.sattory.app.rag.domain.RagSource

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
    val embeddingModelStatus: ModelStatus = ModelStatus.Checking,
    val collections: List<DocumentCollection> = emptyList(),
    val selectedCollectionId: Long? = null,
    val collectionTitleInput: String = "",
    val collectionTextInput: String = "",
    val editingCollectionId: Long? = null,
    val ragIndexState: RagIndexState = RagIndexState.Idle,
    val activeSources: List<RagSource> = emptyList(),
    val savedConversations: List<SavedConversationSummary> = emptyList(),
    val showSaveConversationDialog: Boolean = false,
    val showOpenSavedConversationDialog: Boolean = false,
    val showNewConversationDialog: Boolean = false,
    val pendingSavedConversationId: Long? = null,
    val isSavingConversation: Boolean = false,
    val currentSavedConversationId: Long? = null,
    val currentConversationTitle: String? = null,
    val currentConversationSaved: Boolean = false,
    val saveConversationMessage: String? = null,
) {
    val selectedCollection: DocumentCollection?
        get() = selectedCollectionId?.let { id -> collections.firstOrNull { it.id == id } }
}

enum class ThemeMode(val label: String) {
    Light("Claro"),
    Dark("Escuro"),
}

enum class ChatDestination(val label: String) {
    Chat("Conversa"),
    SavedConversations("Conversas salvas"),
    FileProfiles("Arquivos"),
    Settings("Ajustes"),
}

sealed interface ChatIntent {
    data class InputChanged(val value: String) : ChatIntent
    data class DestinationSelected(val destination: ChatDestination) : ChatIntent
    data class ThemeSelected(val themeMode: ThemeMode) : ChatIntent
    data class SuggestionSelected(val text: String) : ChatIntent
    data class EditMessage(val messageId: Long) : ChatIntent
    data class ToggleMessageActions(val messageId: Long) : ChatIntent
    data class CollectionSelected(val collectionId: Long?) : ChatIntent
    data class CollectionTitleChanged(val value: String) : ChatIntent
    data class CollectionTextChanged(val value: String) : ChatIntent
    data class EditCollection(val collectionId: Long) : ChatIntent
    data class DeleteCollection(val collectionId: Long) : ChatIntent
    data class DeleteSavedConversation(val conversationId: Long) : ChatIntent
    data class OpenSavedConversation(val conversationId: Long) : ChatIntent
    data object ToggleDesktopNavigation : ChatIntent
    data object SendMessage : ChatIntent
    data object RequestSaveConversation : ChatIntent
    data object ConfirmSaveConversation : ChatIntent
    data object DismissSaveConversation : ChatIntent
    data object ConfirmOpenSavedConversation : ChatIntent
    data object SaveAndOpenSavedConversation : ChatIntent
    data object DismissOpenSavedConversation : ChatIntent
    data object ConfirmNewConversation : ChatIntent
    data object SaveAndNewConversation : ChatIntent
    data object DismissNewConversation : ChatIntent
    data object CreateCollection : ChatIntent
    data object SaveCollection : ChatIntent
    data object CancelCollectionEditing : ChatIntent
    data object CancelEditing : ChatIntent
    data object NewConversation : ChatIntent
    data object RetryModelDownload : ChatIntent
    data object RetryEmbeddingModelDownload : ChatIntent
}
