package codex.tasks

import codebase.store.DocumentChunk
import codebase.store.StoreStatements
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Vectorizes document chunks with ONNX AllMiniLmL6V2 and stores them in pgvector.
 *
 * EPIC CDX-RAG-3: thin wrapper consuming the N1 socle
 * [codebase.store.StoreStatements] SQL templates and
 * [codebase.store.DocumentChunk] type. The R2DBC orchestration (embedding +
 * UPSERT) stays here — the store owns the schema, the task owns the Gradle
 * wiring. Tables `codex_documents` / `codex_chunks` are unchanged (full
 * backward compat, zero re-vectorisation — Ink Economy Law).
 *
 * @property chunksFile input JSON chunks file
 * @property pgHost PostgreSQL host
 * @property pgPort PostgreSQL port
 * @property pgDatabase PostgreSQL database name
 * @property pgUser PostgreSQL username
 * @property pgPassword PostgreSQL password
 * @property batchSize number of chunks per batch (default: 32)
 */
@DisableCachingByDefault(because = "ONNX embeddings + pgvector (R2DBC) — external dependencies, non-cacheable")
abstract class CodexIngestTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val chunksFile: RegularFileProperty
    @get:Input abstract val pgHost: Property<String>
    @get:Input abstract val pgPort: Property<String>
    @get:Input abstract val pgDatabase: Property<String>
    @get:Input abstract val pgUser: Property<String>
    @get:Input abstract val pgPassword: Property<String>
    @get:Optional @get:Input abstract val batchSize: Property<String>

    private val model: AllMiniLmL6V2EmbeddingModel by lazy { AllMiniLmL6V2EmbeddingModel() }

    @TaskAction
    fun ingest() = runBlocking {
        val input = chunksFile.asFile.get()
        val host = pgHost.get(); val port = pgPort.get().toInt()
        val db = pgDatabase.get(); val user = pgUser.get(); val pass = pgPassword.get()

        logger.lifecycle("[codex] collectIngest : ${input.name} → pgvector ($host:$port/$db)")

        val json = Json { ignoreUnknownKeys = true }
        val chunks = json.decodeFromString<List<DocumentChunk>>(input.readText())

        val factory = createFactory(host, port, db, user, pass)
        val conn = factory.create().awaitFirst()
        try {
            initSchema(factory)
            val docCount = ingestChunks(conn, chunks)
            logger.lifecycle("[codex] ✓ collectIngest — $docCount docs, ${chunks.size} chunks")
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    private fun createFactory(host: String, port: Int, db: String, user: String, pass: String): ConnectionFactory =
        PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(host).port(port).database(db).username(user).password(pass).build()
        )

    private suspend fun initSchema(factory: ConnectionFactory) {
        val conn = factory.create().awaitFirst()
        try {
            StoreStatements.initSchema().forEach { conn.createStatement(it).execute().awaitFirst() }
        } finally { conn.close().awaitFirstOrNull() }
    }

    private suspend fun ingestChunks(conn: io.r2dbc.spi.Connection, chunks: List<DocumentChunk>): Int {
        val groups = chunks.groupBy { it.sourceDocument }
        val effectiveBatchSize = batchSize.orNull?.toIntOrNull() ?: 32
        var docCount = 0

        for ((source, docChunks) in groups) {
            val license = docChunks.first().license
            val docId = conn.createStatement(StoreStatements.insertDocument())
                .bind(0, source).bind(1, docChunks.size).bind(2, license)
                .execute().awaitFirst().map { r, _ -> r.get("id", Long::class.java)!! }.awaitFirst()

            logger.lifecycle("[codex]   $source (${docChunks.size} chunks, batch=$effectiveBatchSize)")

            var stored = 0
            // CDX-CR3-3: local counter per document instead of chunks.indexOf(chunk)
            // which returns the first occurrence for duplicated chunks.
            for ((localIndex, chunk) in docChunks.withIndex()) {
                val chunkId = conn.createStatement(StoreStatements.insertChunk())
                    .bind(0, docId).bind(1, localIndex).bind(2, chunk.content)
                    .bind(3, chunk.sectionPath).bind(4, chunk.headingLevel)
                    .execute().awaitFirst().map { r, _ -> r.get("id", Long::class.java)!! }.awaitFirst()

                val vec = computeEmbedding(chunk.content)
                // CDX-CR3-1: the chunkId (Long produced by PostgreSQL via
                // RETURNING id) and the vector (ONNX numbers) are safe to
                // inline — no user input. R2DBC/pgvector does not support
                // parameterised vector binding in a SET context (limitation
                // documented in StoreStatements).
                conn.createStatement(StoreStatements.updateEmbedding(vec, chunkId))
                    .execute().awaitFirst()
                stored++
            }
            docCount++
            logger.lifecycle("[codex]   ✓ $stored embeddings")
        }
        return docCount
    }

    private fun computeEmbedding(text: String): String {
        val v = model.embed(TextSegment.from(text)).content().vector()
        return v.joinToString(",")
    }
}
