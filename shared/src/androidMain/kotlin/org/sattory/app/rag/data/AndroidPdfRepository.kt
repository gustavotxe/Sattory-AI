package org.sattory.app.rag.data

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.sattory.app.rag.domain.PdfDocument
import org.sattory.app.rag.domain.PdfRepository
import org.sattory.app.rag.domain.RagDocumentLimits
import java.io.File
import java.io.Writer

class AndroidPdfRepository(context: Context) : PdfRepository {
    private val context = context.applicationContext

    override suspend fun extract(reference: String): PdfDocument = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val uri = reference.toUri()
        val title = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Documento PDF"
        val temporary = File.createTempFile("rag-", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= RagDocumentLimits.MAX_FILE_BYTES) { "O PDF excede o limite de 20 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Nao foi possivel abrir o PDF.")
            PDDocument.load(temporary, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                extractDocument(document, title)
            }
        } finally {
            temporary.delete()
        }
    }
    private suspend fun extractDocument(document: PDDocument, title: String): PdfDocument {
        require(!document.isEncrypted) { "Selecione um PDF sem senha." }
        require(document.currentAccessPermission.canExtractContent()) { "O PDF nao permite extracao de texto." }
        require(document.numberOfPages <= RagDocumentLimits.MAX_PAGES) { "O PDF excede o limite de 500 paginas." }
        val coroutineContext = currentCoroutineContext()
        val text = StringBuilder()
        val writer = object : Writer() {
            override fun write(buffer: CharArray, offset: Int, length: Int) {
                coroutineContext.ensureActive()
                require(text.length + length <= RagDocumentLimits.MAX_CHARACTERS) { "O PDF excede o limite de 200 mil caracteres." }
                text.append(buffer, offset, length)
            }
            override fun flush() = Unit
            override fun close() = Unit
        }

        PDFTextStripper().writeText(document, writer)
        return PdfDocument(title.removeSuffix(".pdf"), text.toString())
    }
}