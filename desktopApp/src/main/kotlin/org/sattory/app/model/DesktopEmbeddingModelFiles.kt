package org.sattory.app.model

import org.sattory.app.platform.DesktopAppDirectories
import java.io.File

class DesktopEmbeddingModelFiles(
    private val directories: DesktopAppDirectories,
) {
    private val modelOverride: File? by lazy {
        System.getProperty(MODEL_PATH_PROPERTY)?.let(::File)
    }

    val modelDirectory: File by lazy {
        modelOverride?.parentFile ?: File(directories.dataRoot, MODEL_DIRECTORY_NAME)
    }

    val modelFile: File by lazy {
        modelOverride ?: File(modelDirectory, EmbeddingModelArtifactSpec.FILE_NAME)
    }

    val partialModelFile: File by lazy {
        File(modelDirectory, "${EmbeddingModelArtifactSpec.FILE_NAME}.part")
    }

    private companion object {
        const val MODEL_PATH_PROPERTY = "sattory.embedding.model.path"
        const val MODEL_DIRECTORY_NAME = "models"
    }
}
