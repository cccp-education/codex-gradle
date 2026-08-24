package codex.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-RC-04-1 : configuration de connexion pgvector session memory.
 *
 * [ProfileConfig] is a pure data class (mirror `RagVectorStore` params)
 * portant les credentials de la base `codex`. Objet pur — unit-testable
 * sans base de données.
 */
class ProfileConfigTest {

    @Test
    fun `default config points at localhost codex`() {
        val config = ProfileConfig()
        assertEquals("localhost", config.host)
        assertEquals(5432, config.port)
        assertEquals("codex", config.database)
        assertEquals("codex", config.username)
        assertEquals("codex", config.password)
    }

    @Test
    fun `custom config preserves all fields`() {
        val config = ProfileConfig(
            host = "db.example.com",
            port = 6543,
            database = "codex-test",
            username = "admin",
            password = "secret"
        )
        assertEquals("db.example.com", config.host)
        assertEquals(6543, config.port)
        assertEquals("codex-test", config.database)
        assertEquals("admin", config.username)
        assertEquals("secret", config.password)
    }
}