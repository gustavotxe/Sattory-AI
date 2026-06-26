package org.sattory.app.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

class ModelPreparationUseCase(
    private val modelRepository: ModelRepository,
) {
    fun observeStatus(): Flow<ModelStatus> =
        flow {
            modelRepository.observeDownloadState().collect { downloadState ->
                emit(downloadState.toModelStatus())
            }
        }.distinctUntilChanged()

    fun ensureModel() {
        modelRepository.requestDownload(ModelDownloadRequestPolicy.Keep)
    }

    fun retryDownload() {
        modelRepository.requestDownload(ModelDownloadRequestPolicy.Replace)
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
