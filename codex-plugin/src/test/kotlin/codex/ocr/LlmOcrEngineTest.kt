package codex.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmOcrEngineTest {

    @Test
    fun `LlmOcrEngine implements OcrEngine`() {
        val engine = LlmOcrEngine(fakeClientReturning("= Title\n\nBody text"), OcrConfig.defaultConfig())
        assertTrue(engine is OcrEngine)
    }

    @Test
    fun `process returns OcrResult with structured AsciiDoc text from LLM response`() {
        val adoc = "= Chapitre 1\n\nParagraphe de test."
        val engine = LlmOcrEngine(fakeClientReturning(adoc), OcrConfig.defaultConfig())
        val request = OcrRequest(
            imageData = ByteArray(16) { 0 },
            format = "image/png",
            language = "fr"
        )

        val result = engine.process(request)

        assertEquals(adoc, result.structuredText)
        assertTrue(result.confidence > 0.0, "LLM OCR should have positive confidence")
        assertEquals("fr", result.language)
        assertEquals("image/png", result.sourceFormat)
        assertEquals("gpt-oss:120b-cloud", result.model)
        assertEquals("ollama", result.metadata["engine"])
    }

    @Test
    fun `process returns empty result when LLM response is blank`() {
        val engine = LlmOcrEngine(fakeClientReturning(""), OcrConfig.defaultConfig())
        val request = OcrRequest(ByteArray(4), "image/png", "en")

        val result = engine.process(request)

        assertEquals("", result.structuredText)
        assertEquals(0.0, result.confidence)
        assertEquals("gpt-oss:120b-cloud", result.model)
    }

    @Test
    fun `process returns empty result when client throws`() {
        val engine = LlmOcrEngine(crashingClient(), OcrConfig.defaultConfig())
        val request = OcrRequest(ByteArray(4), "image/png", "en")

        val result = engine.process(request)

        assertEquals("", result.structuredText)
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun `process uses custom prompt when provided in request`() {
        val capturing = CapturingClient()
        val engine = LlmOcrEngine(capturing, OcrConfig.defaultConfig())
        val request = OcrRequest(
            imageData = ByteArray(8),
            format = "image/png",
            language = "fr",
            prompt = "Extract text and structure as AsciiDoc sections"
        )

        engine.process(request)

        assertTrue(capturing.lastPrompt!!.contains("Extract text and structure as AsciiDoc sections"))
    }

    @Test
    fun `process encodes image as base64 in Ollama chat payload`() {
        val capturing = CapturingClient()
        val engine = LlmOcrEngine(capturing, OcrConfig.defaultConfig())
        val imageBytes = byteArrayOf(0x42.toByte(), 0x4D.toByte())
        val request = OcrRequest(imageBytes, "image/png", "fr")

        engine.process(request)

        assertTrue(capturing.lastImages!!.isNotEmpty(), "Image list should not be empty")
        assertTrue(capturing.lastImages!!.size == 1, "Should send exactly one image")
    }

    private fun fakeClientReturning(text: String): OllamaChatClient = object : OllamaChatClient {
        override fun chat(config: OcrConfig, prompt: String, images: List<String>): String = text
    }

    private fun crashingClient(): OllamaChatClient = object : OllamaChatClient {
        override fun chat(config: OcrConfig, prompt: String, images: List<String>): String =
            throw RuntimeException("Connection refused")
    }

    private class CapturingClient : OllamaChatClient {
        var lastPrompt: String? = null
        var lastImages: List<String>? = null
        override fun chat(config: OcrConfig, prompt: String, images: List<String>): String {
            lastPrompt = prompt
            lastImages = images
            return "= Captured\n\nContent"
        }
    }
}