package codex.tasks

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.PostgreSQLContainer
import java.nio.file.Path
import kotlin.io.path.writeText

@Tag("integration")
class CodexIngestRetrieveIT {

    companion object {
        private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("codex").withUsername("codex").withPassword("codex")

        @JvmStatic @BeforeAll fun start() { postgres.start() }
        @JvmStatic @AfterAll fun stop() { postgres.stop() }

        fun host() = postgres.host
        fun port() = postgres.firstMappedPort
        fun db() = postgres.databaseName
        fun user() = postgres.username
        fun pass() = postgres.password
    }

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    private fun makeIngestTask(project: org.gradle.api.Project, chunksFile: java.io.File, batch: String = "32") =
        project.tasks.register("collectIngest", CodexIngestTask::class.java).get().also {
            it.chunksFile.set(chunksFile); it.pgHost.set(host()); it.pgPort.set(port().toString())
            it.pgDatabase.set(db()); it.pgUser.set(user()); it.pgPassword.set(pass())
            it.batchSize.set(batch)
        }

    private fun makeRetrieveTask(project: org.gradle.api.Project, query: String, topK: String, output: java.io.File) =
        project.tasks.register("collectRetrieve", CodexRetrieveTask::class.java).get().also {
            it.query.set(query); it.topK.set(topK)
            it.pgHost.set(host()); it.pgPort.set(port().toString())
            it.pgDatabase.set(db()); it.pgUser.set(user()); it.pgPassword.set(pass())
            it.outputFile.set(output)
        }

    @Test
    fun `end-to-end ingest and retrieve`(@TempDir tempDir: Path) {
        val chunksFile = tempDir.resolve("chunks.json").toFile()
        val chunks = listOf(
            DocumentChunk("chk-001", "test-book", "Ch1", 1, "Machine learning fundamentals.", license = "Apache-2.0"),
            DocumentChunk("chk-002", "test-book", "Ch2", 1, "Deep neural network architectures.", license = "Apache-2.0"),
            DocumentChunk("chk-003", "test-book", "Ch3", 1, "French cuisine recipes.", license = "Apache-2.0")
        )
        chunksFile.writeText(json.encodeToString(ListSerializer(DocumentChunk.serializer()), chunks))

        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        makeIngestTask(project, chunksFile, "2").ingest()

        val outputFile = tempDir.resolve("retrieve.json").toFile()
        makeRetrieveTask(project, "machine learning", "3", outputFile).retrieve()

        assertTrue(outputFile.exists())
        val results = Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<List<RetrieveResult>>(outputFile.readText())
        assertFalse(results.isEmpty(), "Should return results but got ${results.size}")
    }

    @Test
    fun `ingest empty chunks`(@TempDir tempDir: Path) {
        val f = tempDir.resolve("empty.json").toFile(); f.writeText("[]")
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        makeIngestTask(project, f).ingest()
    }

    @Test
    fun `batching with batch size 1`(@TempDir tempDir: Path) {
        val chunksFile = tempDir.resolve("batch.json").toFile()
        val chunks = (1..3).map { i -> DocumentChunk("chk-b$i", "batch-test", "S$i", 1, "Content $i.", license = "Apache-2.0") }
        chunksFile.writeText(json.encodeToString(ListSerializer(DocumentChunk.serializer()), chunks))

        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        makeIngestTask(project, chunksFile, "1").ingest()

        val outputFile = tempDir.resolve("batch-retrieve.json").toFile()
        makeRetrieveTask(project, "Content", "3", outputFile).retrieve()
        val results = Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<List<RetrieveResult>>(outputFile.readText())
        assertEquals(3, results.size, "Expected 3 results, got ${results.size}")
    }

    @Test
    fun `codexPipeline auto-detects PDF`(@TempDir tempDir: Path) {
        // Mini PDF valide avec 1 page vide (base64 encodé)
        val pdfBytes = java.util.Base64.getDecoder().decode(
            "JVBERi0xLjQKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PgplbmRvYmoK" +
            "MiAwIG9iago8PC9UeXBlL1BhZ2VzL0tpZHNbMyAwIFJdL0NvdW50IDE+PgplbmRvYmoK" +
            "MyAwIG9iago8PC9UeXBlL1BhZ2UvUGFyZW50IDIgMCBSL01lZGlhQm94WzAgMCA2MTIg" +
            "NzkyXT4+CmVuZG9iagp4cmVmIDAgNAowMDAwMDAwMDA5IDY1NTM1IGYgCjAwMDAwMDAw" +
            "NTIgMDAwMDAgbiAKMDAwMDAwMDExMCAwMDAwMCBuIAowMDAwMDAwMTc0IDAwMDAwIG4g" +
            "CnRyYWlsZXIKPDwvU2l6ZTQvUm9vdCAxIDAgUj4+CnN0YXJ0eHJlZgoyNzkKJSVFT0Y="
        )
        val pdfFile = tempDir.resolve("doc.pdf").toFile(); pdfFile.writeBytes(pdfBytes)
        val outputFile = tempDir.resolve("pipeline.adoc").toFile()
        val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.tasks.register("transformCorpusToPdf", CodexPipelineTask::class.java).get().also {
            it.sourceFile.set(pdfFile); it.outputFile.set(outputFile); it.licenseName.set("Apache-2.0")
            it.pgHost.set(host()); it.pgPort.set(port().toString())
            it.pgDatabase.set(db()); it.pgUser.set(user()); it.pgPassword.set(pass())
        }.pipeline()
        assertTrue(outputFile.exists())
    }
}
