package org.sattory.app.model

import android.content.Context
import java.io.File

object EmbeddingModelFileSpec {
    const val FILE_NAME = EmbeddingModelArtifactSpec.FILE_NAME
    const val DOWNLOAD_URL = EmbeddingModelArtifactSpec.DOWNLOAD_URL

    fun directory(context: Context): File = File(context.filesDir, "models")
    fun file(context: Context): File = File(directory(context), FILE_NAME)
    fun partialFile(context: Context): File = File(directory(context), "$FILE_NAME.part")
}
