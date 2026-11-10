package org.sattory.app.presentation

import kotlin.test.AfterTest
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.sattory.app.conversation.data.UnavailableSavedConversationRepository
import org.sattory.app.conversation.domain.DeleteSavedConversationUseCase
import org.sattory.app.conversation.domain.GetSavedConversationUseCase
import org.sattory.app.conversation.domain.ObserveSavedConversationsUseCase
import org.sattory.app.conversation.domain.SaveConversationUseCase
import org.sattory.app.llm.GenerationOptions
import org.sattory.app.llm.LocalLlm
import org.sattory.app.model.EmbeddingModelPreparationUseCase
import org.sattory.app.model.ModelPreparationUseCase
import org.sattory.app.model.ReadyModelRepository
import org.sattory.app.rag.UnavailableRagRepository
import org.sattory.app.rag.domain.CreateCollectionUseCase
import org.sattory.app.rag.domain.DeleteCollectionUseCase
import org.sattory.app.rag.domain.DocumentCollection
import org.sattory.app.rag.domain.GetCollectionDraftUseCase
import org.sattory.app.rag.domain.ImportPdfUseCase
import org.sattory.app.rag.domain.ObserveCollectionsUseCase
import org.sattory.app.rag.domain.PdfDocument
import org.sattory.app.rag.domain.PdfRepository
import org.sattory.app.rag.domain.RagPromptBuilder
import org.sattory.app.rag.domain.RagRepository
import org.sattory.app.rag.domain.RagSource
import org.sattory.app.rag.domain.RetrieveContextUseCase
import org.sattory.app.rag.domain.UpdateCollectionUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class ChatStoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ChatStore>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        stores.forEach(ChatStore::close)
        stores.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun combinesStreamedTokensIntoOneAssistantMessageAndResetsContext() = runTest(dispatcher) {
        val llm = FakeLocalLlm()
        val store = createStore(llm)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Ola"))
        store.dispatch(ChatIntent.SendMessage)
        advanceUntilIdle()

        var currentState = store.state.first()
        assertEquals(listOf("Ola", "Resposta em streaming"), currentState.messages.map { it.text })
        assertFalse(currentState.isGenerating)
        assertFalse(currentState.isAwaitingFirstToken)

        val originalUserId = currentState.messages.first().id
        store.dispatch(ChatIntent.ToggleMessageActions(originalUserId))
        advanceUntilIdle()
        currentState = store.state.first()
        assertEquals(originalUserId, currentState.selectedMessageId)

        store.dispatch(ChatIntent.EditMessage(originalUserId))
        advanceUntilIdle()
        currentState = store.state.first()
        assertNull(currentState.selectedMessageId)

        store.dispatch(ChatIntent.InputChanged("Ola, editado"))
        store.dispatch(ChatIntent.SendMessage)
        advanceUntilIdle()

        currentState = store.state.first()
        assertEquals(listOf("Ola, editado", "Resposta em streaming"), currentState.messages.map { it.text })
        assertEquals(listOf(0), llm.truncatedAt)

        store.dispatch(ChatIntent.NewConversation)
        advanceUntilIdle()
        currentState = store.state.first()
        assertTrue(currentState.showNewConversationDialog)
        assertEquals(0, llm.resetCount)

        store.dispatch(ChatIntent.ConfirmNewConversation)
        advanceUntilIdle()
        currentState = store.state.first()
        assertEquals(1, llm.resetCount)
        assertEquals(emptyList(), currentState.messages)
    }

    @Test
    fun usesGroundedGenerationOptionsWhenRagSourcesAreAvailable() = runTest(dispatcher) {
        val llm = FakeLocalLlm()
        val source = RagSource(
            documentTitle = "Inventario",
            text = "A caixa azul fica ao lado da caixa verde. A etiqueta da caixa azul informa prioridade alta.",
            score = 0.82f,
        )
        val store = createStore(llm, FixedRagRepository(listOf(source)))
        advanceUntilIdle()

        store.dispatch(ChatIntent.CollectionSelected(1L))
        store.dispatch(ChatIntent.InputChanged("Qual e a prioridade da caixa verde?"))
        store.dispatch(ChatIntent.SendMessage)
        advanceUntilIdle()

        assertEquals(GenerationOptions.GroundedAnswering, llm.options.single())
        assertContains(llm.prompts.single(), "claramente apoiada")
        assertContains(llm.prompts.single(), "O material enviado nao informa isso.")
        assertContains(llm.prompts.single(), "Nao atribua")
        assertEquals(listOf(source), store.state.first().activeSources)
    }

    @Test
    fun documentaryQueryWithoutEvidenceDoesNotGenerateAnUngroundedAnswer() = runTest(dispatcher) {
        val llm = FakeLocalLlm()
        val store = createStore(llm, FixedRagRepository(emptyList()))
        advanceUntilIdle()
        store.dispatch(ChatIntent.CollectionSelected(1L))
        store.dispatch(ChatIntent.InputChanged("Qual era a matricula do carro de Cila?"))
        store.dispatch(ChatIntent.SendMessage)
        advanceUntilIdle()
        assertTrue(llm.prompts.isEmpty())
        assertEquals("O material enviado nao informa isso.", store.state.value.messages.last().text)
        assertFalse(store.state.value.canContinueLastResponse)
    }

    @Test
    fun removesContradictoryRefusalFromCompletedRagAnswer() = runTest(dispatcher) {
        val llm = FakeLocalLlm {
            flowOf("Cila era um monstro de seis cabecas.", " O material enviado nao informa isso.")
        }
        val source = RagSource("Odisseia", "Cila era um monstro de seis cabecas.", 0.9f)
        val store = createStore(llm, FixedRagRepository(listOf(source)))
        advanceUntilIdle()
        store.dispatch(ChatIntent.CollectionSelected(1L))
        store.dispatch(ChatIntent.InputChanged("Quem era Cila?"))
        store.dispatch(ChatIntent.SendMessage)
        advanceUntilIdle()
        assertEquals("Cila era um monstro de seis cabecas.", store.state.value.messages.last().text)
    }

    @Test
    fun offersContinueWhenGenerationReachesTokenBudget() = runTest(dispatcher) {
        val llm = FakeLocalLlm(tokenLimited = true) { options ->
            flow {
                repeat(options.maxTokens) { emit("x") }
            }
        }
        val store = createStore(llm)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Escreva um texto longo"))
        store.dispatch(ChatIntent.SendMessage)
        advanceUntilIdle()

        assertTrue(store.state.first().canContinueLastResponse)

        store.dispatch(ChatIntent.ContinueLastResponse)
        advanceUntilIdle()

        assertEquals("continue", llm.prompts.last())
        assertEquals("continue", store.state.first().messages.dropLast(1).last().text)
    }

    @Test
    fun doesNotOfferContinueWhenGenerationStopsBeforeTokenBudget() = runTest(dispatcher) {
        val llm = FakeLocalLlm()
        val store = createStore(llm)
        advanceUntilIdle()

        store.dispatch(ChatIntent.InputChanged("Ola"))
        store.dispatch(ChatIntent.SendMessage)
        advanceUntilIdle()

        assertFalse(store.state.first().canContinueLastResponse)
    }

    @Test
    fun ragPromptKeepsEntityBoundariesExplicit() {
        val prompt = RagPromptBuilder().build(
            userPrompt = "Qual e a prioridade da caixa verde?",
            sources = listOf(
                RagSource(
                    documentTitle = "Inventario",
                    text = "A caixa azul fica ao lado da caixa verde. A etiqueta da caixa azul informa prioridade alta.",
                    score = 0.82f,
                ),
            ),
        )

        assertContains(prompt, "Distingua quem viveu um episodio de quem ouviu o relato")
        assertContains(prompt, "combinar frases proximas")
        assertContains(prompt, "Nao atribua")
        assertContains(prompt, "O material enviado nao informa isso.")
    }

    @Test
    fun pdfImportIgnoresDuplicateSelectionAndConcurrentDraftChanges() = runTest {
        val result = kotlinx.coroutines.CompletableDeferred<PdfDocument>()
        var calls = 0
        val importer = ImportPdfUseCase(object : PdfRepository {
            override suspend fun extract(reference: String): PdfDocument {
                calls++
                return result.await()
            }
        })
        val store = createStore(FakeLocalLlm(), importPdfUseCase = importer)
        store.dispatch(ChatIntent.PdfSelected("first"))
        store.dispatch(ChatIntent.PdfSelected("second"))
        store.dispatch(ChatIntent.CollectionTextChanged("concurrent edit"))
        store.dispatch(ChatIntent.CancelCollectionEditing)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(store.state.value.isImportingPdf)
        result.complete(PdfDocument("Imported", "a".repeat(100)))
        advanceUntilIdle()
        assertEquals("a".repeat(100), store.state.value.collectionTextInput)
        assertFalse(store.state.value.isImportingPdf)
    }

    private fun createStore(
        llm: LocalLlm,
        ragRepository: RagRepository = UnavailableRagRepository(),
        importPdfUseCase: ImportPdfUseCase? = null,
        parentScope: CoroutineScope = CoroutineScope(dispatcher),
    ): ChatStore {
        val conversationRepository = UnavailableSavedConversationRepository()
        return ChatStore(
            localLlm = llm,
            modelPreparationUseCase = ModelPreparationUseCase(ReadyModelRepository()),
            embeddingModelPreparationUseCase = EmbeddingModelPreparationUseCase(ReadyModelRepository()),
            observeCollectionsUseCase = ObserveCollectionsUseCase(ragRepository),
            createCollectionUseCase = CreateCollectionUseCase(ragRepository),
            getCollectionDraftUseCase = GetCollectionDraftUseCase(ragRepository),
            updateCollectionUseCase = UpdateCollectionUseCase(ragRepository),
            deleteCollectionUseCase = DeleteCollectionUseCase(ragRepository),
            retrieveContextUseCase = RetrieveContextUseCase(ragRepository),
            ragPromptBuilder = RagPromptBuilder(),
            observeSavedConversationsUseCase = ObserveSavedConversationsUseCase(conversationRepository),
            saveConversationUseCase = SaveConversationUseCase(conversationRepository),
            getSavedConversationUseCase = GetSavedConversationUseCase(conversationRepository),
            deleteSavedConversationUseCase = DeleteSavedConversationUseCase(conversationRepository),
            importPdfUseCase = importPdfUseCase,
            parentScope = parentScope,
        ).also(stores::add)
    }

    private class FakeLocalLlm(
        private val tokenLimited: Boolean = false,
        private val responseFactory: (GenerationOptions) -> Flow<String> = {
            flowOf("Resposta", " em", " streaming")
        },
    ) : LocalLlm {
        var resetCount = 0
        val truncatedAt = mutableListOf<Int>()
        val prompts = mutableListOf<String>()
        val options = mutableListOf<GenerationOptions>()

        override suspend fun countPromptTokens(prompt: String): Int = prompt.encodeToByteArray().size

        override fun generate(prompt: String, options: GenerationOptions): Flow<String> {
            prompts += prompt
            this.options += options
            return responseFactory(options)
        }

        override suspend fun wasLastGenerationTokenLimited(): Boolean = tokenLimited

        override suspend fun resetConversation() {
            resetCount++
        }

        override suspend fun truncateConversation(messageCount: Int) {
            truncatedAt += messageCount
        }

        override fun close() = Unit
    }

    private class FixedRagRepository(private val sources: List<RagSource>) : RagRepository {
        override fun observeCollections(): Flow<List<DocumentCollection>> = flowOf(
            listOf(
                DocumentCollection(
                    id = 1L,
                    name = "Teste",
                    description = "",
                    documentCount = 1,
                    chunkCount = sources.size,
                    createdAtMillis = 0L,
                    updatedAtMillis = 0L,
                ),
            ),
        )

        override suspend fun createCollection(input: org.sattory.app.rag.domain.CreateCollectionInput): Long = 1L
        override suspend fun collectionDraft(collectionId: Long): org.sattory.app.rag.domain.CollectionDraft? = null
        override suspend fun updateCollection(
            collectionId: Long,
            input: org.sattory.app.rag.domain.CreateCollectionInput,
        ) = Unit

        override suspend fun deleteCollection(collectionId: Long) = Unit

        override suspend fun retrieve(collectionId: Long, query: String, limit: Int): List<RagSource> =
            sources.take(limit)
    }
}
