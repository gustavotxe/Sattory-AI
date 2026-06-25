package org.sattory.app.presentation.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.sattory.app.llm.GenerationOptions
import org.sattory.app.llm.LocalLlm
import org.sattory.app.conversation.data.UnavailableSavedConversationRepository
import org.sattory.app.conversation.domain.DeleteSavedConversationUseCase
import org.sattory.app.conversation.domain.GetSavedConversationUseCase
import org.sattory.app.conversation.domain.ObserveSavedConversationsUseCase
import org.sattory.app.conversation.domain.SaveConversationUseCase
import org.sattory.app.model.EmbeddingModelPreparationUseCase
import org.sattory.app.model.ModelPreparationUseCase
import org.sattory.app.model.ReadyModelRepository
import org.sattory.app.rag.data.UnavailableRagRepository
import org.sattory.app.rag.domain.CreateCollectionUseCase
import org.sattory.app.rag.domain.DeleteCollectionUseCase
import org.sattory.app.rag.domain.GetCollectionDraftUseCase
import org.sattory.app.rag.domain.ObserveCollectionsUseCase
import org.sattory.app.rag.domain.RagPromptBuilder
import org.sattory.app.rag.domain.RetrieveContextUseCase
import org.sattory.app.rag.domain.UpdateCollectionUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun combinesStreamedTokensIntoOneAssistantMessageAndResetsContext() = runTest(dispatcher) {
        val llm = FakeLocalLlm()
        val viewModel = createViewModel(llm)
        advanceUntilIdle()

        viewModel.onIntent(ChatIntent.InputChanged("Ola"))
        viewModel.onIntent(ChatIntent.SendMessage)
        advanceUntilIdle()

        var currentState = viewModel.state.first()
        assertEquals(listOf("Ola", "Resposta em streaming"), currentState.messages.map { it.text })
        assertFalse(currentState.isGenerating)
        assertFalse(currentState.isAwaitingFirstToken)

        val originalUserId = currentState.messages.first().id
        viewModel.onIntent(ChatIntent.ToggleMessageActions(originalUserId))
        advanceUntilIdle()
        currentState = viewModel.state.first()
        assertEquals(originalUserId, currentState.selectedMessageId)

        viewModel.onIntent(ChatIntent.EditMessage(originalUserId))
        advanceUntilIdle()
        currentState = viewModel.state.first()
        assertNull(currentState.selectedMessageId)

        viewModel.onIntent(ChatIntent.InputChanged("Ola, editado"))
        viewModel.onIntent(ChatIntent.SendMessage)
        advanceUntilIdle()

        currentState = viewModel.state.first()
        assertEquals(listOf("Ola, editado", "Resposta em streaming"), currentState.messages.map { it.text })
        assertEquals(listOf(0), llm.truncatedAt)

        viewModel.onIntent(ChatIntent.NewConversation)
        advanceUntilIdle()
        currentState = viewModel.state.first()
        assertTrue(currentState.showNewConversationDialog)
        assertEquals(0, llm.resetCount)

        viewModel.onIntent(ChatIntent.ConfirmNewConversation)
        advanceUntilIdle()
        currentState = viewModel.state.first()
        assertEquals(1, llm.resetCount)
        assertEquals(emptyList(), currentState.messages)
    }

    private fun createViewModel(llm: LocalLlm): ChatViewModel {
        val ragRepository = UnavailableRagRepository()
        val conversationRepository = UnavailableSavedConversationRepository()
        return ChatViewModel(
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
        )
    }

    private class FakeLocalLlm : LocalLlm {
        var resetCount = 0
        val truncatedAt = mutableListOf<Int>()

        override fun generate(prompt: String, options: GenerationOptions): Flow<String> =
            flowOf("Resposta", " em", " streaming")

        override suspend fun resetConversation() {
            resetCount++
        }

        override suspend fun truncateConversation(messageCount: Int) {
            truncatedAt += messageCount
        }

        override fun close() = Unit
    }
}
