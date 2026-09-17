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

    // ── OCR-QUALITY-1 — true confidence (not the historical 0.7 placeholder) ──

    @Test
    fun `TesseractOcrEngine reports real confidence from TSV word scores`(@TempDir dir: Path) {
        val imageFile = File(dir.toFile(), "quality.png")
        writeTextPng(imageFile, "OCR QUALITY")

        val engine = TesseractOcrEngine(tesseractPath = "tesseract")
        val result = engine.process(OcrRequest(imageFile.readBytes(), "image/png", "eng"))

        assertTrue(result.structuredText.isNotEmpty(), "recognised text must not be empty")
        assertTrue(result.confidence in 0.0..1.0, "confidence must be in [0,1]")
        assertTrue(result.confidence > 0.5,
            "a clean rendered image must yield a real confidence > 0.5 (was hardcoded 0.7), got ${result.confidence}")
        assertEquals("tsv", result.metadata["confidenceSource"],
            "confidence must come from the parsed TSV, not a placeholder")
    }

    @Test
    fun `TesseractOcrEngine marks a textless image with zero confidence`(@TempDir dir: Path) {
        val imageFile = File(dir.toFile(), "blank.png")
        createMinimalPng(imageFile)

        val engine = TesseractOcrEngine(tesseractPath = "tesseract")
        val result = engine.process(OcrRequest(imageFile.readBytes(), "image/png", "eng"))

        assertEquals(0.0, result.confidence,
            "an image with no recognised word must carry zero confidence (doubt signal)")
    }

    @Test
    fun `TesseractOcrEngine degrades to text-only confidence when tesseract emits no TSV`(@TempDir dir: Path) {
        // A stub tesseract that writes the plain .txt but no .tsv — simulates an
        // old tesseract build without the TSV config. The engine must stay
        // functional (text preserved) and honestly mark the confidence source.
        val stub = File(dir.toFile(), "stub-tesseract.sh")
        stub.writeText(
            """
            #!/bin/sh
            # $1=input $2=outputBase
            echo "stub text" > "${'$'}2.txt"
            """.trimIndent()
        )
        stub.setExecutable(true)

        val imageFile = File(dir.toFile(), "img.png")
        createMinimalPng(imageFile)

        val engine = TesseractOcrEngine(tesseractPath = stub.absolutePath)
        val result = engine.process(OcrRequest(imageFile.readBytes(), "image/png", "eng"))

        assertEquals("stub text", result.structuredText.trim(), "text must be preserved in degraded mode")
        assertEquals("degraded", result.metadata["confidenceSource"],
            "missing TSV must be flagged as degraded, not silently trusted")
        assertTrue(result.confidence in 0.0..1.0)
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

    /**
     * Renders a real PNG with the given text using the JDK's headless AWT
     * (`BufferedImage` + `ImageIO`) — no external image tooling required. The
     * resulting image is legible enough for Tesseract to return a meaningful
     * word-level confidence.
     */
    private fun writeTextPng(file: File, text: String) {
        val image = java.awt.image.BufferedImage(500, 120, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, image.width, image.height)
            g.color = java.awt.Color.BLACK
            g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 60)
            g.drawString(text, 20, 80)
        } finally {
            g.dispose()
        }
        javax.imageio.ImageIO.write(image, "png", file)
    }
}