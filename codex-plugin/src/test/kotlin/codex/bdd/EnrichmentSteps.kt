package codex.bdd

import codex.enrichment.EnrichedLddNode
import codex.tasks.EnrichJsonLddTask
import io.cucumber.java.en.And
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
 * Cucumber steps for `codex_enrich_json_ldd.feature` (CDX-4-4).
 *
 * Drives [codex.tasks.EnrichJsonLddTask] end-to-end through Gradle's
 * `ProjectBuilder` — mirrors the functional test
 * [codex.tasks.EnrichJsonLddTaskTest] but expressed as BDD scenarios.
 * No production code is exercised beyond the task wiring already
 * validated in S-086; this is pure behaviour validation.
 *
 * The Background sets up the LDD, chunks and graph json files. Scenarios
 * 2 and 3 override the graph json file (missing / invalid) after the
 * Background to exercise the degraded-silent path of
 * [codex.enrichment.GraphifyFileResolver].
 */
class EnrichmentSteps {

    private lateinit var tempDir: File
    private lateinit var lddFile: File
    private lateinit var chunksFile: File
    private var graphFile: File? = null
    private var firstOutput: File? = null
    private var secondOutput: File? = null

    @Given("a JSON LDD file with sections {string} and {string}")
    fun aJsonLddFileWithSectionsAnd(s1: String, s2: String) {
        tempDir = kotlin.io.path.createTempDirectory(prefix = "cdx-enrich-bdd-").toFile()
        val lddJson = """
            [
              {"title":"$s1","level":1,"type":null,"text":null,"children":[]},
              {"title":"$s2","level":1,"type":null,"text":null,"children":[]}
            ]
        """.trimIndent()
        lddFile = File(tempDir, "book.ldd.json").apply { writeText(lddJson) }
    }

    @Given("a chunks file with one chunk in section {string} and one chunk in section {string}")
    fun aChunksFileWithOneChunkInSectionAndOneChunkInSection(s1: String, s2: String) {
        val chunksJson = """
            [
              {"id":"chk-1","sourceDocument":"book","sectionPath":"Chapter 1 > $s1",
               "headingLevel":2,"content":"Some content","codeBlocks":[],"entities":[],
               "overlapNext":null,"license":"UNKNOWN"},
              {"id":"chk-2","sourceDocument":"book","sectionPath":"Chapter 1 > $s2",
               "headingLevel":2,"content":"Other content","codeBlocks":[],"entities":[],
               "overlapNext":null,"license":"UNKNOWN"}
            ]
        """.trimIndent()
        chunksFile = File(tempDir, "book.chunks.json").apply { writeText(chunksJson) }
    }

    @Given("a graph json file with nodes labelled {string} and {string}")
    fun aGraphJsonFileWithNodesLabelledAnd(s1: String, s2: String) {
        val graphJson = """
            {
              "nodes":[
                {"id":"node-arch","label":"$s1","type":"section"},
                {"id":"node-test","label":"$s2","type":"section"}
              ],
              "edges":[],
              "communities":[]
            }
        """.trimIndent()
        graphFile = File(tempDir, "graph.json").apply { writeText(graphJson) }
    }

    @Given("a missing graph json file")
    fun aMissingGraphJsonFile() {
        graphFile = File(tempDir, "missing-graph.json")
    }

    @Given("an invalid graph json file")
    fun anInvalidGraphJsonFile() {
        graphFile = File(tempDir, "invalid-graph.json").apply { writeText("{ this is not valid json }") }
    }

    @When("the enrich json ldd task runs")
    fun theEnrichJsonLddTaskRuns() {
        firstOutput = File(tempDir, "enriched.json")
        runTask(firstOutput!!)
    }

    @When("the enrich json ldd task runs twice")
    fun theEnrichJsonLddTaskRunsTwice() {
        firstOutput = File(tempDir, "enriched-1.json")
        secondOutput = File(tempDir, "enriched-2.json")
        runTask(firstOutput!!)
        runTask(secondOutput!!)
    }

    @Then("the enriched json contains {int} sections")
    fun theEnrichedJsonContainsSections(count: Int) {
        val enriched = parseEnriched(currentOutput())
        assertEquals(count, enriched.size, "expected $count enriched sections")
    }

    @And("the section {string} has {int} rag chunk attached")
    fun theSectionHasRagChunkAttached(section: String, count: Int) {
        val node = findSection(section)
        assertEquals(count, node.ragChunks.size, "section '$section' should have $count rag chunk(s)")
    }

    @And("the section {string} has the graphify node {string} resolved")
    fun theSectionHasTheGraphifyNodeResolved(section: String, node: String) {
        val s = findSection(section)
        assertTrue(s.graphifyNodes.contains(node),
            "section '$section' should have graphify node '$node' resolved, got: ${s.graphifyNodes}")
    }

    @And("the section {string} has a semantic density of {double}")
    fun theSectionHasASemanticDensityOf(section: String, density: Double) {
        val s = findSection(section)
        assertEquals(density, s.semanticDensity, 0.0001,
            "section '$section' should have semantic density $density")
    }

    @And("the section {string} has extracted entities")
    fun theSectionHasExtractedEntities(section: String) {
        val s = findSection(section)
        assertTrue(s.entities.isNotEmpty(),
            "section '$section' should have extracted entities from its title, got: ${s.entities}")
    }

    @And("every section has no graphify nodes resolved")
    fun everySectionHasNoGraphifyNodesResolved() {
        val enriched = parseEnriched(currentOutput())
        assertTrue(enriched.all { it.graphifyNodes.isEmpty() },
            "every section should have no graphify nodes resolved (degraded silent)")
    }

    @Then("the two enriched outputs are identical")
    fun theTwoEnrichedOutputsAreIdentical() {
        val out1 = firstOutput ?: error("first run did not happen")
        val out2 = secondOutput ?: error("second run did not happen")
        assertEquals(out1.readText(), out2.readText(),
            "enrichment must be idempotent — same inputs yield same output")
    }

    private fun runTask(output: File) {
        val graph = graphFile ?: error("graph json file was not set up")
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "enrichJsonLdd",
            EnrichJsonLddTask::class.java
        ).get()
        task.jsonFile.set(lddFile)
        task.chunksFile.set(chunksFile)
        task.graphifyFile.set(graph)
        task.outputFile.set(output)
        task.enrich()
    }

    private fun currentOutput(): File = firstOutput ?: error("task did not run")

    private fun findSection(title: String): EnrichedLddNode {
        val enriched = parseEnriched(currentOutput())
        return enriched.find { it.ldd.title == title }
            ?: error("section '$title' not found in enriched output: ${enriched.map { it.ldd.title }}")
    }

    private fun parseEnriched(file: File): List<EnrichedLddNode> {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(
            ListSerializer(EnrichedLddNode.serializer()),
            file.readText()
        )
    }
}