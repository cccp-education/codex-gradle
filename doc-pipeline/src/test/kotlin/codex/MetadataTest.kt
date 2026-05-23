package codex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MetadataTest {

    @Test
    fun `forBrooklyn creates metadata with correct source`() {
        val metadata = Metadata.forBrooklyn()
        assertEquals("brooklyn", metadata.source)
        assertEquals("pipeline", metadata.type)
        assertEquals("onnx-local", metadata.model)
        assertEquals("1.0", metadata.version)
        assertTrue(metadata.dependencies.contains("queens"))
    }

    @Test
    fun `forBrooklyn accepts custom parameters`() {
        val metadata = Metadata.forBrooklyn(
            type = "ingest",
            model = "test-model",
            sessions = 5,
            dependencies = listOf("queens", "brooklyn")
        )
        assertEquals("brooklyn", metadata.source)
        assertEquals("ingest", metadata.type)
        assertEquals("test-model", metadata.model)
        assertEquals(5, metadata.sessions)
        assertEquals(listOf("queens", "brooklyn"), metadata.dependencies)
    }

    @Test
    fun `forBrooklyn generates non-null timestamp`(@TempDir tempDir: File) {
        val metadata = Metadata.forBrooklyn()
        assertNotNull(metadata.generatedAt)
        assertTrue(metadata.generatedAt.isNotEmpty())
    }

    @Test
    fun `writeTo creates metadata JSON file`(@TempDir tempDir: File) {
        val metadata = Metadata.forBrooklyn(type = "test-write")
        val resultFile = Metadata.writeTo(tempDir, metadata)

        assertTrue(resultFile.exists())
        assertEquals("metadata.json", resultFile.name)
        assertTrue(resultFile.length() > 0)
    }

    @Test
    fun `writeTo JSON contains expected fields`(@TempDir tempDir: File) {
        val metadata = Metadata.forBrooklyn()
        Metadata.writeTo(tempDir, metadata)

        val json = File(tempDir, "metadata.json").readText()
        assertTrue(json.contains("\"source\" : \"brooklyn\""))
        assertTrue(json.contains("\"type\" : \"pipeline\""))
        assertTrue(json.contains("\"model\" : \"onnx-local\""))
        assertTrue(json.contains("\"version\" : \"1.0\""))
        assertTrue(json.contains("\"dependencies\""))
        assertTrue(json.contains("\"queens\""))
    }

    @Test
    fun `writeTo creates parent directories`(@TempDir tempDir: File) {
        val nestedDir = File(tempDir, "a/b/c")
        val metadata = Metadata.forBrooklyn()
        val resultFile = Metadata.writeTo(nestedDir, metadata)

        assertTrue(nestedDir.exists())
        assertTrue(resultFile.exists())
    }

    @Test
    fun `metadata data class equality works`() {
        val m1 = Metadata.forBrooklyn()
        val m2 = Metadata.forBrooklyn()
        // generatedAt differs so they won't be equal
        assertEquals(m1.source, m2.source)
        assertEquals(m1.type, m2.type)
        assertEquals(m1.model, m2.model)
        assertEquals(m1.version, m2.version)
    }
}
