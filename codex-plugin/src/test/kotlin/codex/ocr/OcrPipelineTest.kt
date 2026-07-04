package codex.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OcrPipelineTest {

    @Test
    fun `OcrPipeline returns LLM result when LLM succeeds`() {
        val llm = stubEngineReturning("= Title\n\nLLM content", model = "gpt-oss:120b-cloud")
        val tesseract = stubEngineReturning("tesseract text", model = "tesseract")
        val pipeline = OcrPipeline(listOf(llm, tesseract))

        val result = pipeline.process(OcrRequest(ByteArray(8), "image/png", "fr"))

        assertEquals("= Title\n\nLLM content", result.structuredText)
        assertEquals("gpt-oss:120b-cloud", result.model)
    }

    @Test
    fun `OcrPipeline falls back to Tesseract when LLM returns empty text`() {
        val llm = stubEngineReturning("", model = "gpt-oss:120b-cloud")
        val tesseract = stubEngineReturning("tesseract fallback text", model = "tesseract")
        val pipeline = OcrPipeline(listOf(llm, tesseract))

        val result = pipeline.process(OcrRequest(ByteArray(8), "image/png", "fr"))

        assertEquals("tesseract fallback text", result.structuredText)
        assertEquals("tesseract", result.model)
    }

    @Test
    fun `OcrPipeline falls back to next engine when LLM confidence is zero`() {
        val llm = stubEngineReturning("", confidence = 0.0, model = "gpt-oss:120b-cloud")
        val tesseract = stubEngineReturning("tess content", confidence = 0.7, model = "tesseract")
        val pipeline = OcrPipeline(listOf(llm, tesseract))

        val result = pipeline.process(OcrRequest(ByteArray(8), "image/png", "en"))

        assertEquals("tess content", result.structuredText)
        assertEquals("tesseract", result.model)
    }

    @Test
    fun `OcrPipeline returns empty result when all engines fail`() {
        val llm = stubEngineReturning("", confidence = 0.0, model = "gpt-oss")
        val tess = stubEngineReturning("", confidence = 0.0, model = "tesseract")
        val pipeline = OcrPipeline(listOf(llm, tess))

        val result = pipeline.process(OcrRequest(ByteArray(8), "image/png", "en"))

        assertEquals("", result.structuredText)
        assertEquals(0.0, result.confidence)
    }

    @Test
    fun `OcrPipeline with single engine returns that engine result`() {
        val only = stubEngineReturning("only text", model = "solo")
        val pipeline = OcrPipeline(listOf(only))

        val result = pipeline.process(OcrRequest(ByteArray(4), "image/png", "fr"))

        assertEquals("only text", result.structuredText)
        assertEquals("solo", result.model)
    }

    @Test
    fun `OcrPipeline metadata records which engine won`() {
        val llm = stubEngineReturning("= Adoc\n\ncontent", model = "gpt-oss:120b-cloud")
        val pipeline = OcrPipeline(listOf(llm))

        val result = pipeline.process(OcrRequest(ByteArray(4), "image/png", "fr"))

        assertTrue(result.metadata.containsKey("engine"))
    }

    private fun stubEngineReturning(
        text: String,
        confidence: Double = if (text.isNotEmpty()) 0.9 else 0.0,
        model: String = "stub"
    ): OcrEngine = object : OcrEngine {
        override fun process(request: OcrRequest): OcrResult = OcrResult.of(
            text = text,
            confidence = confidence,
            language = request.language,
            model = model,
            metadata = mapOf("engine" to model)
        )
    }
}