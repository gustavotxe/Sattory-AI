package org.sattory.app.presentation

import org.sattory.app.model.ModelStatus
import org.sattory.app.rag.domain.RagIndexState

sealed interface ChatIntentReduction {
    data class StateUpdated(val state: ChatState) : ChatIntentReduction
    data object EffectRequired : ChatIntentReduction
}

object ChatReducer {
    fun reduce(state: ChatState, intent: ChatIntent): ChatIntentReduction = when (intent) {
        is ChatIntent.InputChanged -> ChatIntentReduction.StateUpdated(state.copy(input = intent.value))
        is ChatIntent.DestinationSelected -> ChatIntentReduction.StateUpdated(
            state.copy(selectedDestination = intent.destination),
        )
        is ChatIntent.ThemeSelected -> ChatIntentReduction.StateUpdated(
            state.copy(themeMode = intent.themeMode),
        )
        is ChatIntent.SuggestionSelected -> ChatIntentReduction.StateUpdated(
            state.copy(input = intent.text),
        )
        is ChatIntent.CollectionSelected -> ChatIntentReduction.StateUpdated(
            state.copy(
                selectedCollectionId = intent.collectionId,
                currentConversationSaved = if (state.messages.isEmpty()) state.currentConversationSaved else false,
            ),
        )
        is ChatIntent.CollectionTitleChanged -> ChatIntentReduction.StateUpdated(
            if (!state.isCollectionBusy) state.copy(collectionTitleInput = intent.value) else state,
        )
        is ChatIntent.CollectionTextChanged -> ChatIntentReduction.StateUpdated(if (!state.isCollectionBusy) state.copy(
            collectionTextInput = intent.value,
            pdfImportError = null,
        ) else state)
        ChatIntent.ToggleDesktopNavigation -> ChatIntentReduction.StateUpdated(state.copy(desktopNavigationExpanded = !state.desktopNavigationExpanded))
        ChatIntent.DismissSaveConversation -> ChatIntentReduction.StateUpdated(state.copy(showSaveConversationDialog = false, saveConversationMessage = null))
        ChatIntent.DismissOpenSavedConversation -> ChatIntentReduction.StateUpdated(state.copy(showOpenSavedConversationDialog = false, pendingSavedConversationId = null))
        ChatIntent.DismissNewConversation -> ChatIntentReduction.StateUpdated(state.copy(showNewConversationDialog = false, saveConversationMessage = null))
        ChatIntent.CancelEditing -> ChatIntentReduction.StateUpdated(state.copy(input = "", editingMessageId = null, selectedMessageId = null))
        else -> ChatIntentReduction.EffectRequired
    }

    fun reduce(state: ChatState, event: ChatEvent): ChatState = when (event) {
        is ChatEvent.EffectFailed -> when (event.intent) {
            is ChatIntent.PdfSelected -> state.copy(isImportingPdf = false, pdfImportError = event.message)
            is ChatIntent.CreateCollection, ChatIntent.SaveCollection,
            is ChatIntent.DeleteCollection, is ChatIntent.EditCollection -> state.copy(ragIndexState = RagIndexState.Failed(event.message))
            ChatIntent.RetryModelDownload -> state.copy(modelStatus = ModelStatus.Failed(event.message))
            ChatIntent.RetryEmbeddingModelDownload -> state.copy(embeddingModelStatus = ModelStatus.Failed(event.message))
            ChatIntent.ConfirmSaveConversation, ChatIntent.SaveAndOpenSavedConversation,
            ChatIntent.SaveAndNewConversation -> state.copy(isSavingConversation = false, saveConversationMessage = event.message)
            is ChatIntent.DeleteSavedConversation, is ChatIntent.OpenSavedConversation,
            ChatIntent.ConfirmOpenSavedConversation -> state.copy(isSavingConversation = false, saveConversationMessage = event.message)
            ChatIntent.NewConversation, ChatIntent.ConfirmNewConversation -> state.copy(saveConversationMessage = event.message)
            else -> state
        }
        is ChatEvent.ModelStatusChanged -> state.copy(modelStatus = event.status)
        is ChatEvent.EmbeddingModelStatusChanged -> state.copy(embeddingModelStatus = event.status)
        is ChatEvent.CollectionsObserved -> state.copy(
            collections = event.collections,
            selectedCollectionId = state.selectedCollectionId?.takeIf { id -> event.collections.any { it.id == id } },
        )
        is ChatEvent.SavedConversationsObserved -> state.copy(savedConversations = event.conversations)
        is ChatEvent.GenerationStarted -> state.copy(
            input = "",
            messages = event.editingIndex?.let { index -> state.messages.take(index) + event.userMessage }
                ?: (state.messages + event.userMessage),
            isGenerating = true,
            isAwaitingFirstToken = true,
            editingMessageId = null,
            selectedMessageId = null,
            currentConversationSaved = false,
            canContinueLastResponse = false,
        )
        is ChatEvent.SourcesRetrieved -> state.copy(activeSources = event.sources)
        is ChatEvent.AssistantTokenReceived -> state.copy(
            messages = if (event.isFirst) state.messages + event.message
            else state.messages.map { if (it.id == event.message.id) event.message else it },
            isAwaitingFirstToken = if (event.isFirst) false else state.isAwaitingFirstToken,
        )
        is ChatEvent.AssistantMessageReplaced -> state.copy(
            messages = if (event.alreadyPresent) state.messages.map { if (it.id == event.message.id) event.message else it }
            else state.messages + event.message,
        )
        is ChatEvent.AssistantAnswerFormatted -> state.copy(messages = state.messages.map {
            if (it.id == event.messageId) it.copy(text = event.text) else it
        })
        is ChatEvent.GenerationFinished -> state.copy(
            isGenerating = false,
            isAwaitingFirstToken = false,
            canContinueLastResponse = event.canContinue,
        )
        is ChatEvent.MessageEditingStarted -> state.copy(input = event.text, editingMessageId = event.messageId, selectedMessageId = null)
        is ChatEvent.MessageActionsToggled -> state.copy(selectedMessageId = if (state.selectedMessageId == event.messageId) null else event.messageId)
        ChatEvent.NewConversationNeedsSave -> state.copy(showNewConversationDialog = true, saveConversationMessage = null)
        ChatEvent.ConversationCleared -> state.copy(
            input = "",
            messages = emptyList(),
            selectedDestination = ChatDestination.Chat,
            isGenerating = false,
            isAwaitingFirstToken = false,
            editingMessageId = null,
            selectedMessageId = null,
            activeSources = emptyList(),
            showSaveConversationDialog = false,
            showOpenSavedConversationDialog = false,
            showNewConversationDialog = false,
            pendingSavedConversationId = null,
            isSavingConversation = false,
            currentSavedConversationId = null,
            currentConversationTitle = null,
            currentConversationSaved = false,
            saveConversationMessage = null,
            canContinueLastResponse = false,
        )
        ChatEvent.PdfImportStarted -> state.copy(isImportingPdf = true, pdfImportError = null)
        is ChatEvent.PdfImportSucceeded -> state.copy(
            collectionTextInput = event.text,
            collectionTitleInput = state.collectionTitleInput.ifBlank { event.title },
            isImportingPdf = false,
        )
        is ChatEvent.PdfImportFailed -> state.copy(isImportingPdf = false, pdfImportError = event.message)
        ChatEvent.CollectionIndexingStarted -> state.copy(ragIndexState = RagIndexState.Indexing)
        is ChatEvent.CollectionCreated -> state.copy(
            selectedCollectionId = event.collectionId,
            collectionTitleInput = "",
            collectionTextInput = "",
            editingCollectionId = null,
            ragIndexState = RagIndexState.Ready,
            selectedDestination = if (event.returnToChat) ChatDestination.Chat else state.selectedDestination,
        )
        is ChatEvent.CollectionIndexFailed -> state.copy(ragIndexState = RagIndexState.Failed(event.message))
        is ChatEvent.CollectionDeleted -> state.copy(
            selectedCollectionId = state.selectedCollectionId.takeUnless { it == event.collectionId },
            editingCollectionId = state.editingCollectionId.takeUnless { it == event.collectionId },
        )
        is ChatEvent.CollectionDraftLoaded -> state.copy(
            selectedDestination = ChatDestination.FileProfiles,
            editingCollectionId = event.collectionId,
            collectionTitleInput = event.title,
            collectionTextInput = event.text,
            ragIndexState = RagIndexState.Idle,
            pdfImportError = null,
        )
        ChatEvent.CollectionEditCancelled -> state.copy(
            collectionTitleInput = "", collectionTextInput = "", editingCollectionId = null,
            ragIndexState = RagIndexState.Idle, pdfImportError = null,
        )
        ChatEvent.SaveDialogRequested -> state.copy(showSaveConversationDialog = true, saveConversationMessage = null)
        ChatEvent.SaveStarted -> state.copy(isSavingConversation = true)
        is ChatEvent.ConversationSaved -> state.copy(
            showSaveConversationDialog = false,
            showNewConversationDialog = false,
            isSavingConversation = false,
            currentSavedConversationId = event.id,
            currentConversationTitle = event.title,
            currentConversationSaved = true,
            saveConversationMessage = event.message,
        )
        is ChatEvent.ConversationSaveFailed -> state.copy(isSavingConversation = false, saveConversationMessage = event.message)
        is ChatEvent.ConversationAutosaved -> state.copy(
            currentSavedConversationId = event.id, currentConversationTitle = event.title,
            currentConversationSaved = true, saveConversationMessage = null,
        )
        is ChatEvent.ConversationAutosaveFailed -> state.copy(currentConversationSaved = false, saveConversationMessage = event.message)
        is ChatEvent.SavedConversationDeleted -> if (state.currentSavedConversationId == event.id) state.copy(
            currentSavedConversationId = null, currentConversationTitle = null, currentConversationSaved = false,
        ) else state
        is ChatEvent.OpenSavedConversationPending -> state.copy(
            pendingSavedConversationId = event.id, showOpenSavedConversationDialog = true, saveConversationMessage = null,
        )
        is ChatEvent.SavedConversationLoaded -> state.copy(
            selectedDestination = ChatDestination.Chat,
            messages = event.messages,
            input = "", isGenerating = false, isAwaitingFirstToken = false,
            editingMessageId = null, selectedMessageId = null, activeSources = emptyList(),
            selectedCollectionId = event.collectionId?.takeIf { id -> state.collections.any { it.id == id } },
            showOpenSavedConversationDialog = false, showNewConversationDialog = false,
            pendingSavedConversationId = null, currentSavedConversationId = event.id,
            currentConversationTitle = event.title, currentConversationSaved = true,
            saveConversationMessage = null,
        )
    }
}
