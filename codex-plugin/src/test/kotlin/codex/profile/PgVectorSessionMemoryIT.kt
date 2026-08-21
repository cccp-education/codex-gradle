package codex.profile

import contracts.runtime.LearnerProfile
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

/**
 * TDD — EPIC CDX-RC-04-2 : functional test R2DBC testcontainers du pont RAG
 * session memory.
 *
 * Valide l'adapter [PgVectorSessionMemory] contre une vraie pgvector via
 * Testcontainers. Le profil stagiaire est persisté dans `codex_learner_profiles`
 * (séparée du corpus documentaire) avec un embedding 384-dim ONNX calculé
 * depuis les weakPoints + annotations.
 *
 * Pattern `CodexIngestRetrieveIT` — `@Tag("integration")` (skip si Docker
 * indisponible via `assumeTrue` dans le runner, ici le container démarre
 * directement).
 */
@Tag("integration")
class PgVectorSessionMemoryIT {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("codex").withUsername("codex").withPassword("codex")

        @JvmStatic @BeforeAll fun start() { postgres.start() }
        @JvmStatic @AfterAll fun stop() { postgres.stop() }
    }

    private fun memory() = PgVectorSessionMemory(
        ProfileConfig(
            host = postgres.host,
            port = postgres.firstMappedPort,
            database = postgres.databaseName,
            username = postgres.username,
            password = postgres.password
        )
    )

    @Test
    fun `save then load round-trips the full profile`() {
        val memory = memory()
        memory.initSchema()
        val profile = LearnerProfile(
            learnerId = "learner-1",
            formationId = "formation-A",
            completedModules = listOf("mod-1", "mod-2"),
            currentModule = "mod-3",
            progressionPct = 50.0,
            comprehensionScore = 72.0,
            weakPoints = listOf("gradients", "backprop"),
            lastInteractionAt = "2026-08-21T10:00:00Z",
            annotations = mapOf("mod-3" to "struggled with regularization")
        )
        memory.save(profile)

        val loaded = memory.load("learner-1", "formation-A")
        assertNotNull(loaded, "profile should be loaded after save")
        assertEquals("learner-1", loaded!!.learnerId)
        assertEquals("formation-A", loaded.formationId)
        assertEquals(listOf("mod-1", "mod-2"), loaded.completedModules)
        assertEquals("mod-3", loaded.currentModule)
        assertEquals(50.0, loaded.progressionPct)
        assertEquals(72.0, loaded.comprehensionScore)
        assertEquals(listOf("gradients", "backprop"), loaded.weakPoints)
        assertEquals("2026-08-21T10:00:00Z", loaded.lastInteractionAt)
        assertEquals(mapOf("mod-3" to "struggled with regularization"), loaded.annotations)
    }

    @Test
    fun `save is an upsert — second save overwrites the first`() {
        val memory = memory()
        memory.initSchema()
        val first = LearnerProfile(
            learnerId = "learner-2",
            formationId = "formation-B",
            weakPoints = listOf("gradients")
        )
        memory.save(first)

        val second = LearnerProfile(
            learnerId = "learner-2",
            formationId = "formation-B",
            weakPoints = listOf("backprop", "regularization"),
            progressionPct = 30.0
        )
        memory.save(second)

        val loaded = memory.load("learner-2", "formation-B")
        assertNotNull(loaded)
        assertEquals(listOf("backprop", "regularization"), loaded!!.weakPoints)
        assertEquals(30.0, loaded.progressionPct)
    }

    @Test
    fun `load returns null for unknown composite key`() {
        val memory = memory()
        memory.initSchema()
        val loaded = memory.load("unknown-learner", "unknown-formation")
        assertNull(loaded, "load should return null for unknown key")
    }

    @Test
    fun `composite key — same learner different formations are distinct profiles`() {
        val memory = memory()
        memory.initSchema()
        memory.save(LearnerProfile("learner-3", "formation-X", weakPoints = listOf("alpha")))
        memory.save(LearnerProfile("learner-3", "formation-Y", weakPoints = listOf("beta")))

        val x = memory.load("learner-3", "formation-X")
        val y = memory.load("learner-3", "formation-Y")
        assertNotNull(x); assertNotNull(y)
        assertEquals(listOf("alpha"), x!!.weakPoints)
        assertEquals(listOf("beta"), y!!.weakPoints)
    }

    @Test
    fun `save with empty profile does not throw`() {
        val memory = memory()
        memory.initSchema()
        val profile = LearnerProfile("learner-empty", "formation-empty")
        memory.save(profile)
        val loaded = memory.load("learner-empty", "formation-empty")
        assertNotNull(loaded)
        assertTrue(loaded!!.weakPoints.isEmpty())
        assertTrue(loaded.annotations.isEmpty())
    }
}