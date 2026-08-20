package codex.tasks

import codex.enrichment.EnrichedLddNode
import codex.tasks.DocumentChunk
import codex.tasks.LddNode
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
 * TDD — EPIC CDX-4-3 : câblage canal Graphify dans [CodexCompositeContextTask].
 *
 * Avant CDX-4-3, `graphifySection` était hardcodé à `""` (canal Graphify
 * muet). Après CDX-4-3, une propriété optionnelle `enrichedJsonFile`
 * (output de `enrichJsonLdd`) permet de peupler le canal via
 * [codex.enrichment.GraphifySectionBuilder]. Fallback `""` si la
 * propriété est absente ou le fichier illisible (backward compat total).
 *
 * Baby-step TDD strict RED → GREEN → REFACTOR.
 */
class CodexCompositeContextGraphifyTest {

    @TempDir
    lateinit var tempDir: File

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun enrichedNode(title: String, vararg nodes: String): EnrichedLddNode =
        EnrichedLddNode(
            ldd = LddNode(title = title, level = 1),
            ragChunks = emptyList(),
            graphifyNodes = nodes.toList(),
            semanticDensity = 0.0,
            entities = emptyList()
        )

    @Test
    fun `buildGraphifySection returns empty when enrichedJsonFile is absent`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        // Fallback backward compat : pas de fichier enrichi → canal muet.
        val section = task.buildGraphifySection()
        assertEquals("", section)
    }

    @Test
    fun `buildGraphifySection returns empty when enrichedJsonFile is empty list`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()
        val enrichedFile = File(tempDir, "enriched-empty.json")
        enrichedFile.writeText(json.encodeToString(ListSerializer(EnrichedLddNode.serializer()), emptyList()))
        task.enrichedJsonFile.set(enrichedFile)

        val section = task.buildGraphifySection()
        assertEquals("", section)
    }

    @Test
    fun `buildGraphifySection populates from enriched nodes with graphify ids`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()
        val enrichedFile = File(tempDir, "enriched.json")
        val enriched = listOf(
            enrichedNode("Architecture", "node-arch-1", "node-arch-2"),
            enrichedNode("Intro") // sans nœuds graphify → omis
        )
        enrichedFile.writeText(json.encodeToString(ListSerializer(EnrichedLddNode.serializer()), enriched))
        task.enrichedJsonFile.set(enrichedFile)

        val section = task.buildGraphifySection()
        assertTrue(section.contains("Architecture"), "section with graphify nodes should be included")
        assertTrue(section.contains("node-arch-1"))
        assertTrue(section.contains("node-arch-2"))
        assertTrue(!section.contains("Intro"), "section without graphify nodes should be omitted")
    }

    @Test
    fun `buildGraphifySection degrades silently on invalid JSON`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()
        val enrichedFile = File(tempDir, "enriched-bad.json")
        enrichedFile.writeText("{ not valid json")
        task.enrichedJsonFile.set(enrichedFile)

        // Dégradé silencieux : JSON invalide → canal muet (pas de crash).
        val section = task.buildGraphifySection()
        assertEquals("", section)
    }

    @Test
    fun `buildGraphifySection is deterministic - same file yields same output`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()
        val enrichedFile = File(tempDir, "enriched.json")
        val enriched = listOf(enrichedNode("Architecture", "node-arch-1"))
        enrichedFile.writeText(json.encodeToString(ListSerializer(EnrichedLddNode.serializer()), enriched))
        task.enrichedJsonFile.set(enrichedFile)

        val r1 = task.buildGraphifySection()
        val r2 = task.buildGraphifySection()
        assertEquals(r1, r2, "buildGraphifySection must be deterministic for the same file")
    }

    @Test
    fun `task exposes optional enrichedJsonFile property`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        assertNotNull(task.enrichedJsonFile)
        assertTrue(!task.enrichedJsonFile.isPresent, "enrichedJsonFile should be optional (no default)")
    }
}