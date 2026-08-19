package codex.tasks

import codex.ontology.OntologyMapping
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * TDD — EPIC CDX-3-2 : Tâche Gradle `deriveOntology` (groupe transform) +
 * wiring [CodexPlugin].
 *
 * `DeriveOntologyTask` consomme le SQL LMD généré par [ImportBookSqlTask]
 * et les LDD nodes produits par [AsciiDocToJsonLddTask], appelle
 * `OntologyDeriver.derive(lddNodes, sqlScript)` et écrit le
 * [OntologyMapping] résultat en JSON.
 *
 * Baby-step TDD strict RED (tâche inexistante) → GREEN → REFACTOR.
 */
class DeriveOntologyTaskTest {

    @TempDir
    lateinit var tempDir: File

    private val bookSql = """
        -- codex: collectBookSql
        CREATE TABLE IF NOT EXISTS book (
            id          SERIAL PRIMARY KEY,
            title       TEXT NOT NULL,
            created_at  TIMESTAMP DEFAULT now()
        );

        CREATE TABLE IF NOT EXISTS documents (
            id          SERIAL PRIMARY KEY,
            book_id     INTEGER REFERENCES book(id) ON DELETE CASCADE,
            title       TEXT NOT NULL,
            level       INTEGER NOT NULL DEFAULT 0,
            parent_id   INTEGER REFERENCES documents(id) ON DELETE CASCADE,
            sort_order  INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS paragraphs (
            id          SERIAL PRIMARY KEY,
            doc_id      INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
            text        TEXT NOT NULL,
            position    INTEGER NOT NULL DEFAULT 0
        );
    """.trimIndent()

    @Test
    fun `deriveOntology task is registered with transform group`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("education.cccp.codex")

        val task = project.tasks.findByName("deriveOntology")
        assertNotNull(task, "deriveOntology task must be registered by CodexPlugin")
        assertEquals("transform", task?.group, "deriveOntology must belong to transform group")
    }

    @Test
    fun `deriveOntology writes ontology mapping JSON from SQL DDL`() {
        val sqlFile = File(tempDir, "book.sql").apply { writeText(bookSql) }
        val lddFile = File(tempDir, "book.ldd.json").apply {
            writeText(Json.encodeToString(ListSerializer(LddNode.serializer()), emptyList()))
        }
        val outputFile = File(tempDir, "ontology.json")

        val task = createTask(sqlFile, lddFile, outputFile)
        task.derive()

        assertTrue(outputFile.exists(), "ontology mapping JSON must be written")
        val mapping = parseMapping(outputFile)
        val allTables = mapping.boundedContexts.flatMap { it.tables }.toSet()
        assertTrue("book" in allTables, "book table should appear in bounded contexts")
        assertTrue("documents" in allTables, "documents table should appear")
        assertTrue("paragraphs" in allTables, "paragraphs table should appear")
    }

    @Test
    fun `deriveOntology uses LDD title to name bounded context`() {
        val sqlFile = File(tempDir, "book.sql").apply { writeText(bookSql) }
        val lddNodes = listOf(LddNode(title = "Applied AI for Enterprise Java", level = 1))
        val lddFile = File(tempDir, "book.ldd.json").apply {
            writeText(Json.encodeToString(ListSerializer(LddNode.serializer()), lddNodes))
        }
        val outputFile = File(tempDir, "ontology.json")

        val task = createTask(sqlFile, lddFile, outputFile)
        task.derive()

        val mapping = parseMapping(outputFile)
        assertEquals(1, mapping.boundedContexts.size)
        val ctx = mapping.boundedContexts.first()
        assertTrue(ctx.name.isNotBlank(), "context name should derive from LDD title or fallback")
    }

    @Test
    fun `deriveOntology with empty SQL produces empty mapping`() {
        val sqlFile = File(tempDir, "empty.sql").apply { writeText("-- nothing") }
        val lddFile = File(tempDir, "empty.ldd.json").apply {
            writeText(Json.encodeToString(ListSerializer(LddNode.serializer()), emptyList()))
        }
        val outputFile = File(tempDir, "ontology.json")

        val task = createTask(sqlFile, lddFile, outputFile)
        task.derive()

        val mapping = parseMapping(outputFile)
        assertTrue(mapping.boundedContexts.isEmpty(), "empty SQL → no bounded contexts")
        assertTrue(mapping.aggregates.isEmpty(), "empty SQL → no aggregates")
        assertTrue(mapping.valueObjects.isEmpty(), "empty SQL → no value objects")
    }

    private fun createTask(
        sqlFile: File,
        lddFile: File,
        outputFile: File
    ): DeriveOntologyTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "deriveOntology",
            DeriveOntologyTask::class.java
        ).get()
        task.sqlFile.set(sqlFile)
        task.jsonFile.set(lddFile)
        task.outputFile.set(outputFile)
        return task
    }

    private fun parseMapping(file: File): OntologyMapping {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(OntologyMapping.serializer(), file.readText())
    }
}