package org.sattory.app.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ModelPreparationUseCaseTest {
    @Test
    fun localModelsAreReadyWithoutObservingNetworkWorkOrRequestingDownloads() = runTest {
        for (oldState in listOf(
            ModelDownloadState.NotScheduled, ModelDownloadState.Queued,
            ModelDownloadState.Failed("Sem internet"), ModelDownloadState.Cancelled,
        )) {
            for (embedding in listOf(false, true)) {
                val repository = FakeRepository(available = true, initialState = oldState)
                val status = if (embedding) EmbeddingModelPreparationUseCase(repository).observeStatus()
                    else ModelPreparationUseCase(repository).observeStatus()
                assertEquals(ModelStatus.Ready, status.first { it == ModelStatus.Ready })
                assertTrue(repository.requests.isEmpty())
                assertEquals(0, repository.observations)
            }
        }
    }

    @Test
    fun unavailableModelDownloadsAndBecomesReadyOnlyAfterLocalValidation() = runTest {
        val repository = FakeRepository(false, ModelDownloadState.Queued)
        val preparation = ModelPreparationUseCase(repository)
        val statuses = mutableListOf<ModelStatus>()
        backgroundScope.launch { preparation.observeStatus().collect { statuses += it } }
        runCurrent()
        assertEquals(listOf(ModelDownloadRequestPolicy.Keep), repository.requests)
        assertTrue(statuses.contains(ModelStatus.Queued))
        repository.available = true
        repository.downloadState.value = ModelDownloadState.Succeeded
        runCurrent()
        assertEquals(ModelStatus.Ready, statuses.last())
    }

    @Test
    fun retryRechecksLocalModelBeforeRequestingNetwork() = runTest {
        val repository = FakeRepository(false, ModelDownloadState.Failed("Offline"))
        val preparation = ModelPreparationUseCase(repository)
        val statuses = mutableListOf<ModelStatus>()
        backgroundScope.launch { preparation.observeStatus().collect { statuses += it } }
        runCurrent()
        preparation.retryDownload()
        runCurrent()
        assertEquals(listOf(ModelDownloadRequestPolicy.Keep, ModelDownloadRequestPolicy.Replace), repository.requests)
        repository.available = true
        preparation.retryDownload()
        runCurrent()
        assertEquals(ModelStatus.Ready, statuses.last())
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun localValidationErrorIsVisibleAndRetryCanRecover() = runTest {
        val repository = FakeRepository(true, ModelDownloadState.Queued)
        repository.validationError = IllegalStateException("Falha ao ler arquivo")
        val preparation = ModelPreparationUseCase(repository)
        val statuses = mutableListOf<ModelStatus>()
        backgroundScope.launch { preparation.observeStatus().collect { statuses += it } }
        runCurrent()
        assertEquals(ModelStatus.Failed("Falha ao ler arquivo"), statuses.last())
        repository.validationError = null
        preparation.retryDownload()
        runCurrent()
        assertEquals(ModelStatus.Ready, statuses.last())
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun cancellationDoesNotBecomeADownloadFailure() = runTest {
        val repository = FakeRepository(true, ModelDownloadState.Queued)
        repository.validationError = CancellationException("Cancelled")
        val statuses = mutableListOf<ModelStatus>()
        val job = launch { ModelPreparationUseCase(repository).observeStatus().collect { statuses += it } }
        runCurrent()
        job.cancel()
        assertTrue(statuses.none { it is ModelStatus.Failed })
        assertTrue(repository.requests.isEmpty())
    }

    private class FakeRepository(var available: Boolean, initialState: ModelDownloadState) : ModelRepository {
        val downloadState = MutableStateFlow(initialState)
        val requests = mutableListOf<ModelDownloadRequestPolicy>()
        var observations = 0
        var validationError: Exception? = null
        override fun observeDownloadState(): Flow<ModelDownloadState> {
            observations++
            return downloadState
        }
        override fun requestDownload(policy: ModelDownloadRequestPolicy) { requests += policy }
        override suspend fun isModelAvailable(): Boolean {
            validationError?.let { throw it }
            return available
        }
    }
}
