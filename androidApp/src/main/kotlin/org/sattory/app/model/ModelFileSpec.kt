package org.sattory.app.model

import android.content.Context
import java.io.File

object ModelFileSpec {
    const val FILE_NAME = ModelArtifactSpec.FILE_NAME

    fun directory(context: Context): File = File(context.filesDir, "models")
    fun file(context: Context): File = File(directory(context), FILE_NAME)
    fun partialFile(context: Context): File = File(directory(context), "$FILE_NAME.part")
}
