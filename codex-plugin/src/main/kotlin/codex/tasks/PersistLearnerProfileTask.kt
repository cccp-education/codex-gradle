package codex.tasks

import codex.profile.PgVectorSessionMemory
import codex.profile.ProfileConfig
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.ObjectMapper
import contracts.runtime.LearnerProfile
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * EPIC CDX-RC-04-3 — Tâche Gradle `persistLearnerProfile`.
 *
 * Thin wrapper Gradle autour de [PgVectorSessionMemory]. Lit un profil JSON
 * (format [LearnerProfile] sérialisé via Jackson — le contrat N0 vit dans
 * `runtime-contracts` et n'est pas `@Serializable`), persiste le profil en
 * pgvector via le pont RAG `SessionMemoryContract`.
 *
 * Groupe Gradle : `codex-memory` (mémoire de session, distincte du pipeline
 * documentaire `collect`/`transform`/`deploy`).
 *
 * @property profileFile input JSON profile file (LearnerProfile serialized)
 * @property pgHost PostgreSQL host
 * @property pgPort PostgreSQL port
 * @property pgDatabase PostgreSQL database name
 * @property pgUser PostgreSQL username
 * @property pgPassword PostgreSQL password
 */
@DisableCachingByDefault(because = "pgvector persistence (R2DBC) — external state, non-cacheable")
abstract class PersistLearnerProfileTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val profileFile: RegularFileProperty

    @get:Input abstract val pgHost: Property<String>
    @get:Input abstract val pgPort: Property<String>
    @get:Input abstract val pgDatabase: Property<String>
    @get:Input abstract val pgUser: Property<String>
    @get:Input abstract val pgPassword: Property<String>

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())

    @TaskAction
    fun persist() {
        val input = profileFile.asFile.get()
        val host = pgHost.get()
        val port = pgPort.get().toInt()
        val db = pgDatabase.get()
        val user = pgUser.get()
        val pass = pgPassword.get()

        logger.lifecycle("[codex] persistLearnerProfile : ${input.name} → pgvector ($host:$port/$db)")

        val profile = mapper.readValue(input, LearnerProfile::class.java)

        val memory = PgVectorSessionMemory(
            ProfileConfig(host = host, port = port, database = db, username = user, password = pass)
        )
        memory.initSchema()
        memory.save(profile)

        logger.lifecycle(
            "[codex] ✓ persistLearnerProfile — profile ${profile.learnerId}/${profile.formationId} persisted"
        )
    }
}