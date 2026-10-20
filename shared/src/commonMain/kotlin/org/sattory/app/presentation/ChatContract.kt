package org.sattory.app.presentation

import org.sattory.app.conversation.domain.SavedConversationSummary
import org.sattory.app.model.ModelStatus
import org.sattory.app.rag.domain.DocumentCollection
import org.sattory.app.rag.domain.RagDocumentLimits
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
    val isImportingPdf: Boolean = false,
    val pdfImportError: String? = null,
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
    val canContinueLastResponse: Boolean = false,
) {
    val isCollectionBusy: Boolean
        get() = isImportingPdf || ragIndexState == RagIndexState.Indexing

    val activeSourceDocumentTitles: List<String>
        get() = activeSources.map { it.documentTitle.trim() }.filter { it.isNotEmpty() }.distinct()

    val canSubmitCollection: Boolean
        get() = collectionTitleInput.isNotBlank() &&
            collectionTextInput.trim().length in 40..RagDocumentLimits.MAX_CHARACTERS &&
            !isImportingPdf && ragIndexState != RagIndexState.Indexing && embeddingModelStatus is ModelStatus.Ready

    val selectedCollection: DocumentCollection?
        get() = selectedCollectionId?.let { id -> collections.firstOrNull { it.id == id } }
}

enum class ThemeMode(val label: String) {
    Light("Claro"),
    Dark("Escuro"),
}

enum class ChatDestination(val label: String) {
    Chat("Conversa"),
    SavedConversations("Conversas Salvas"),
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
    data class PdfSelected(val reference: String) : ChatIntent
    data class EditCollection(val collectionId: Long) : ChatIntent
    data class DeleteCollection(val collectionId: Long) : ChatIntent
    data class DeleteSavedConversation(val conversationId: Long) : ChatIntent
    data class OpenSavedConversation(val conversationId: Long) : ChatIntent
    data object ToggleDesktopNavigation : ChatIntent
    data object SendMessage : ChatIntent
    data object ContinueLastResponse : ChatIntent
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

sealed interface ChatEvent {
    data class EffectFailed(val intent: ChatIntent, val message: String) : ChatEvent
    data class ModelStatusChanged(val status: ModelStatus) : ChatEvent
    data class EmbeddingModelStatusChanged(val status: ModelStatus) : ChatEvent
    data class CollectionsObserved(val collections: List<DocumentCollection>) : ChatEvent
    data class SavedConversationsObserved(val conversations: List<SavedConversationSummary>) : ChatEvent
    data class GenerationStarted(val userMessage: ChatMessage, val editingIndex: Int?) : ChatEvent
    data class SourcesRetrieved(val sources: List<RagSource>) : ChatEvent
    data class AssistantTokenReceived(val message: ChatMessage, val isFirst: Boolean) : ChatEvent
    data class AssistantMessageReplaced(val message: ChatMessage, val alreadyPresent: Boolean) : ChatEvent
    data class AssistantAnswerFormatted(val messageId: Long, val text: String) : ChatEvent
    data class GenerationFinished(val canContinue: Boolean) : ChatEvent
    data class MessageEditingStarted(val messageId: Long, val text: String) : ChatEvent
    data class MessageActionsToggled(val messageId: Long) : ChatEvent
    data object NewConversationNeedsSave : ChatEvent
    data object ConversationCleared : ChatEvent
    data object PdfImportStarted : ChatEvent
    data class PdfImportSucceeded(val title: String, val text: String) : ChatEvent
    data class PdfImportFailed(val message: String) : ChatEvent
    data object CollectionIndexingStarted : ChatEvent
    data class CollectionCreated(val collectionId: Long, val returnToChat: Boolean = true) : ChatEvent
    data class CollectionIndexFailed(val message: String) : ChatEvent
    data class CollectionDeleted(val collectionId: Long) : ChatEvent
    data class CollectionDraftLoaded(val collectionId: Long, val title: String, val text: String) : ChatEvent
    data object CollectionEditCancelled : ChatEvent
    data object SaveDialogRequested : ChatEvent
    data object SaveStarted : ChatEvent
    data class ConversationSaved(val id: Long, val title: String, val message: String) : ChatEvent
    data class ConversationSaveFailed(val message: String) : ChatEvent
    data class ConversationAutosaved(val id: Long, val title: String) : ChatEvent
    data class ConversationAutosaveFailed(val message: String) : ChatEvent
    data class SavedConversationDeleted(val id: Long) : ChatEvent
    data class OpenSavedConversationPending(val id: Long) : ChatEvent
    data class SavedConversationLoaded(
        val id: Long,
        val title: String,
        val collectionId: Long?,
        val messages: List<ChatMessage>,
    ) : ChatEvent
}
