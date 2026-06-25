package org.sattory.app.model

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AndroidEmbeddingModelRepository(context: Context) : ModelRepository {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    override fun observeDownloadState(): Flow<ModelDownloadState> {
        return workManager.getWorkInfosForUniqueWorkFlow(EmbeddingModelDownloadWorker.UNIQUE_WORK_NAME)
            .map { workInfos -> workInfos.lastOrNull().toModelDownloadState() }
            .distinctUntilChanged()
    }

    override fun requestDownload(policy: ModelDownloadRequestPolicy) {
        val request = OneTimeWorkRequestBuilder<EmbeddingModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(EmbeddingModelDownloadWorker.UNIQUE_WORK_NAME, policy.toExistingWorkPolicy(), request)
    }

    override suspend fun isModelAvailable(): Boolean = withContext(Dispatchers.IO) {
        GgufFileVerifier.isValid(EmbeddingModelFileSpec.file(appContext))
    }

    private fun ModelDownloadRequestPolicy.toExistingWorkPolicy(): ExistingWorkPolicy = when (this) {
        ModelDownloadRequestPolicy.Keep -> ExistingWorkPolicy.KEEP
        ModelDownloadRequestPolicy.Replace -> ExistingWorkPolicy.REPLACE
    }

    private fun WorkInfo?.toModelDownloadState(): ModelDownloadState = when (this?.state) {
        null -> ModelDownloadState.NotScheduled
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelDownloadState.Queued
        WorkInfo.State.RUNNING -> ModelDownloadState.Running(progress.toModelDownloadProgress())
        WorkInfo.State.SUCCEEDED -> ModelDownloadState.Succeeded
        WorkInfo.State.FAILED -> ModelDownloadState.Failed(
            outputData.getString(EmbeddingModelDownloadWorker.KEY_ERROR)
                ?: "Nao foi possivel preparar o modelo de embeddings",
        )
        WorkInfo.State.CANCELLED -> ModelDownloadState.Cancelled
    }

    private fun androidx.work.Data.toModelDownloadProgress(): ModelDownloadProgress = when (getString(EmbeddingModelDownloadWorker.KEY_STAGE)) {
        EmbeddingModelDownloadWorker.STAGE_DOWNLOADING -> ModelDownloadProgress(
            stage = ModelDownloadStage.Downloading,
            downloadedBytes = getLong(EmbeddingModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L),
            totalBytes = getLong(EmbeddingModelDownloadWorker.KEY_TOTAL_BYTES, -1L).takeIf { it > 0 },
        )
        EmbeddingModelDownloadWorker.STAGE_VERIFYING -> ModelDownloadProgress(ModelDownloadStage.Verifying)
        EmbeddingModelDownloadWorker.STAGE_READY -> ModelDownloadProgress(ModelDownloadStage.Ready)
        else -> ModelDownloadProgress(ModelDownloadStage.Unknown)
    }
}
