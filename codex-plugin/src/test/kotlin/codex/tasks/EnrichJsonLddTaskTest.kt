package codex.tasks

import codex.enrichment.EnrichedLddNode
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
 * TDD — EPIC CDX-4-2 : Tâche Gradle `enrichJsonLdd` (groupe transform) +
 * wiring [CodexPlugin].
 *
 * `EnrichJsonLddTask` consomme le JSON LDD produit par
 * [AsciiDocToJsonLddTask], les chunks RAG produits par [ChunkDocumentTask]
 * et le `graph.json` produit par graphify-gradle (N0), appelle
 * `JsonLddEnricher.enrich(lddNodes, ragChunks, graphifyResolver)` et écrit
 * la liste des [EnrichedLddNode] en JSON.
 *
 * Baby-step TDD strict RED (tâche inexistante) → GREEN → REFACTOR.
 */
class EnrichJsonLddTaskTest {

    @TempDir
    lateinit var tempDir: File

    private val lddJson = """
        [
          {"title":"Architecture","level":1,"type":null,"text":null,"children":[]},
          {"title":"Testing","level":1,"type":null,"text":null,"children":[]}
        ]
    """.trimIndent()

    private val chunksJson = """
        [
          {"id":"chk-1","sourceDocument":"book","sectionPath":"Chapter 1 > Architecture",
           "headingLevel":2,"content":"Some content","codeBlocks":[],"entities":[],
           "overlapNext":null,"license":"UNKNOWN"},
          {"id":"chk-2","sourceDocument":"book","sectionPath":"Chapter 1 > Testing",
           "headingLevel":2,"content":"Other content","codeBlocks":[],"entities":[],
           "overlapNext":null,"license":"UNKNOWN"}
        ]
    """.trimIndent()

    private val graphJson = """
        {
          "nodes":[
            {"id":"node-arch","label":"Architecture","type":"section"},
            {"id":"node-test","label":"Testing","type":"section"}
          ],
          "edges":[],
          "communities":[]
        }
    """.trimIndent()

    @Test
    fun `enrichJsonLdd task is registered with transform group`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("education.cccp.codex")

        val task = project.tasks.findByName("enrichJsonLdd")
        assertNotNull(task, "enrichJsonLdd task must be registered by CodexPlugin")
        assertEquals("transform", task?.group, "enrichJsonLdd must belong to transform group")
    }

    @Test
    fun `enrichJsonLdd writes enriched JSON from LDD + chunks + graph`() {
        val lddFile = File(tempDir, "book.ldd.json").apply { writeText(lddJson) }
        val chunksFile = File(tempDir, "book.chunks.json").apply { writeText(chunksJson) }
        val graphFile = File(tempDir, "graph.json").apply { writeText(graphJson) }
        val outputFile = File(tempDir, "enriched.json")

        val task = createTask(lddFile, chunksFile, graphFile, outputFile)
        task.enrich()

        assertTrue(outputFile.exists(), "enriched JSON must be written")
        val enriched = parseEnriched(outputFile)
        assertEquals(2, enriched.size, "one EnrichedLddNode per LDD section")
    }

    @Test
    fun `enrichJsonLdd attaches rag chunks matching section title`() {
        val lddFile = File(tempDir, "book.ldd.json").apply { writeText(lddJson) }
        val chunksFile = File(tempDir, "book.chunks.json").apply { writeText(chunksJson) }
        val graphFile = File(tempDir, "graph.json").apply { writeText(graphJson) }
        val outputFile = File(tempDir, "enriched.json")

        val task = createTask(lddFile, chunksFile, graphFile, outputFile)
        task.enrich()

        val enriched = parseEnriched(outputFile)
        val arch = enriched.first { it.ldd.title == "Architecture" }
        assertEquals(1, arch.ragChunks.size, "Architecture section should have 1 matching chunk")
        assertEquals("chk-1", arch.ragChunks.first().id)
    }

    @Test
    fun `enrichJsonLdd resolves graphify nodes from graph json`() {
        val lddFile = File(tempDir, "book.ldd.json").apply { writeText(lddJson) }
        val chunksFile = File(tempDir, "book.chunks.json").apply { writeText(chunksJson) }
        val graphFile = File(tempDir, "graph.json").apply { writeText(graphJson) }
        val outputFile = File(tempDir, "enriched.json")

        val task = createTask(lddFile, chunksFile, graphFile, outputFile)
        task.enrich()

        val enriched = parseEnriched(outputFile)
        val arch = enriched.first { it.ldd.title == "Architecture" }
        assertTrue(arch.graphifyNodes.contains("node-arch"),
            "graphify node 'node-arch' should be resolved for Architecture section")
    }

    @Test
    fun `enrichJsonLdd degrades silently when graph json is missing`() {
        val lddFile = File(tempDir, "book.ldd.json").apply { writeText(lddJson) }
        val chunksFile = File(tempDir, "book.chunks.json").apply { writeText(chunksJson) }
        val missingGraph = File(tempDir, "missing-graph.json")
        val outputFile = File(tempDir, "enriched.json")

        val task = createTask(lddFile, chunksFile, missingGraph, outputFile)
        task.enrich()

        val enriched = parseEnriched(outputFile)
        assertTrue(enriched.all { it.graphifyNodes.isEmpty() },
            "missing graph.json → no graphify nodes (degraded silent)")
    }

    @Test
    fun `enrichJsonLdd computes semantic density for each section`() {
        val lddFile = File(tempDir, "book.ldd.json").apply { writeText(lddJson) }
        val chunksFile = File(tempDir, "book.chunks.json").apply { writeText(chunksJson) }
        val graphFile = File(tempDir, "graph.json").apply { writeText(graphJson) }
        val outputFile = File(tempDir, "enriched.json")

        val task = createTask(lddFile, chunksFile, graphFile, outputFile)
        task.enrich()

        val enriched = parseEnriched(outputFile)
        val arch = enriched.first { it.ldd.title == "Architecture" }
        assertEquals(0.5, arch.semanticDensity, 0.0001,
            "1 matching chunk out of 2 total → density 0.5")
    }

    private fun createTask(
        lddFile: File,
        chunksFile: File,
        graphifyFile: File,
        outputFile: File
    ): EnrichJsonLddTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "enrichJsonLdd",
            EnrichJsonLddTask::class.java
        ).get()
        task.jsonFile.set(lddFile)
        task.chunksFile.set(chunksFile)
        task.graphifyFile.set(graphifyFile)
        task.outputFile.set(outputFile)
        return task
    }

    private fun parseEnriched(file: File): List<EnrichedLddNode> {
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(
            ListSerializer(EnrichedLddNode.serializer()),
            file.readText()
        )
    }
}