package codex.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OcrRequestTest {

    @Test
    fun `request with byte array equals same content`() {
        val bytes = "image-data".toByteArray()
        val a = OcrRequest(bytes, "image/png", "fr")
        val b = OcrRequest(bytes.clone(), "image/png", "fr")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `request with different byte arrays are not equal`() {
        val a = OcrRequest("data-a".toByteArray(), "image/png", "fr")
        val b = OcrRequest("data-b".toByteArray(), "image/png", "fr")
        assertNotEquals(a, b)
    }

    @Test
    fun `request with different format are not equal`() {
        val bytes = "data".toByteArray()
        val a = OcrRequest(bytes, "image/png", "fr")
        val b = OcrRequest(bytes.clone(), "image/jpeg", "fr")
        assertNotEquals(a, b)
    }

    @Test
    fun `request with different language are not equal`() {
        val bytes = "data".toByteArray()
        val a = OcrRequest(bytes, "image/png", "fr")
        val b = OcrRequest(bytes.clone(), "image/png", "en")
        assertNotEquals(a, b)
    }

    @Test
    fun `request with different prompt are not equal`() {
        val bytes = "data".toByteArray()
        val a = OcrRequest(bytes, "image/png", "fr", prompt = "extract text")
        val b = OcrRequest(bytes.clone(), "image/png", "fr", prompt = "extract tables")
        assertNotEquals(a, b)
    }

    @Test
    fun `request with same prompt are equal`() {
        val bytes = "data".toByteArray()
        val a = OcrRequest(bytes, "image/png", "fr", prompt = "extract text")
        val b = OcrRequest(bytes.clone(), "image/png", "fr", prompt = "extract text")
        assertEquals(a, b)
    }

    @Test
    fun `request with null prompt are equal`() {
        val bytes = "data".toByteArray()
        val a = OcrRequest(bytes, "image/png", "fr", prompt = null)
        val b = OcrRequest(bytes.clone(), "image/png", "fr", prompt = null)
        assertEquals(a, b)
    }

    @Test
    fun `toString does not print byte content`() {
        val request = OcrRequest(ByteArray(1024), "image/png", "fr", prompt = "hello")
        val str = request.toString()
        assertTrue(str.contains("OcrRequest"))
        assertTrue(str.contains("image/png"))
        assertTrue(str.contains("fr"))
        assertTrue(str.contains("hello"))
        assertTrue(str.contains("1024 bytes"))
    }

    @Test
    fun `writeTo creates json file with metadata only`(@TempDir tempDir: File) {
        val request = OcrRequest(ByteArray(10), "image/jpeg", "en", prompt = "test")
        val file = OcrRequest.writeTo(tempDir, request)
        assertTrue(file.exists())
        assertEquals("ocr-request.json", file.name)
        assertTrue(file.length() > 0)
    }

    @Test
    fun `fromFile roundtrip preserves metadata fields`(@TempDir tempDir: File) {
        val original = OcrRequest(ByteArray(64), "image/tiff", "de", prompt = "ocr this")
        OcrRequest.writeTo(tempDir, original)
        val reloaded = OcrRequest.fromFile(File(tempDir, "ocr-request.json"))
        assertEquals("image/tiff", reloaded.format)
        assertEquals("de", reloaded.language)
        assertEquals("ocr this", reloaded.prompt)
        assertEquals(64, reloaded.imageData.size)
    }
}
