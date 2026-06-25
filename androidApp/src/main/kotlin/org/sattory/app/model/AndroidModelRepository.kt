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

class AndroidModelRepository(context: Context) : ModelRepository {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    override fun observeDownloadState(): Flow<ModelDownloadState> {
        return workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_WORK_NAME)
            .map { workInfos -> workInfos.lastOrNull().toModelDownloadState() }
            .distinctUntilChanged()
    }

    override fun requestDownload(policy: ModelDownloadRequestPolicy) {
        enqueue(policy.toExistingWorkPolicy())
    }

    override suspend fun isModelAvailable(): Boolean = withContext(Dispatchers.IO) {
        ModelFileSpec.file(appContext).isFile
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ModelDownloadWorker.UNIQUE_WORK_NAME, policy, request)
    }

    private fun ModelDownloadRequestPolicy.toExistingWorkPolicy(): ExistingWorkPolicy = when (this) {
        ModelDownloadRequestPolicy.Keep -> ExistingWorkPolicy.KEEP
        ModelDownloadRequestPolicy.Replace -> ExistingWorkPolicy.REPLACE
    }

    private fun WorkInfo?.toModelDownloadState(): ModelDownloadState {
        return when (this?.state) {
            null -> ModelDownloadState.NotScheduled
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelDownloadState.Queued
            WorkInfo.State.RUNNING -> ModelDownloadState.Running(progress.toModelDownloadProgress())
            WorkInfo.State.SUCCEEDED -> ModelDownloadState.Succeeded
            WorkInfo.State.FAILED -> ModelDownloadState.Failed(
                outputData.getString(ModelDownloadWorker.KEY_ERROR)
                    ?: "Nao foi possivel preparar o modelo local",
            )
            WorkInfo.State.CANCELLED -> ModelDownloadState.Cancelled
        }
    }

    private fun androidx.work.Data.toModelDownloadProgress(): ModelDownloadProgress {
        return when (getString(ModelDownloadWorker.KEY_STAGE)) {
            ModelDownloadWorker.STAGE_DOWNLOADING -> ModelDownloadProgress(
                stage = ModelDownloadStage.Downloading,
                downloadedBytes = getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L),
                totalBytes = getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, -1L).takeIf { it > 0 },
            )
            ModelDownloadWorker.STAGE_VERIFYING -> ModelDownloadProgress(ModelDownloadStage.Verifying)
            ModelDownloadWorker.STAGE_READY -> ModelDownloadProgress(ModelDownloadStage.Ready)
            else -> ModelDownloadProgress(ModelDownloadStage.Unknown)
        }
    }
}
