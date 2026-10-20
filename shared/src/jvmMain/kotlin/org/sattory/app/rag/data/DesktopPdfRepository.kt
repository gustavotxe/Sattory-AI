package org.sattory.app.rag.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.sattory.app.rag.domain.PdfDocument
import org.sattory.app.rag.domain.PdfRepository
import org.sattory.app.rag.domain.RagDocumentLimits
import java.io.File
import java.io.Writer

class DesktopPdfRepository : PdfRepository {
    override suspend fun extract(reference: String): PdfDocument = withContext(Dispatchers.IO) {
        val file = File(reference)
        require(file.length() <= RagDocumentLimits.MAX_FILE_BYTES) { "O PDF excede o limite de 20 MB." }
        Loader.loadPDF(file, IOUtils.createTempFileOnlyStreamCache()).use { document ->
            extractDocument(document, file.nameWithoutExtension)
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