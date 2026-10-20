package org.sattory.app.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.sattory.app.rag.domain.CreateCollectionInput
import org.sattory.app.rag.domain.CreateCollectionUseCase
import org.sattory.app.rag.domain.DeleteCollectionUseCase
import org.sattory.app.rag.domain.GetCollectionDraftUseCase
import org.sattory.app.rag.domain.ImportPdfUseCase
import org.sattory.app.rag.domain.RagIndexState
import org.sattory.app.rag.domain.UpdateCollectionUseCase

internal class ChatCollectionEffects(
    private val scope: CoroutineScope,
    private val state: StateFlow<org.sattory.app.presentation.ChatState>,
    private val importPdfUseCase: ImportPdfUseCase?,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val getCollectionDraftUseCase: GetCollectionDraftUseCase,
    private val updateCollectionUseCase: UpdateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val emit: (ChatEvent) -> Unit,
    private val apply: (ChatEvent) -> Unit,
) {
    fun importPdf(reference: String) {
        val importer = importPdfUseCase ?: return
        if (state.value.isImportingPdf || state.value.ragIndexState == RagIndexState.Indexing) return
        apply(ChatEvent.PdfImportStarted)
        scope.launch {
            try {
                val document = importer(reference)
                emit(ChatEvent.PdfImportSucceeded(document.title, document.text))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(ChatEvent.PdfImportFailed(error.message ?: "Nao foi possivel importar o PDF."))
            }
        }
    }

    fun createCollection() {
        val current = state.value
        if (current.ragIndexState == RagIndexState.Indexing || current.isImportingPdf) return
        apply(ChatEvent.CollectionIndexingStarted)
        scope.launch {
            try {
                val id = createCollectionUseCase(toCreateInput(current))
                emit(ChatEvent.CollectionCreated(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(ChatEvent.CollectionIndexFailed(error.message ?: "Nao foi possivel indexar a colecao"))
            }
        }
    }

    suspend fun deleteCollection(collectionId: Long) {
        if (state.value.isImportingPdf || state.value.ragIndexState == RagIndexState.Indexing) return
        deleteCollectionUseCase(collectionId)
        emit(ChatEvent.CollectionDeleted(collectionId))
    }

    suspend fun editCollection(collectionId: Long) {
        if (state.value.isImportingPdf || state.value.ragIndexState == RagIndexState.Indexing) return
        val draft = getCollectionDraftUseCase(collectionId) ?: return
        emit(ChatEvent.CollectionDraftLoaded(draft.id, draft.title, draft.text))
    }

    fun saveCollection() {
        val current = state.value
        val editingId = current.editingCollectionId ?: return
        if (current.ragIndexState == RagIndexState.Indexing || current.isImportingPdf) return
        apply(ChatEvent.CollectionIndexingStarted)
        scope.launch {
            try {
                updateCollectionUseCase(editingId, toCreateInput(current))
                emit(ChatEvent.CollectionCreated(editingId, returnToChat = false))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emit(ChatEvent.CollectionIndexFailed(error.message ?: "Nao foi possivel salvar a colecao"))
            }
        }
    }

    fun cancelCollectionEditing() {
        if (state.value.isImportingPdf || state.value.ragIndexState == RagIndexState.Indexing) return
        apply(ChatEvent.CollectionEditCancelled)
    }

    private fun toCreateInput(state: ChatState) = CreateCollectionInput(
        name = state.collectionTitleInput,
        text = state.collectionTextInput,
    )
}
