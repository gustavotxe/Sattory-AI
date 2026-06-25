package org.sattory.app.model

import android.content.Context
import java.io.File

object ModelFileSpec {
    const val FILE_NAME = ModelArtifactSpec.FILE_NAME
    const val DOWNLOAD_URL = ModelArtifactSpec.DOWNLOAD_URL
    const val SHA256 = ModelArtifactSpec.SHA256

    fun directory(context: Context): File = File(context.filesDir, "models")
    fun file(context: Context): File = File(directory(context), FILE_NAME)
    fun partialFile(context: Context): File = File(directory(context), "$FILE_NAME.part")
}
