package org.sattory.app.model

import kotlinx.coroutines.flow.Flow

sealed interface ModelStatus {
    data object Checking : ModelStatus
    data object Queued : ModelStatus
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : ModelStatus {
        val progress: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toDouble() / it).toFloat().coerceIn(0f, 1f) }
    }
    data object Verifying : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

enum class ModelDownloadRequestPolicy {
    Keep,
    Replace,
}

enum class ModelDownloadStage {
    Downloading,
    Verifying,
    Ready,
    Unknown,
}

data class ModelDownloadProgress(
    val stage: ModelDownloadStage,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
)

sealed interface ModelDownloadState {
    data object NotScheduled : ModelDownloadState
    data object Queued : ModelDownloadState
    data class Running(val progress: ModelDownloadProgress) : ModelDownloadState
    data object Succeeded : ModelDownloadState
    data class Failed(val message: String) : ModelDownloadState
    data object Cancelled : ModelDownloadState
}

interface ModelRepository {
    fun observeDownloadState(): Flow<ModelDownloadState>
    fun requestDownload(policy: ModelDownloadRequestPolicy)
    suspend fun isModelAvailable(): Boolean
}
