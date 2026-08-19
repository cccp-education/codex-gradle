package codex.bdd

import codex.ontology.OntologyMapping
import codex.tasks.DeriveOntologyTask
import codex.tasks.LddNode
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * Cucumber steps for `codex_derive_ontology.feature` (CDX-3-3).
 *
 * Drives [codex.tasks.DeriveOntologyTask] end-to-end through Gradle's
 * `ProjectBuilder` — mirrors the functional test
 * [codex.tasks.DeriveOntologyTaskTest] but expressed as BDD scenarios.
 * No production code is exercised beyond the task wiring already
 * validated in S-084; this is pure behaviour validation.
 */
class OntologySteps {

    private lateinit var tempDir: File
    private lateinit var sqlFile: File
    private lateinit var lddFile: File
    private lateinit var outputFile: File
    private var mapping: OntologyMapping? = null

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

    @Given("a SQL DDL script with tables linked by foreign keys")
    fun aSqlDdlScriptWithTablesLinkedByForeignKeys() {
        tempDir = kotlin.io.path.createTempDirectory(prefix = "cdx-ontology-bdd-").toFile()
        sqlFile = File(tempDir, "book.sql").apply { writeText(bookSql) }
    }

    @Given("a JSON LDD file with a section title {string}")
    fun aJsonLddFileWithASectionTitle(title: String) {
        val lddNodes = listOf(LddNode(title = title, level = 1))
        lddFile = File(tempDir, "book.ldd.json").apply {
            writeText(Json.encodeToString(ListSerializer(LddNode.serializer()), lddNodes))
        }
        outputFile = File(tempDir, "ontology.json")
    }

    @When("the derive ontology task runs")
    fun theDeriveOntologyTaskRuns() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "deriveOntology",
            DeriveOntologyTask::class.java
        ).get()
        task.sqlFile.set(sqlFile)
        task.jsonFile.set(lddFile)
        task.outputFile.set(outputFile)
        task.derive()
        mapping = parseMapping(outputFile)
    }

    @Then("the ontology mapping contains a bounded context named {string}")
    fun theOntologyMappingContainsABoundedContextNamed(name: String) {
        val m = mapping ?: error("Task did not run")
        val ctx = m.boundedContexts.find { it.name == name }
        assertNotNull(ctx, "Expected a bounded context named '$name', got: ${m.boundedContexts.map { it.name }}")
    }

    @Then("the bounded context contains the tables {string}, {string} and {string}")
    fun theBoundedContextContainsTheTablesAnd(t1: String, t2: String, t3: String) {
        val m = mapping ?: error("Task did not run")
        val allTables = m.boundedContexts.flatMap { it.tables }.toSet()
        assertTrue(t1 in allTables, "Expected '$t1' in bounded context tables")
        assertTrue(t2 in allTables, "Expected '$t2' in bounded context tables")
        assertTrue(t3 in allTables, "Expected '$t3' in bounded context tables")
    }

    @Then("the ontology mapping contains an aggregate with root table {string} and dependent {string}")
    fun theOntologyMappingContainsAnAggregateWithRootTableAndDependent(root: String, dependent: String) {
        val m = mapping ?: error("Task did not run")
        val agg = m.aggregates.find { it.rootTable == root }
        assertNotNull(agg, "Expected an aggregate with root '$root', got: ${m.aggregates.map { it.rootTable }}")
        assertTrue(
            dependent in (agg?.dependentTables ?: emptyList()),
            "Expected dependent '$dependent' in aggregate '$root', got: ${agg?.dependentTables}"
        )
    }

    @Then("the ontology mapping contains a value object for table {string}")
    fun theOntologyMappingContainsAValueObjectForTable(table: String) {
        val m = mapping ?: error("Task did not run")
        val vo = m.valueObjects.find { it.table == table }
        assertNotNull(vo, "Expected a value object for table '$table', got: ${m.valueObjects.map { it.table }}")
    }

    @Then("the value object {string} has the columns {string}, {string} and {string}")
    fun theValueObjectHasTheColumnsAnd(table: String, c1: String, c2: String, c3: String) {
        val m = mapping ?: error("Task did not run")
        val vo = m.valueObjects.find { it.table == table }
            ?: error("Value object '$table' not found")
        val cols = vo.columns
        assertTrue(c1 in cols, "Expected column '$c1' in '$table', got: $cols")
        assertTrue(c2 in cols, "Expected column '$c2' in '$table', got: $cols")
        assertTrue(c3 in cols, "Expected column '$c3' in '$table', got: $cols")
    }

    private fun parseMapping(file: File): OntologyMapping {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(OntologyMapping.serializer(), file.readText())
    }
}