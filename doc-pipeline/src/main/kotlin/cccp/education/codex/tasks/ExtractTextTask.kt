package codex.tasks

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Extracts raw text from a PDF file using PDFBox.
 *
 * Strips all formatting and produces plain text sorted by position.
 * This is the simplest extraction mode — no structure or hierarchy is preserved.
 *
 * @property pdfFile input PDF file
 * @property outputFile output plain text file
 */
abstract class ExtractTextTask : DefaultTask() {

    @get:InputFile
    abstract val pdfFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun extract() {
        val input = pdfFile.asFile.get()
        val output = outputFile.asFile.get()

        logger.lifecycle("[codex] collectText : ${input.name} → ${output.name}")

        Loader.loadPDF(input).use { document ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
            }
            val rawText = stripper.getText(document)
            output.writeText(rawText)
        }

        logger.lifecycle("[codex] ✓ collectText done — ${output.length()} bytes")
    }
}
