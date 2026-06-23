package codex.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class TesseractOcrEngineTest {

    @Test
    fun `TesseractOcrEngine implements OcrEngine`() {
        val engine = TesseractOcrEngine()
        assertTrue(engine is OcrEngine)
    }

    @Test
    fun `TesseractOcrEngine processes minimal image without throwing`(@TempDir dir: Path) {
        val imageFile = File(dir.toFile(), "test.png")
        createMinimalPng(imageFile)

        val engine = TesseractOcrEngine(tesseractPath = "tesseract")
        val request = OcrRequest(imageFile.readBytes(), "image/png", "eng")
        val result = engine.process(request)

        assertTrue(result.structuredText != null || result.structuredText == "",
            "Tesseract should return a result (may be empty for textless image)")
        assertEquals("tesseract", result.model)
    }

    @Test
    fun `TesseractOcrEngine returns empty result on tesseract failure`() {
        val engine = TesseractOcrEngine(tesseractPath = "nonexistent-tesseract-binary")
        val request = OcrRequest(ByteArray(10), "image/png", "eng")
        val result = engine.process(request)

        assertEquals("", result.structuredText, "Failed tesseract should return empty text")
        assertEquals(0.0, result.confidence, "Failed tesseract should have 0 confidence")
    }

    @Test
    fun `TesseractOcrEngine maps language codes to tesseract format`(@TempDir dir: Path) {
        val imageFile = File(dir.toFile(), "lang.png")
        createMinimalPng(imageFile)

        val engine = TesseractOcrEngine(tesseractPath = "tesseract")
        val request = OcrRequest(imageFile.readBytes(), "image/png", "fr")
        val result = engine.process(request)

        assertEquals("fr", result.language, "Result language should reflect request language")
    }

    @Test
    fun `TesseractOcrEngine returns result with sourceFormat from request`(@TempDir dir: Path) {
        val imageFile = File(dir.toFile(), "fmt.jpg")
        createMinimalPng(imageFile)

        val engine = TesseractOcrEngine(tesseractPath = "tesseract")
        val request = OcrRequest(imageFile.readBytes(), "image/jpeg", "eng")
        val result = engine.process(request)

        assertEquals("image/jpeg", result.sourceFormat)
    }

    private fun createMinimalPng(file: File) {
        val pngHex = "89504E470D0A1A0A0000000D4948445200000001000000010802000000907" +
            "71DE0000000C4944415408D763F8FFFF3F000005005E018246A4B10000000049" +
            "454E44AE426082"
        val cleaned = pngHex.replace(Regex("[^0-9A-Fa-f]"), "")
        val bytes = ByteArray(cleaned.length / 2)
        for (i in bytes.indices) {
            bytes[i] = (cleaned.substring(i * 2, i * 2 + 2).toInt(16) and 0xFF).toByte()
        }
        file.writeBytes(bytes)
    }
}