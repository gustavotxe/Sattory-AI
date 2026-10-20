package org.sattory.app.presentation

import kotlinx.coroutines.CoroutineScope
import org.sattory.app.conversation.domain.DeleteSavedConversationUseCase
import org.sattory.app.conversation.domain.GetSavedConversationUseCase
import org.sattory.app.conversation.domain.ObserveSavedConversationsUseCase
import org.sattory.app.conversation.domain.SaveConversationUseCase
import org.sattory.app.llm.LocalLlm
import org.sattory.app.model.EmbeddingModelPreparationUseCase
import org.sattory.app.model.ModelPreparationUseCase
import org.sattory.app.rag.domain.CreateCollectionUseCase
import org.sattory.app.rag.domain.DeleteCollectionUseCase
import org.sattory.app.rag.domain.GetCollectionDraftUseCase
import org.sattory.app.rag.domain.ImportPdfUseCase
import org.sattory.app.rag.domain.ObserveCollectionsUseCase
import org.sattory.app.rag.domain.RagPromptBuilder
import org.sattory.app.rag.domain.RetrieveContextUseCase
import org.sattory.app.rag.domain.UpdateCollectionUseCase

class ChatStoreFactory(
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
    private val importPdfUseCase: ImportPdfUseCase,
) {
    fun create(scope: CoroutineScope) = ChatStore(
        localLlm = localLlm,
        modelPreparationUseCase = modelPreparationUseCase,
        embeddingModelPreparationUseCase = embeddingModelPreparationUseCase,
        observeCollectionsUseCase = observeCollectionsUseCase,
        createCollectionUseCase = createCollectionUseCase,
        getCollectionDraftUseCase = getCollectionDraftUseCase,
        updateCollectionUseCase = updateCollectionUseCase,
        deleteCollectionUseCase = deleteCollectionUseCase,
        retrieveContextUseCase = retrieveContextUseCase,
        ragPromptBuilder = ragPromptBuilder,
        observeSavedConversationsUseCase = observeSavedConversationsUseCase,
        saveConversationUseCase = saveConversationUseCase,
        getSavedConversationUseCase = getSavedConversationUseCase,
        deleteSavedConversationUseCase = deleteSavedConversationUseCase,
        importPdfUseCase = importPdfUseCase,
        parentScope = scope,
    )
}
