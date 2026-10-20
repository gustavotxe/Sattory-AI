package org.sattory.app.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

class ModelPreparationUseCase(
    private val modelRepository: ModelRepository,
) {
    private val retryVersion = MutableStateFlow(0L)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeStatus(): Flow<ModelStatus> =
        retryVersion.flatMapLatest { version ->
            prepareStatus(if (version == 0L) ModelDownloadRequestPolicy.Keep else ModelDownloadRequestPolicy.Replace)
        }.distinctUntilChanged()

    private fun prepareStatus(policy: ModelDownloadRequestPolicy): Flow<ModelStatus> =
        flow {
            emit(ModelStatus.Verifying)
            if (modelRepository.isModelAvailable()) {
                emit(ModelStatus.Ready)
                return@flow
            }
            modelRepository.requestDownload(policy)
            modelRepository.observeDownloadState().collect { downloadState ->
                emit(downloadState.toModelStatus())
            }
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(ModelStatus.Failed(error.message ?: "Nao foi possivel verificar o modelo local"))
        }.distinctUntilChanged()

    fun retryDownload() {
        retryVersion.update { it + 1 }
    }

    private suspend fun ModelDownloadState.toModelStatus(): ModelStatus = when (this) {
        ModelDownloadState.NotScheduled -> ModelStatus.Checking
        ModelDownloadState.Queued -> ModelStatus.Queued
        is ModelDownloadState.Running -> progress.toModelStatus()
        ModelDownloadState.Succeeded -> {
            if (modelRepository.isModelAvailable()) {
                ModelStatus.Ready
            } else {
                modelRepository.requestDownload(ModelDownloadRequestPolicy.Replace)
                ModelStatus.Queued
            }
        }
        is ModelDownloadState.Failed -> ModelStatus.Failed(message)
        ModelDownloadState.Cancelled -> ModelStatus.Queued
    }

    private fun ModelDownloadProgress.toModelStatus(): ModelStatus = when (stage) {
        ModelDownloadStage.Downloading -> ModelStatus.Downloading(
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
        )
        ModelDownloadStage.Verifying -> ModelStatus.Verifying
        ModelDownloadStage.Ready -> ModelStatus.Ready
        ModelDownloadStage.Unknown -> ModelStatus.Checking
    }
}
