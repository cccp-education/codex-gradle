package codex.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OcrConfigTest {

    @Test
    fun `defaultConfig returns deepseek-v4-pro with temperature zero`() {
        val config = OcrConfig.defaultConfig()
        assertEquals("ollama", config.provider)
        assertEquals("deepseek-v4-pro:latest", config.model)
        assertEquals(4096, config.maxTokens)
        assertEquals(0.0, config.temperature)
        assertEquals(null, config.endpoint)
        assertTrue(config.extra.isEmpty())
    }

    @Test
    fun `custom config with endpoint`() {
        val config = OcrConfig(
            provider = "openai",
            model = "gpt-4o",
            maxTokens = 2048,
            temperature = 0.3,
            endpoint = "https://api.custom.io/v1"
        )
        assertEquals("openai", config.provider)
        assertEquals("gpt-4o", config.model)
        assertEquals(2048, config.maxTokens)
        assertEquals(0.3, config.temperature)
        assertEquals("https://api.custom.io/v1", config.endpoint)
    }

    @Test
    fun `writeTo creates ocr-config json file`(@TempDir tempDir: File) {
        val config = OcrConfig.defaultConfig()
        val result = OcrConfig.writeTo(tempDir, config)
        assertTrue(result.exists())
        assertEquals("ocr-config.json", result.name)
        assertTrue(result.length() > 0)
    }

    @Test
    fun `fromFile roundtrip preserves all fields`(@TempDir tempDir: File) {
        val original = OcrConfig(
            provider = "deepseek-v4-pro",
            model = "deepseek-v4-pro:cloud",
            maxTokens = 8192,
            temperature = 0.1,
            endpoint = "https://api.corp.com/llm",
            extra = mapOf("region" to "eu-west")
        )
        OcrConfig.writeTo(tempDir, original)
        val reloaded = OcrConfig.fromFile(File(tempDir, "ocr-config.json"))
        assertEquals(original, reloaded)
    }

    @Test
    fun `writeTo JSON contains expected keys`(@TempDir tempDir: File) {
        OcrConfig.writeTo(tempDir, OcrConfig.defaultConfig())
        val json = File(tempDir, "ocr-config.json").readText()
        assertTrue(json.contains("\"provider\""))
        assertTrue(json.contains("\"model\""))
        assertTrue(json.contains("\"maxTokens\""))
        assertTrue(json.contains("\"temperature\""))
    }
}
