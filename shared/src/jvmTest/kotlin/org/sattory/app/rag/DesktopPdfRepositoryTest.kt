package org.sattory.app.rag

import java.io.File
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.sattory.app.rag.data.DesktopPdfRepository
import java.awt.image.BufferedImage
import kotlin.test.assertFalse
import org.sattory.app.rag.domain.ImportPdfUseCase
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class DesktopPdfRepositoryTest {
    @Test
    fun extractsEveryPageAndReleasesFile() = runTest {
        val file = File.createTempFile("rag-test-", ".pdf")
        try {
            PDDocument().use { document ->
                val image = BufferedImage(200, 40, BufferedImage.TYPE_INT_RGB)
                image.createGraphics().let { graphics ->
                    try { graphics.drawString("IMAGE_ONLY_SECRET", 5, 20) } finally { graphics.dispose() }
                }
                val embeddedImage = LosslessFactory.createFromImage(document, image)
                for (number in 1..100) {
                    val page = PDPage()
                    document.addPage(page)
                    PDPageContentStream(document, page).use { content ->
                        content.drawImage(embeddedImage, 50f, 600f)
                        content.beginText()
                        content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                        content.newLineAtOffset(50f, 700f)
                        content.showText("Page $number contains information for this RAG profile.")
                        content.endText()
                    }
                }
                document.save(file)
            }
            val result = ImportPdfUseCase(DesktopPdfRepository())(file.absolutePath)
            for (number in 1..100) assertContains(result.text, "Page $number contains")
            assertFalse(result.text.contains("IMAGE_ONLY_SECRET"))
            check(file.delete())
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsPdfWithoutExtractableText() = runTest {
        val file = File.createTempFile("rag-empty-", ".pdf")
        try {
            PDDocument().use { document ->
                document.addPage(PDPage())
                document.save(file)
            }
            assertFailsWith<IllegalArgumentException> {
                ImportPdfUseCase(DesktopPdfRepository())(file.absolutePath)
            }
        } finally {
            file.delete()
        }
    }
}
