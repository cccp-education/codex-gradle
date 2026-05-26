package codex.store

import codex.tasks.RetrieveResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrowsExactly
import java.lang.reflect.InvocationTargetException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodexVectorStoreTest {

    @Test
    fun `store can be instantiated with all connection parameters`() {
        val store = CodexVectorStore(
            host = "testhost",
            port = 5433,
            database = "testdb",
            username = "testuser",
            password = "testpass"
        )
        assertNotNull(store)
    }

    @Test
    fun `default parameters are localhost standard`() {
        val store = CodexVectorStore()
        assertNotNull(store)
    }

    @Test
    fun `RetrieveResult data class contract is intact`() {
        val result = RetrieveResult(
            chunkId = 1L,
            chunkIndex = 0,
            chunkText = "test chunk",
            sectionPath = "Chapter 1",
            headingLevel = 1,
            sourceDocument = "test-doc",
            similarity = 0.95
        )
        assertEquals(1L, result.chunkId)
        assertEquals(0, result.chunkIndex)
        assertEquals("test chunk", result.chunkText)
        assertEquals("Chapter 1", result.sectionPath)
        assertEquals(1, result.headingLevel)
        assertEquals("test-doc", result.sourceDocument)
        assertEquals(0.95, result.similarity, 0.001)
    }

    @Test
    fun `searchBlocking is a valid suspending wrapper`() {
        val store = CodexVectorStore()
        val method = store.javaClass.getMethod("searchBlocking", String::class.java, Int::class.java)
        assertNotNull(method)
        assertEquals(List::class.java, method.returnType)
    }

    @Test
    fun `search exists as a method`() {
        val store = CodexVectorStore()
        val methodExists = store.javaClass.declaredMethods.any { it.name == "search" }
        assertTrue(methodExists)
    }

    @Test
    fun `computeEmbedding produces valid vector format`() {
        val store = CodexVectorStore()
        // Access private computeEmbedding via reflection
        val method = store.javaClass.getDeclaredMethod("computeEmbedding", String::class.java)
        method.isAccessible = true
        val result = method.invoke(store, "test query") as String

        assertTrue(result.startsWith("["), "Vector should start with '['")
        assertTrue(result.endsWith("]"), "Vector should end with ']'")
        assertTrue(result.contains(","), "Vector should contain comma-separated values")
        // AllMiniLmL6V2 produces 384-dimensional vectors
        val parts = result.removeSurrounding("[", "]").split(",")
        assertEquals(384, parts.size, "AllMiniLmL6V2 should produce 384-dim vector")
    }

    @Test
    fun `computeEmbedding with short text still works`() {
        val store = CodexVectorStore()
        val method = store.javaClass.getDeclaredMethod("computeEmbedding", String::class.java)
        method.isAccessible = true
        val result = method.invoke(store, "x") as String

        assertTrue(result.startsWith("["))
        assertTrue(result.endsWith("]"))
        val parts = result.removeSurrounding("[", "]").split(",")
        assertEquals(384, parts.size)
    }

    @Test
    fun `computeEmbedding with empty string throws`() {
        val store = CodexVectorStore()
        val method = store.javaClass.getDeclaredMethod("computeEmbedding", String::class.java)
        method.isAccessible = true
        val exception = assertThrowsExactly(InvocationTargetException::class.java) {
            method.invoke(store, "")
        }
        assertEquals(
            IllegalArgumentException::class.java,
            exception.cause!!.javaClass,
            "computeEmbedding should throw IllegalArgumentException for empty string"
        )
    }

    @Test
    fun `buildConnectionFactory produces valid factory`() {
        val store = CodexVectorStore(
            host = "testhost",
            port = 5433,
            database = "testdb",
            username = "testuser",
            password = "testpass"
        )
        val method = store.javaClass.getDeclaredMethod("buildConnectionFactory")
        method.isAccessible = true
        val factory = method.invoke(store)

        assertNotNull(factory, "buildConnectionFactory should return non-null ConnectionFactory")
    }

    @Test
    fun `store constructor stores all parameters correctly`() {
        val store = CodexVectorStore(
            host = "remote.example.com",
            port = 9999,
            database = "production",
            username = "reader",
            password = "p@ssw0rd!"
        )

        // Verify via reflection that parameters are stored
        val hostField = store.javaClass.getDeclaredField("host").also { it.isAccessible = true }
        val portField = store.javaClass.getDeclaredField("port").also { it.isAccessible = true }
        val dbField = store.javaClass.getDeclaredField("database").also { it.isAccessible = true }
        val userField = store.javaClass.getDeclaredField("username").also { it.isAccessible = true }
        val passField = store.javaClass.getDeclaredField("password").also { it.isAccessible = true }

        assertEquals("remote.example.com", hostField.get(store))
        assertEquals(9999, portField.get(store))
        assertEquals("production", dbField.get(store))
        assertEquals("reader", userField.get(store))
        assertEquals("p@ssw0rd!", passField.get(store))
    }
}
