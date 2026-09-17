package codex.tasks

import codex.ocr.OcrEngine
import codex.ocr.OcrRequest
import codex.ocr.OcrResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * OCR-QUALITY-2 — problematic-image detection wired into [CollectOcrTask].
 *
 * The analyzer ([codex.ocr.OcrQualityAnalyzer]) is unit-tested in isolation;
 * this suite proves the acquisition task exposes the doubt to the outside
 * world as a JSON report — the artefact the human iteration (and the
 * downstream RAG ingestion, OCR-QUALITY-4) consumes.
 *
 * Baby-step TDD RED → GREEN → REFACTOR.
 */
class CollectOcrQualityReportTest {

    @Test
    fun `task exposes an optional qualityReportFile property`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        assertNotNull(task.qualityReportFile, "qualityReportFile property must exist")
        assertFalse(task.qualityReportFile.isPresent, "qualityReportFile is optional (off by default)")
    }

    @Test
    fun `collectOcr writes a quality report flagging a low-confidence page`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        File(imagesDir, "page-001.png").writeBytes(minimalPng())
        val outputDir = File(tempDir, "pages").apply { mkdirs() }
        val reportFile = File(tempDir, "ocr-quality-report.json")

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(imagesDir)
        task.outputDir.set(outputDir)
        task.qualityReportFile.set(reportFile)
        task.language.set("fr")
        // An engine that returns readable text at a doubtful confidence → LOW_CONFIDENCE.
        task.aiEngine.set(fakeEngine("A long enough readable page body above the short threshold.", 0.30))

        task.collectOcr()

        assertTrue(reportFile.exists(), "quality report must be written when configured")
        val json = reportFile.readText()
        assertTrue(json.contains("\"imagesScanned\": 1"), "report must count scanned images: $json")
        assertTrue(json.contains("LOW_CONFIDENCE"), "report must flag the doubtful page: $json")
    }

    @Test
    fun `collectOcr quality report flags a ghost image`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        File(imagesDir, "page-001.png").writeBytes(minimalPng())
        val outputDir = File(tempDir, "pages").apply { mkdirs() }
        val reportFile = File(tempDir, "ocr-quality-report.json")

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(imagesDir)
        task.outputDir.set(outputDir)
        task.qualityReportFile.set(reportFile)
        task.language.set("fr")
        task.aiEngine.set(
            fakeEngine(
                "A long enough readable page body above the short threshold.\nimage::ghost.png[]",
                0.95
            )
        )

        task.collectOcr()

        val json = reportFile.readText()
        assertTrue(json.contains("IMAGE_MISSING"), "report must flag the ghost image: $json")
        assertTrue(json.contains("ghost.png"), "report must carry the missing target: $json")
    }

    @Test
    fun `collectOcr writes no quality report when the property is unset`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val imagesDir = File(tempDir, "images").apply { mkdirs() }
        File(imagesDir, "page-001.png").writeBytes(minimalPng())
        val outputDir = File(tempDir, "pages").apply { mkdirs() }

        val task = project.tasks.findByName("collectOcr") as CollectOcrTask
        task.inputDir.set(imagesDir)
        task.outputDir.set(outputDir)
        task.language.set("fr")
        task.aiEngine.set(fakeEngine("A long enough readable page body above the short threshold.", 0.95))

        task.collectOcr()

        // Backward compatible: no report unless explicitly requested.
        assertEquals(0, outputDir.parentFile.listFiles { f -> f.name.contains("quality") }?.size ?: 0)
    }

    private fun fakeEngine(text: String, confidence: Double): OcrEngine = object : OcrEngine {
        override fun process(request: OcrRequest): OcrResult = OcrResult.of(
            text = text,
            confidence = confidence,
            language = request.language,
            model = "fake-ai",
            metadata = mapOf("engine" to "fake-ai")
        )
    }

    private fun minimalPng(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
        0x89.toByte(),
    )
}
