package codex.enrichment

import codex.tasks.DocumentChunk
import codex.tasks.LddNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-4-3 : `GraphifySectionBuilder` object pur.
 *
 * Construit le contenu du canal Graphify du `CompositeContext` à partir
 * de la liste des [EnrichedLddNode] produits par [JsonLddEnricher].
 * Chaque section LDD enrichie en nœuds Graphify est représentée par :
 *
 *  - le titre de la section,
 *  - la liste des ids de nœuds Graphify résolus.
 *
 * Les sections sans nœuds Graphify sont omises (économie d'encre). Le
 * builder est déterministe et side-effect free : mêmes entrées → même
 * sortie. Empty list → empty string (fallback backward compat).
 *
 * Baby-step TDD strict RED (type inexistant) → GREEN → REFACTOR.
 */
class GraphifySectionBuilderTest {

    @Test
    fun `build with empty enriched nodes returns empty string`() {
        val result = GraphifySectionBuilder.build(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `build omits sections without graphify nodes`() {
        val enriched = listOf(
            EnrichedLddNode(
                ldd = LddNode(title = "Intro", level = 1),
                ragChunks = emptyList(),
                graphifyNodes = emptyList(),
                semanticDensity = 0.0,
                entities = emptyList()
            ),
            EnrichedLddNode(
                ldd = LddNode(title = "Architecture", level = 1),
                ragChunks = emptyList(),
                graphifyNodes = listOf("node-arch-1"),
                semanticDensity = 0.0,
                entities = emptyList()
            )
        )
        val result = GraphifySectionBuilder.build(enriched)
        assertTrue(result.contains("Architecture"), "section with graphify nodes should be included")
        assertTrue(!result.contains("Intro"), "section without graphify nodes should be omitted")
    }

    @Test
    fun `build lists resolved graphify node ids per section`() {
        val enriched = listOf(
            EnrichedLddNode(
                ldd = LddNode(title = "Architecture", level = 1),
                ragChunks = emptyList(),
                graphifyNodes = listOf("node-arch-1", "node-arch-2"),
                semanticDensity = 0.0,
                entities = emptyList()
            )
        )
        val result = GraphifySectionBuilder.build(enriched)
        assertTrue(result.contains("node-arch-1"), "graphify node id should appear in section")
        assertTrue(result.contains("node-arch-2"), "all resolved node ids should appear")
    }

    @Test
    fun `build is pure - same input yields same output`() {
        val enriched = listOf(
            EnrichedLddNode(
                ldd = LddNode(title = "Architecture", level = 1),
                ragChunks = emptyList(),
                graphifyNodes = listOf("node-arch-1"),
                semanticDensity = 0.0,
                entities = emptyList()
            )
        )
        val r1 = GraphifySectionBuilder.build(enriched)
        val r2 = GraphifySectionBuilder.build(enriched)
        assertEquals(r1, r2, "GraphifySectionBuilder must be deterministic (pure object)")
    }

    @Test
    fun `build handles multiple sections each with their graphify nodes`() {
        val enriched = listOf(
            EnrichedLddNode(
                ldd = LddNode(title = "Architecture", level = 1),
                ragChunks = emptyList(),
                graphifyNodes = listOf("node-arch"),
                semanticDensity = 0.0,
                entities = emptyList()
            ),
            EnrichedLddNode(
                ldd = LddNode(title = "Testing", level = 1),
                ragChunks = emptyList(),
                graphifyNodes = listOf("node-test-1", "node-test-2"),
                semanticDensity = 0.0,
                entities = emptyList()
            )
        )
        val result = GraphifySectionBuilder.build(enriched)
        assertTrue(result.contains("Architecture"))
        assertTrue(result.contains("node-arch"))
        assertTrue(result.contains("Testing"))
        assertTrue(result.contains("node-test-1"))
        assertTrue(result.contains("node-test-2"))
    }

    @Test
    fun `build with all sections empty graphify nodes returns empty string`() {
        val enriched = listOf(
            EnrichedLddNode(
                ldd = LddNode(title = "A", level = 1),
                ragChunks = emptyList(),
                graphifyNodes = emptyList(),
                semanticDensity = 0.0,
                entities = emptyList()
            ),
            EnrichedLddNode(
                ldd = LddNode(title = "B", level = 1),
                ragChunks = emptyList(),
                graphifyNodes = emptyList(),
                semanticDensity = 0.0,
                entities = emptyList()
            )
        )
        val result = GraphifySectionBuilder.build(enriched)
        assertEquals("", result, "all sections without graphify nodes → empty string")
    }

    @Test
    fun `build ignores rag chunks and entities - only graphify nodes matter`() {
        val enriched = listOf(
            EnrichedLddNode(
                ldd = LddNode(title = "Architecture", level = 1),
                ragChunks = listOf(
                    DocumentChunk(
                        id = "chk-1", sourceDocument = "book",
                        sectionPath = "Chapter > Architecture", headingLevel = 2, content = "content"
                    )
                ),
                graphifyNodes = listOf("node-arch"),
                semanticDensity = 0.5,
                entities = listOf("Eric Evans")
            )
        )
        val result = GraphifySectionBuilder.build(enriched)
        assertTrue(result.contains("Architecture"))
        assertTrue(result.contains("node-arch"))
        assertTrue(!result.contains("content"), "rag chunk content should not leak into graphify section")
        assertTrue(!result.contains("Eric Evans"), "entities should not leak into graphify section")
    }
}