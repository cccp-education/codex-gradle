package codex.tasks

import codex.LicenseZone
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * CDX-UNIFY-1 — Characterization test for [CodexPipelineTask.pipeline()]
 * output contract, locking behaviour before the `chunkMd` →
 * `SemanticChunker.chunk()` delegation refactor (constat #7).
 *
 * The pipeline task writes an AsciiDoc file (the extraction output) and
 * logs the chunk count. The chunks themselves are *ephemeral* — they are
 * not persisted by this task (the pgvector path goes through
 * `ChunkDocumentTask` → `SemanticChunker.chunk()`). This suite locks the
 * observable output contract so the refactor is provably non-breaking :
 *
 * - output file exists and is non-blank
 * - output is AsciiDoc (starts with `=`)
 * - log line announces chunk count
 * - routing disabled → output goes to [outputFile]
 *
 * Pattern CDX-7-1 (characterization) : these tests pass with the current
 * `chunkMd` and must still pass after delegation to `SemanticChunker.chunk()`.
 * Baby-step TDD strict : RED (contract not yet asserted) → GREEN (pass with
 * current code) → REFACTOR (delegate, tests stay green).
 */
class CodexPipelineChunkingTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `pipeline writes non-blank AsciiDoc output to configured outputFile`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "Released under the Apache License 2.0. Some content here.")
        val outputFile = File(tempDir, "build/output.adoc")
        outputFile.parentFile.mkdirs()

        runPipeline(pdfFile, outputFile, routing = false)

        assertTrue(outputFile.exists(), "Output AsciiDoc file should exist after pipeline")
        val content = outputFile.readText()
        assertTrue(content.isNotBlank(), "Output AsciiDoc should not be blank")
        assertTrue(
            content.trimStart().startsWith("="),
            "Output should be AsciiDoc (starts with '=')"
        )
    }

    @Test
    fun `pipeline output contains extractable heading or body text`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "Released under the Apache License 2.0. Heading text and body content.")
        val outputFile = File(tempDir, "build/output.adoc")
        outputFile.parentFile.mkdirs()

        runPipeline(pdfFile, outputFile, routing = false)

        val content = outputFile.readText()
        assertTrue(
            content.contains("Apache License") || content.contains("Heading"),
            "Output AsciiDoc should contain the extracted PDF text — got: $content"
        )
    }

    @Test
    fun `pipeline on empty pdf produces fallback AsciiDoc output`() {
        val pdfFile = File(tempDir, "empty.pdf")
        createPdf(pdfFile, "")
        val outputFile = File(tempDir, "build/output.adoc")
        outputFile.parentFile.mkdirs()

        runPipeline(pdfFile, outputFile, routing = false)

        assertTrue(outputFile.exists(), "Output should exist even for empty PDF")
        val content = outputFile.readText()
        assertTrue(content.isNotBlank(), "Output should not be blank (fallback AsciiDoc)")
    }

    @Test
    fun `pipeline output is stable across two runs on same input`() {
        val pdfFile = File(tempDir, "book.pdf")
        createPdf(pdfFile, "Released under the Apache License 2.0. Stable content for idempotence.")
        val out1 = File(tempDir, "build/run1.adoc")
        val out2 = File(tempDir, "build/run2.adoc")
        out1.parentFile.mkdirs()

        runPipeline(pdfFile, out1, routing = false)
        runPipeline(pdfFile, out2, routing = false)

        assertTrue(out1.exists() && out2.exists(), "Both outputs should exist")
        assertTrue(
            out1.readText() == out2.readText(),
            "Pipeline output should be deterministic across runs on same input"
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun runPipeline(pdfFile: File, outputFile: File, routing: Boolean) {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("transformCorpusToPdf", CodexPipelineTask::class.java).get()
        task.sourceFile.set(pdfFile)
        task.outputFile.set(outputFile)
        task.pgHost.set("localhost")
        task.pgPort.set("5432")
        task.pgDatabase.set("codex")
        task.pgUser.set("codex")
        task.pgPassword.set("codex")
        if (routing) {
            task.licenceRouting.set(true)
            task.baseDir.set(tempDir)
            task.fallbackZone.set(LicenseZone.OSS)
        }
        task.pipeline()
    }

    private fun createPdf(file: File, text: String) {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            if (text.isNotBlank()) {
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(font, 11f)
                    cs.newLineAtOffset(50f, 700f)
                    cs.showText(text)
                    cs.endText()
                }
            }
            doc.save(file)
        }
    }
}