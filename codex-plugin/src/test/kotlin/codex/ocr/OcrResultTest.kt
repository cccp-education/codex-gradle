package codex.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OcrResultTest {

    @Test
    fun `of creates result with expected fields`() {
        val result = OcrResult.of(
            text = "= Title\n\nContent paragraph.",
            confidence = 0.95,
            language = "fr",
            model = "gpt-oss:120b-cloud",
            metadata = mapOf("page" to "1", "source" to "scan-001")
        )
        assertEquals("= Title\n\nContent paragraph.", result.structuredText)
        assertEquals(0.95, result.confidence)
        assertEquals("fr", result.language)
        assertEquals("gpt-oss:120b-cloud", result.model)
        assertEquals(mapOf("page" to "1", "source" to "scan-001"), result.metadata)
    }

    @Test
    fun `of defaults sourceFormat to image-png`() {
        val result = OcrResult.of(
            text = "test",
            confidence = 0.8,
            language = "en",
            model = "test-model"
        )
        assertEquals("image/png", result.sourceFormat)
    }

    @Test
    fun `of generates non-null timestamp`() {
        val result = OcrResult.of(
            text = "test",
            confidence = 0.5,
            language = "de",
            model = "test-model"
        )
        assertNotNull(result.generatedAt)
        assertTrue(result.generatedAt.isNotEmpty())
    }

    @Test
    fun `of metadata defaults to empty map`() {
        val result = OcrResult.of(
            text = "test",
            confidence = 0.7,
            language = "it",
            model = "test-model"
        )
        assertTrue(result.metadata.isEmpty())
    }

    @Test
    fun `writeTo creates ocr-result json file`(@TempDir tempDir: File) {
        val result = OcrResult.of(
            text = "= Chapter 1\nContent.",
            confidence = 0.99,
            language = "fr",
            model = "gpt-oss:120b-cloud"
        )
        val file = OcrResult.writeTo(tempDir, result)
        assertTrue(file.exists())
        assertEquals("ocr-result.json", file.name)
        assertTrue(file.length() > 0)
    }

    @Test
    fun `fromFile roundtrip preserves all fields`(@TempDir tempDir: File) {
        val original = OcrResult(
            structuredText = "= Title\n\nParagraph.",
            confidence = 0.87,
            language = "en",
            sourceFormat = "image/tiff",
            generatedAt = "2026-05-30T12:00:00Z",
            model = "gpt-oss:120b-cloud",
            metadata = mapOf("dpi" to "300", "rotation" to "0")
        )
        OcrResult.writeTo(tempDir, original)
        val reloaded = OcrResult.fromFile(File(tempDir, "ocr-result.json"))
        assertEquals(original, reloaded)
    }

    @Test
    fun `writeTo JSON contains expected keys`(@TempDir tempDir: File) {
        val result = OcrResult.of(
            text = "test",
            confidence = 0.9,
            language = "fr",
            model = "test-model"
        )
        OcrResult.writeTo(tempDir, result)
        val json = File(tempDir, "ocr-result.json").readText()
        assertTrue(json.contains("\"structuredText\""))
        assertTrue(json.contains("\"confidence\""))
        assertTrue(json.contains("\"language\""))
        assertTrue(json.contains("\"sourceFormat\""))
        assertTrue(json.contains("\"model\""))
        assertTrue(json.contains("\"metadata\""))
    }

    @Test
    fun `writeTo creates parent directories`(@TempDir tempDir: File) {
        val nested = File(tempDir, "x/y/z")
        val result = OcrResult.of(
            text = "test",
            confidence = 0.5,
            language = "en",
            model = "test-model"
        )
        val file = OcrResult.writeTo(nested, result)
        assertTrue(nested.exists())
        assertTrue(file.exists())
    }
}
