package codex.tasks

import codebase.store.RagVectorStore
import codebase.store.RetrieveResult
import codex.enrichment.EnrichedLddNode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * TDD — EPIC CDX-CONTEXT-HARDENING (US-1 + US-2).
 *
 * Second constat of the S-220 dogfooding run: the plugin wires
 * `enrichedJsonFile` **unconditionally** to `build/codex/enriched-ldd.json`
 * while the property was an `@InputFile @Optional` — Gradle validates the
 * input file exists *before* `@TaskAction`, so running
 * `generateCompositeContext` standalone (without a prior `enrichJsonLdd`)
 * failed with `Input file does not exist`. The documented `""` fallback of
 * [CodexCompositeContextTask.buildGraphifySection] was unreachable.
 *
 * US-1 fix: the input becomes a tolerant file collection (`@InputFiles`)
 * — the default wiring is preserved (Graphify channel populated when the
 * artifact exists) but a missing file degrades to the `""` fallback
 * (Économie d'Encre : never force `enrichJsonLdd` to run).
 *
 * US-2: an execution-level suite drives [CodexCompositeContextTask.execute]
 * end-to-end with an injected in-memory store — the exact hole that let the
 * S-220 P0 (`Serializer for class 'Any' is not found`) reach production.
 *
 * Baby-step TDD strict RED -> GREEN -> REFACTOR.
 */
class CodexCompositeContextHardeningTest {

    @TempDir
    lateinit var tempDir: File

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Recording in-memory subclass — no pgvector, no R2DBC, no Docker. */
    private class RecordingRagStore(
        private val results: List<RetrieveResult>,
    ) : RagVectorStore() {
        override suspend fun searchWithDoubt(query: String, topK: Int): List<RetrieveResult> = results
    }

    private fun plainTask() =
        ProjectBuilder.builder().build()
            .tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

    private fun pluggedTask(): CodexCompositeContextTask {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")
        return project.tasks.findByName("generateCompositeContext") as CodexCompositeContextTask
    }

    private fun result(text: String, doubtful: Boolean = false) = RetrieveResult(
        chunkId = 1L, chunkIndex = 0, chunkText = text,
        sectionPath = "Ch1 > Sec", headingLevel = 2, sourceDocument = "book",
        similarity = 0.8, confidence = 1.0, doubtful = doubtful,
    )

    private fun enrichedNode(title: String, vararg nodes: String): EnrichedLddNode =
        EnrichedLddNode(
            ldd = LddNode(title = title, level = 1),
            ragChunks = emptyList(),
            graphifyNodes = nodes.toList(),
            semanticDensity = 0.0,
            entities = emptyList(),
        )

    // ── US-1 : tolerant input wiring ─────────────────────────────────────

    @Test
    fun `the plugin default wiring targets the enrichJsonLdd output location`() {
        val task = pluggedTask()

        val wired = task.enrichedJsonFile.singleOrNull()

        assertTrue(wired != null, "the plugin must wire the enriched JSON input by default")
        assertEquals("enriched-ldd.json", wired!!.name)
        assertEquals("codex", wired.parentFile.name)
    }

    @Test
    fun `resolving the default enriched json collection tolerates a missing file`() {
        val task = pluggedTask()

        // The default artifact does not exist yet — resolving the input
        // collection must not throw (this is what @InputFile used to do).
        val files = task.enrichedJsonFile.files

        assertEquals(1, files.size)
        assertFalse(files.single().exists(), "the default enriched JSON is absent before enrichJsonLdd runs")
    }

    @Test
    fun `buildGraphifySection falls back to empty when the default file does not exist`() {
        val task = pluggedTask()

        assertEquals("", task.buildGraphifySection())
    }

    // ── US-2 : execution-level suite (the hole that let the P0 pass) ─────

    @Test
    fun `execute writes the composite context JSON from the socle retrieval`() {
        val task = plainTask()
        val out = File(tempDir, "out/composite-context.json")
        task.outputFile.set(out)
        task.storeOverride.set(RecordingRagStore(listOf(result("clean section"), result("shaky", doubtful = true))))

        task.execute()

        val root = Json.parseToJsonElement(out.readText()).jsonObject
        assertEquals(2, root["entries"]!!.jsonArray.size)
        assertEquals("2", root["count"]!!.jsonPrimitive.content)
    }

    @Test
    fun `execute writes the vibecoding contract and metadata alongside the composite JSON`() {
        val task = plainTask()
        val out = File(tempDir, "out/composite-context.json")
        task.outputFile.set(out)
        task.storeOverride.set(RecordingRagStore(listOf(result("clean section"))))

        task.execute()

        assertTrue(File(out.parentFile, "composite-context-vibecoding.json").exists())
        assertTrue(File(out.parentFile, "metadata.json").exists())
    }

    @Test
    fun `execute succeeds standalone when the enriched json file is missing`() {
        val task = plainTask()
        val out = File(tempDir, "out/composite-context.json")
        task.outputFile.set(out)
        task.storeOverride.set(RecordingRagStore(listOf(result("clean section"))))
        // Mirrors the plugin default when enrichJsonLdd has not run.
        task.enrichedJsonFile.setFrom(File(tempDir, "absent/enriched-ldd.json"))

        task.execute()

        assertTrue(out.exists(), "standalone generateCompositeContext must produce its output")
    }

    @Test
    fun `execute degrades silently on an unreadable enriched json file`() {
        val task = plainTask()
        val out = File(tempDir, "out/composite-context.json")
        val broken = File(tempDir, "broken-enriched.json")
        broken.writeText("{ not valid json")
        task.outputFile.set(out)
        task.storeOverride.set(RecordingRagStore(listOf(result("clean section"))))
        task.enrichedJsonFile.setFrom(broken)

        task.execute()

        assertTrue(out.exists())
        assertEquals("", task.buildGraphifySection())
    }

    @Test
    fun `execute reaches the graphify channel when the enriched json exists`() {
        val task = plainTask()
        val out = File(tempDir, "out/composite-context.json")
        val enriched = File(tempDir, "enriched.json")
        enriched.writeText(
            json.encodeToString(
                ListSerializer(EnrichedLddNode.serializer()),
                listOf(enrichedNode("Architecture", "node-arch-1")),
            )
        )
        task.outputFile.set(out)
        task.storeOverride.set(RecordingRagStore(emptyList()))
        task.enrichedJsonFile.setFrom(enriched)

        task.execute()

        assertTrue(task.buildGraphifySection().contains("node-arch-1"))
    }
}
