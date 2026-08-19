package codex.store

import codex.tasks.DocumentChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-CR3-3 : `chunk_index` incorrect pour contenu dupliqué.
 *
 * Avant fix : `CodexIngestTask.ingestChunks` utilisait `chunks.indexOf(chunk)`
 * sur la liste globale des chunks. Pour deux chunks identiques (même contenu,
 * même sectionPath, même headingLevel), `indexOf` retourne la première
 * occurrence → les deux chunks recevaient le même `chunk_index` en base,
 * corrompant l'ordre de lecture du corpus et l'idempotence économique
 * (re-ingestion inconditionnelle sur index dupliqués).
 *
 * Après fix : l'index est un compteur local parcouru par document
 * (`docChunks.withIndex()`), indépendant de l'égalité structurelle des
 * chunks. Deux chunks identiques dans le même document reçoivent des
 * index distincts (0, 1, 2...).
 */
class ChunkIndexAssignmentTest {

    @Test
    fun `local indices are sequential per document`() {
        val chunks = listOf(
            chunk("doc-A", "Section 1", "Content 1"),
            chunk("doc-A", "Section 2", "Content 2"),
            chunk("doc-A", "Section 3", "Content 3")
        )
        val indices = IngestIndexing.assignLocalIndices(chunks)
        assertEquals(listOf(0, 1, 2), indices["doc-A"])
    }

    @Test
    fun `duplicate chunks in same document get distinct indices`() {
        val duplicateContent = "Identical body repeated twice."
        val chunks = listOf(
            chunk("doc-A", "Section 1", duplicateContent),
            chunk("doc-A", "Section 2", duplicateContent)
        )
        val indices = IngestIndexing.assignLocalIndices(chunks)
        assertEquals(listOf(0, 1), indices["doc-A"])
        assertNotEquals(
            indices["doc-A"]!![0],
            indices["doc-A"]!![1],
            "Duplicate chunks must not share the same chunk_index (CDX-CR3-3)"
        )
    }

    @Test
    fun `indices are local per document, not global`() {
        val chunks = listOf(
            chunk("doc-A", "Section 1", "Content A1"),
            chunk("doc-B", "Section 1", "Content B1"),
            chunk("doc-B", "Section 2", "Content B2")
        )
        val indices = IngestIndexing.assignLocalIndices(chunks)
        assertEquals(listOf(0), indices["doc-A"])
        assertEquals(listOf(0, 1), indices["doc-B"])
    }

    @Test
    fun `three duplicate chunks produce 0 1 2 not 0 0 0`() {
        val body = "Same body."
        val chunks = listOf(
            chunk("doc-A", "S1", body),
            chunk("doc-A", "S2", body),
            chunk("doc-A", "S3", body)
        )
        val indices = IngestIndexing.assignLocalIndices(chunks)
        assertEquals(listOf(0, 1, 2), indices["doc-A"])
        assertTrue(indices["doc-A"]!!.toSet().size == 3, "All indices must be unique")
    }

    @Test
    fun `empty chunks produce empty index map`() {
        val indices = IngestIndexing.assignLocalIndices(emptyList())
        assertTrue(indices.isEmpty())
    }

    @Test
    fun `single document single chunk produces index 0`() {
        val indices = IngestIndexing.assignLocalIndices(
            listOf(chunk("solo", "S1", "only one"))
        )
        assertEquals(listOf(0), indices["solo"])
    }

    private fun chunk(source: String, section: String, content: String): DocumentChunk =
        DocumentChunk(
            id = "chk-$source-$section",
            sourceDocument = source,
            sectionPath = section,
            headingLevel = 1,
            content = content,
            license = "Apache-2.0"
        )
}