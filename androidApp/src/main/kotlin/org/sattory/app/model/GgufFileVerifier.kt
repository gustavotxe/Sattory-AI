package org.sattory.app.model

import java.io.File

object GgufFileVerifier {
    fun isValid(file: File): Boolean {
        if (!file.isFile || file.length() < GGUF_MAGIC.size) return false
        val header = ByteArray(GGUF_MAGIC.size)
        file.inputStream().use { input ->
            if (input.read(header) != header.size) return false
        }
        return header.contentEquals(GGUF_MAGIC)
    }

    private val GGUF_MAGIC = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())
}
