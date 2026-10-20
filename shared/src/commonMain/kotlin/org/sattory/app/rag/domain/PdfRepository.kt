package org.sattory.app.rag.domain

data class PdfDocument(val title: String, val text: String)

interface PdfRepository {
    suspend fun extract(reference: String): PdfDocument
}

object RagDocumentLimits {
    const val MAX_CHARACTERS = 200_000
    const val MAX_FILE_BYTES = 20L * 1024 * 1024
    const val MAX_PAGES = 500

    fun validate(text: String) {
        require(text.length <= MAX_CHARACTERS) { "O documento excede o limite de 200 mil caracteres." }
        require(text.trim().length >= 40) { "O documento precisa ter pelo menos 40 caracteres de texto. PDFs digitalizados precisam de OCR." }
    }
}

class ImportPdfUseCase(private val repository: PdfRepository) {
    suspend operator fun invoke(reference: String): PdfDocument = repository.extract(reference).also {
        RagDocumentLimits.validate(it.text)
    }
}
