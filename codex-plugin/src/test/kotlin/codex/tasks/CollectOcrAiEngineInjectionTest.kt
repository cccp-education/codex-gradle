package codex.tasks

import codex.ocr.OcrEngine
import codex.ocr.OcrRequest
import codex.ocr.OcrResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * CDX-OCR-1 — Injection of the AI OCR port into [CollectOcrTask].
 *
 * Boundary rule (cadrage EPIC CDX-OCR-BOUNDARY, S-096): software OCR
 * (Tesseract) is actioned by codex; AI-assisted OCR is actioned by the
 * codebase socle. The port is the existing [OcrEngine] fun interface —
 * no new type. The AI engine is injected by the composition root
 * (consumer); without injection the pipeline degrades to Tesseract-only.
 *
 * CDX-OCR-3 (S-100) purge: the codex IA stack (`LlmOcrEngine`,
 * `HttpOllamaChatClient`, `OllamaChatClient`, `OcrConfig`) and the
 * inert `ollamaHost`/`ollamaPort`/`model` properties on
 * [CollectOcrTask] / [codex.CodexExtension] have been removed. Codex
 * no longer wires an AI engine itself — only Tesseract remains.
 */
class CollectOcrAiEngineInjectionTest {

    @Test
    fun `task exposes an optional aiEngine property`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask

        assertNotNull(task.aiEngine, "aiEngine property must exist on CollectOcrTask")
        assertNull(task.aiEngine.orNull, "aiEngine must be unset by default (degraded Tesseract-only mode)")
    }

    @Test
    fun `plugin apply leaves aiEngine unset - degraded Tesseract only is the default`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask

        // The plugin wires ollama conventions but MUST NOT pre-wire an AI engine:
        // wiring an AI engine is the consumer's (composition root) responsibility.
        assertNull(task.aiEngine.orNull)
    }

    @Test
    fun `injected AI engine structured AsciiDoc flows to page adoc file`(
        @TempDir tempDir: File
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        File(imagesDir, "page-001.png").writeBytes(minimalPng())

        val outputDir = File(tempDir, "ocr-pages").apply { mkdirs() }

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(imagesDir)
        task.outputDir.set(outputDir)
        task.language.set("fr")
        task.aiEngine.set(fakeAiEngine("= Chapter One\n\nStructured content from AI engine."))

        task.collectOcr()

        val pageFile = File(outputDir, "001-page-001.adoc")
        assertTrue(pageFile.exists(), "page file must be written")
        assertEquals(
            "= Chapter One\n\nStructured content from AI engine.",
            pageFile.readText(),
            "page content must be the injected engine structured AsciiDoc output"
        )
    }

    @Test
    fun `AI engine failure falls back to Tesseract degraded marker`(
        @TempDir tempDir: File
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        File(imagesDir, "page-001.png").writeBytes(minimalPng())

        val outputDir = File(tempDir, "ocr-pages").apply { mkdirs() }

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(imagesDir)
        task.outputDir.set(outputDir)
        task.language.set("fr")
        // AI engine produces empty text with zero confidence → pipeline falls back
        // to Tesseract (absent or unable to read a 1x1 blank PNG) → failure marker.
        task.aiEngine.set(fakeAiEngine("", confidence = 0.0))

        task.collectOcr()

        val pageFile = File(outputDir, "001-page-001.adoc")
        assertTrue(pageFile.exists())
        assertEquals("[page vide ou OCR échec]", pageFile.readText())
    }

    @Test
    fun `injected AI engine also feeds legacy outputFile backward compat`(
        @TempDir tempDir: File
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        File(imagesDir, "page-001.png").writeBytes(minimalPng())

        val outputDir = File(tempDir, "ocr-pages").apply { mkdirs() }
        val legacyFile = File(tempDir, "ocr-legacy.adoc")

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(imagesDir)
        task.outputDir.set(outputDir)
        task.outputFile.set(legacyFile)
        task.language.set("fr")
        task.aiEngine.set(fakeAiEngine("= Legacy\n\ncontent"))

        task.collectOcr()

        assertTrue(legacyFile.exists(), "legacy outputFile must still be written")
        val content = legacyFile.readText()
        assertTrue(content.startsWith("= OCR Book"), "legacy header preserved")
        assertTrue(content.contains("== Page 1"), "legacy page header preserved")
        assertTrue(content.contains("= Legacy\n\ncontent"), "injected engine content flows to legacy output")
    }

    @Test
    fun `no images short-circuits before any engine runs even when AI engine injected`(
        @TempDir tempDir: File
    ) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val emptyDir = File(tempDir, "images-empty").apply { mkdirs() }
        val outputDir = File(tempDir, "ocr-pages").apply { mkdirs() }
        val outputFile = File(tempDir, "ocr-output.adoc")

        var engineCalls = 0
        val countingEngine = object : OcrEngine {
            override fun process(request: OcrRequest): OcrResult {
                engineCalls++
                return OcrResult.of(
                    text = "should not happen",
                    confidence = 0.9,
                    language = request.language,
                    model = "counting-ai",
                    metadata = mapOf("engine" to "counting-ai")
                )
            }
        }

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(emptyDir)
        task.outputDir.set(outputDir)
        task.outputFile.set(outputFile)
        task.language.set("fr")
        task.aiEngine.set(countingEngine)

        task.collectOcr()

        assertEquals(0, engineCalls, "no engine may run when there are no images")
        assertFalse(File(outputDir, "001-x.adoc").exists())
        assertTrue(outputFile.readText().contains("No images found"))
    }

    private fun fakeAiEngine(text: String, confidence: Double = if (text.isNotEmpty()) 0.9 else 0.0): OcrEngine =
        object : OcrEngine {
            override fun process(request: OcrRequest): OcrResult = OcrResult.of(
                text = text,
                confidence = confidence,
                language = request.language,
                model = "fake-ai",
                metadata = mapOf("engine" to "fake-ai")
            )
        }

    private fun minimalPng(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk header
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1 pixel
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(), // RGBA, CRC
        0x89.toByte(), // IDAT start
    )
}
