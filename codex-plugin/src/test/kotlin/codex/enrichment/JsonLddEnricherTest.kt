package codex.enrichment

import codex.tasks.DocumentChunk
import codex.tasks.LddNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-4-1 : Domaine `codex.enrichment` + `JsonLddEnricher` object pur.
 *
 * `JsonLddEnricher.enrich(lddNodes, ragChunks, graphifyResolver)` croise le
 * JSON LDD (structure documentaire) avec les chunks RAG pgvector et les
 * nœuds Graphify résolus par titre de section. Chaque section LDD reçoit :
 *
 *  - `ragChunks`        : chunks RAG joints par `sectionPath` (match titre).
 *  - `graphifyNodes`    : nœuds Graphify résolus par titre de section.
 *  - `semanticDensity`  : ratio `joinedChunks.size / totalChunks`.
 *  - `entities`         : entités extraites du contenu (peuple le placeholder
 *                         `DocumentChunk.entities`).
 *
 * Baby-step TDD strict RED (types inexistants) → GREEN → REFACTOR.
 */
class JsonLddEnricherTest {

    private val graphifyResolver = object : GraphifyResolver {
        override fun resolve(sectionTitle: String): List<String> = emptyList()
    }

    @Test
    fun `enrich with empty lddNodes returns empty list`() {
        val result = JsonLddEnricher.enrich(emptyList(), emptyList(), graphifyResolver)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `enrich attaches rag chunks matching section title`() {
        val section = LddNode(title = "Architecture", level = 1)
        val chunk = DocumentChunk(
            id = "chk-1",
            sourceDocument = "book",
            sectionPath = "Chapter 1 > Architecture",
            headingLevel = 2,
            content = "Some content"
        )
        val result = JsonLddEnricher.enrich(listOf(section), listOf(chunk), graphifyResolver)
        assertEquals(1, result.size)
        assertTrue(result.first().ragChunks.contains(chunk),
            "chunk whose sectionPath ends with the section title should be attached")
    }

    @Test
    fun `enrich does not attach rag chunks from other sections`() {
        val section = LddNode(title = "Architecture", level = 1)
        val other = DocumentChunk(
            id = "chk-2",
            sourceDocument = "book",
            sectionPath = "Chapter 1 > Testing",
            headingLevel = 2,
            content = "Other content"
        )
        val result = JsonLddEnricher.enrich(listOf(section), listOf(other), graphifyResolver)
        assertEquals(0, result.first().ragChunks.size,
            "chunk whose sectionPath does not end with the section title is not attached")
    }

    @Test
    fun `enrich resolves graphify nodes via resolver`() {
        val section = LddNode(title = "Architecture", level = 1)
        val resolver = object : GraphifyResolver {
            override fun resolve(sectionTitle: String): List<String> =
                if (sectionTitle == "Architecture") listOf("node-arch-1", "node-arch-2") else emptyList()
        }
        val result = JsonLddEnricher.enrich(listOf(section), emptyList(), resolver)
        assertEquals(listOf("node-arch-1", "node-arch-2"), result.first().graphifyNodes)
    }

    @Test
    fun `enrich computes semantic density as joinedOverTotal ratio`() {
        val section = LddNode(title = "Architecture", level = 1)
        val matching = DocumentChunk(
            id = "chk-1", sourceDocument = "book",
            sectionPath = "Chapter 1 > Architecture", headingLevel = 2, content = "a"
        )
        val other = DocumentChunk(
            id = "chk-2", sourceDocument = "book",
            sectionPath = "Chapter 1 > Testing", headingLevel = 2, content = "b"
        )
        val result = JsonLddEnricher.enrich(listOf(section), listOf(matching, other), graphifyResolver)
        assertEquals(0.5, result.first().semanticDensity, 0.0001,
            "1 matching chunk out of 2 total → density 0.5")
    }

    @Test
    fun `enrich returns zero density when no rag chunks provided`() {
        val section = LddNode(title = "Architecture", level = 1)
        val result = JsonLddEnricher.enrich(listOf(section), emptyList(), graphifyResolver)
        assertEquals(0.0, result.first().semanticDensity, 0.0001)
    }

    @Test
    fun `enrich extracts entities from section content`() {
        val section = LddNode(title = "Domain-Driven Design", level = 1, text = "Introduced by Eric Evans")
        val result = JsonLddEnricher.enrich(listOf(section), emptyList(), graphifyResolver)
        assertTrue(result.first().entities.isNotEmpty(),
            "capitalized words from title and text should be extracted as entities")
    }

    @Test
    fun `enrich preserves ldd node title and level`() {
        val section = LddNode(title = "Architecture", level = 2)
        val result = JsonLddEnricher.enrich(listOf(section), emptyList(), graphifyResolver)
        assertEquals("Architecture", result.first().ldd.title)
        assertEquals(2, result.first().ldd.level)
    }

    @Test
    fun `enrich is pure - same input yields same output`() {
        val section = LddNode(title = "Architecture", level = 1)
        val chunk = DocumentChunk(
            id = "chk-1", sourceDocument = "book",
            sectionPath = "Chapter 1 > Architecture", headingLevel = 2, content = "a"
        )
        val r1 = JsonLddEnricher.enrich(listOf(section), listOf(chunk), graphifyResolver)
        val r2 = JsonLddEnricher.enrich(listOf(section), listOf(chunk), graphifyResolver)
        assertEquals(r1, r2, "JsonLddEnricher must be deterministic (pure object)")
    }

    @Test
    fun `enrich handles paragraph nodes without rag chunks`() {
        val paragraph = LddNode(title = "", level = -1, type = "paragraph", text = "Some text")
        val result = JsonLddEnricher.enrich(listOf(paragraph), emptyList(), graphifyResolver)
        assertEquals(1, result.size)
        assertEquals(0, result.first().ragChunks.size)
    }
}