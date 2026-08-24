package codex.profile

import contracts.runtime.LearnerProfile
import contracts.runtime.SessionMemoryContract
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import reactor.core.publisher.Mono

/**
 * EPIC CDX-RC-04-2 — Implémentation privée du pont RAG `SessionMemoryContract`.
 *
 * Consomme le contrat N0 `SessionMemoryContract` (MEMPHIS `runtime-contracts`)
 * et l'implémente côté codex N2 via pgvector. Le profil stagiaire est persisté
 * dans une table dédiée `codex_learner_profiles` (séparée du corpus
 * documentaire — voir [ProfileStatements.initSchema]) avec un embedding
 * 384-dim ONNX calculé depuis les weakPoints + annotations (voir
 * [ProfileEmbedding]).
 *
 * Pattern `RagVectorStore` (codebase.store) / `CodexIngestTask` :
 * - ONNX `AllMiniLmL6V2EmbeddingModel` pour l'embedding (lazy).
 * - R2DBC `PostgresqlConnectionFactory` pour la persistance.
 * - `runBlocking` sync wrappers pour usage depuis tâche Gradle.
 *
 * Le contrat N0 est OSS (Apache 2.0), l'implémentation du pont RAG est OSS
 * (codex Apache 2.0). Pas de code propriétaire ici — la frontière licence
 * 3 étages place l'orchestration agentique privée dans workspace-gradle,
 * pas dans codex.
 *
 * @property config configuration de connexion pgvector
 */
class PgVectorSessionMemory(
    private val config: ProfileConfig = ProfileConfig()
) : SessionMemoryContract {

    private val model: AllMiniLmL6V2EmbeddingModel by lazy { AllMiniLmL6V2EmbeddingModel() }
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Initialise le schéma `codex_learner_profiles` si nécessaire.
     * Idempotent — safe à appeler avant chaque save.
     */
    fun initSchema() = runBlocking {
        val factory = createFactory()
        val conn = factory.create().awaitFirst()
        try {
            ProfileStatements.initSchema().forEach { stmt ->
                conn.createStatement(stmt).execute().awaitFirst()
            }
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    override fun save(profile: LearnerProfile) = runBlocking {
        val factory = createFactory()
        val conn = factory.create().awaitFirst()
        try {
            upsertProfile(conn, profile)
            updateEmbedding(conn, profile)
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    override fun load(learnerId: String, formationId: String): LearnerProfile? = runBlocking {
        val factory = createFactory()
        val conn = factory.create().awaitFirst()
        try {
            loadProfile(conn, learnerId, formationId)
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    private fun createFactory(): ConnectionFactory =
        PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(config.host)
                .port(config.port)
                .database(config.database)
                .username(config.username)
                .password(config.password)
                .build()
        )

    private suspend fun upsertProfile(
        conn: io.r2dbc.spi.Connection,
        profile: LearnerProfile
    ) {
        val completedModules = json.encodeToString(
            ListSerializer(String.serializer()), profile.completedModules
        )
        val weakPoints = json.encodeToString(
            ListSerializer(String.serializer()), profile.weakPoints
        )
        val annotations = json.encodeToString(
            MapSerializer(String.serializer(), String.serializer()), profile.annotations
        )
        val stmt = conn.createStatement(ProfileStatements.upsertProfile())
            .bind(0, profile.learnerId)
            .bind(1, profile.formationId)
            .bind(2, completedModules)
        val currentModule = profile.currentModule
        if (currentModule == null) stmt.bindNull(3, String::class.java)
        else stmt.bind(3, currentModule)
        stmt.bind(4, profile.progressionPct)
            .bind(5, profile.comprehensionScore)
            .bind(6, weakPoints)
        val lastInteractionAt = profile.lastInteractionAt
        if (lastInteractionAt == null) stmt.bindNull(7, String::class.java)
        else stmt.bind(7, lastInteractionAt)
        stmt.bind(8, annotations)
        stmt.execute().awaitFirst().rowsUpdated.awaitFirst()
    }

    private suspend fun updateEmbedding(
        conn: io.r2dbc.spi.Connection,
        profile: LearnerProfile
    ) {
        val text = ProfileEmbedding.textToEmbed(profile)
        // Si pas de signal sémantique, on skip l'embedding — économie d'encre.
        // Un profil sans weak points ni annotations n'a pas vocation à être
        // retrouvé par recherche sémantique.
        if (text.isBlank()) return
        val vector = model.embed(TextSegment.from(text)).content().vector()
            .joinToString(",", "", "")
        val sql = ProfileStatements.updateEmbedding(vector, profile.learnerId, profile.formationId)
        conn.createStatement(sql).execute().awaitFirst().rowsUpdated.awaitFirst()
    }

    private suspend fun loadProfile(
        conn: io.r2dbc.spi.Connection,
        learnerId: String,
        formationId: String
    ): LearnerProfile? {
        val result = conn.createStatement(ProfileStatements.selectProfile())
            .bind(0, learnerId)
            .bind(1, formationId)
            .execute()
            .awaitFirst()
        return Mono.from(result.map { row, _ ->
            LearnerProfile(
                learnerId = row.get("learner_id", String::class.java)!!,
                formationId = row.get("formation_id", String::class.java)!!,
                completedModules = json.decodeFromString(
                    ListSerializer(String.serializer()),
                    row.get("completed_modules", String::class.java) ?: "[]"
                ),
                currentModule = row.get("current_module", String::class.java),
                progressionPct = (row.get("progression_pct") as Number).toDouble(),
                comprehensionScore = (row.get("comprehension_score") as Number).toDouble(),
                weakPoints = json.decodeFromString(
                    ListSerializer(String.serializer()),
                    row.get("weak_points", String::class.java) ?: "[]"
                ),
                lastInteractionAt = row.get("last_interaction_at", String::class.java),
                annotations = json.decodeFromString(
                    MapSerializer(String.serializer(), String.serializer()),
                    row.get("annotations", String::class.java) ?: "{}"
                )
            )
        }).awaitFirstOrNull()
    }
}