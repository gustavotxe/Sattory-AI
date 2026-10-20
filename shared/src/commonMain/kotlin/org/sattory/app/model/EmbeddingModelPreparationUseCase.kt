package org.sattory.app.model

import kotlinx.coroutines.flow.Flow

class EmbeddingModelPreparationUseCase(modelRepository: ModelRepository) {
    private val preparation = ModelPreparationUseCase(modelRepository)

    fun observeStatus(): Flow<ModelStatus> = preparation.observeStatus()

    fun retryDownload() = preparation.retryDownload()
}
