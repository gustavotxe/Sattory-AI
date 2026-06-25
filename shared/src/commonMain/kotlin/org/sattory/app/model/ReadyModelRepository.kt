package org.sattory.app.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ReadyModelRepository : ModelRepository {
    override fun observeDownloadState(): Flow<ModelDownloadState> = flowOf(ModelDownloadState.Succeeded)
    override fun requestDownload(policy: ModelDownloadRequestPolicy) = Unit
    override suspend fun isModelAvailable(): Boolean = true
}
