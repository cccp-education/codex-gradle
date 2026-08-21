package codex.profile

import codex.tasks.PersistLearnerProfileTask
import contracts.runtime.LearnerProfile
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * TDD — EPIC CDX-RC-04-3 : tâche Gradle `persistLearnerProfile` + wiring
 * CodexPlugin.
 *
 * Valide le thin wrapper Gradle autour de [PgVectorSessionMemory]. La tâche
 * lit un profil JSON (format `LearnerProfile` sérialisé), persiste le profil
 * en pgvector via le pont RAG `SessionMemoryContract`.
 *
 * Pattern `CodexIngestRetrieveIT` — `@Tag("integration")` + Testcontainers.
 */
@Tag("integration")
class PersistLearnerProfileTaskIT {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("codex").withUsername("codex").withPassword("codex")

        @JvmStatic @BeforeAll fun start() { postgres.start() }
        @JvmStatic @AfterAll fun stop() { postgres.stop() }
    }

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())

    @Test
    fun `persistLearnerProfile task persists a profile JSON to pgvector`(@TempDir tempDir: Path) {
        val profileFile = tempDir.resolve("profile.json").toFile()
        val profile = LearnerProfile(
            learnerId = "task-learner-1",
            formationId = "formation-task",
            weakPoints = listOf("gradients", "backprop"),
            annotations = mapOf("mod-3" to "struggled with regularization"),
            progressionPct = 40.0,
            comprehensionScore = 60.0
        )
        profileFile.writeText(mapper.writeValueAsString(profile))

        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        val task = project.tasks.register("persistLearnerProfile", PersistLearnerProfileTask::class.java).get().also {
            it.profileFile.set(profileFile)
            it.pgHost.set(postgres.host)
            it.pgPort.set(postgres.firstMappedPort.toString())
            it.pgDatabase.set(postgres.databaseName)
            it.pgUser.set(postgres.username)
            it.pgPassword.set(postgres.password)
        }
        task.persist()

        val memory = PgVectorSessionMemory(
            ProfileConfig(
                host = postgres.host,
                port = postgres.firstMappedPort,
                database = postgres.databaseName,
                username = postgres.username,
                password = postgres.password
            )
        )
        val loaded = memory.load("task-learner-1", "formation-task")
        assertNotNull(loaded)
        assertEquals(listOf("gradients", "backprop"), loaded!!.weakPoints)
        assertEquals(40.0, loaded.progressionPct)
        assertEquals(mapOf("mod-3" to "struggled with regularization"), loaded.annotations)
    }

    @Test
    fun `task is registered in codex-memory group by CodexPlugin`(@TempDir tempDir: Path) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.pluginManager.apply("education.cccp.codex")
        val task = project.tasks.findByName("persistLearnerProfile")
        assertNotNull(task, "persistLearnerProfile should be registered by CodexPlugin")
        assertEquals("codex-memory", task!!.group, "task group should be codex-memory")
    }
}