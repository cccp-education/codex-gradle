package codex.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SemanticChunker] — pure object extracted from
 * `ChunkDocumentTask` (CDX-7-3, constat #7 of CADRAGE_EPIC_P1_CORPUS_ENRICHMENT).
 *
 * Characterization tests : the chunking logic already exists in
 * `ChunkDocumentTask.buildChunks` / `buildSectionPath` /
 * `extractFirstTwoSentences` ; this suite covers them in isolation so the
 * behaviour is locked before any unification with `CodexPipelineTask.chunkMd`.
 */
class SemanticChunkerTest {

    @Test
    fun `chunk splits simple markdown into heading sections`() {
        val text = """
            # Main Title

            Intro paragraph text.

            ## Chapter One

            Chapter one content here.

            ## Chapter Two

            Chapter two content.
        """.trimIndent()

        val chunks = SemanticChunker.chunk(text, "doc", "Apache-2.0")

        assertTrue(chunks.size >= 3, "Expected at least 3 chunks, got ${chunks.size}")
        assertEquals("doc", chunks.first().sourceDocument)
        assertTrue(chunks.any { it.sectionPath.contains("Main Title") })
        assertTrue(chunks.any { it.sectionPath.contains("Chapter One") })
        assertTrue(chunks.any { it.sectionPath.contains("Chapter Two") })
        assertEquals("Apache-2.0", chunks.first().license)
    }

    @Test
    fun `chunk produces single chunk for no-heading document`() {
        val text = """
            This document has no headings.
            Just plain text everywhere.
            Multiple lines of content.
        """.trimIndent()

        val chunks = SemanticChunker.chunk(text, "nohead", "Apache-2.0")

        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].headingLevel)
        assertEquals("nohead", chunks[0].sourceDocument)
    }

    @Test
    fun `chunk extracts code blocks from sections`() {
        val text = """
            # Code Examples

            Here is some javascript:

            ```javascript
            function hello() {
                console.log("Hello");
            }
            ```

            ## Python Example

            More explanation.

            ```python
            def greet():
                print("Hi")
            ```
        """.trimIndent()

        val chunks = SemanticChunker.chunk(text, "code", "Apache-2.0")

        val jsChunk = chunks.find { it.sectionPath.contains("Code Examples") }
        assertNotNull(jsChunk)
        assertTrue(jsChunk!!.codeBlocks.any { it.contains("console.log") })

        val pyChunk = chunks.find { it.sectionPath.contains("Python Example") }
        assertNotNull(pyChunk)
        assertTrue(pyChunk!!.codeBlocks.any { it.contains("print(") })
    }

    @Test
    fun `chunk generates deterministic IDs for the same input`() {
        val text = """
            # Test

            Content here.
        """.trimIndent()

        val ids1 = SemanticChunker.chunk(text, "idtest", "Apache-2.0").map { it.id }
        val ids2 = SemanticChunker.chunk(text, "idtest", "Apache-2.0").map { it.id }

        assertEquals(ids1, ids2, "Chunk IDs should be deterministic")
        assertTrue(ids1.first().startsWith("chk-"))
    }

    @Test
    fun `buildSectionPath builds hierarchy from ancestor headings`() {
        val sections = listOf(
            SemanticChunker.Section("= Part One", 1, "Part One"),
            SemanticChunker.Section("== Chapter A", 2, "Chapter A"),
            SemanticChunker.Section("=== Section A.1", 3, "Section A.1"),
            SemanticChunker.Section("=== Section A.2", 3, "Section A.2"),
            SemanticChunker.Section("== Chapter B", 2, "Chapter B"),
        )

        val pathA1 = SemanticChunker.buildSectionPath(sections, 2)
        assertTrue(pathA1.contains("Part One"))
        assertTrue(pathA1.contains("Chapter A"))
        assertTrue(pathA1.contains("Section A.1"))
        assertTrue(pathA1.contains(" > "), "Path should have separators")

        val pathB = SemanticChunker.buildSectionPath(sections, 4)
        assertTrue(pathB.contains("Part One"))
        assertTrue(pathB.contains("Chapter B"))
        assertTrue(!pathB.contains("Chapter A"), "Chapter B should not include Chapter A")
    }

    @Test
    fun `extractFirstTwoSentences returns first two sentences from lines`() {
        val lines = listOf(
            "Second section starts here.",
            "More second section text.",
            "And a third sentence to fill it up.",
        )

        val result = SemanticChunker.extractFirstTwoSentences(lines)

        assertNotNull(result)
        assertTrue(result!!.contains("Second section starts here"))
        assertTrue(result.contains("More second section text"))
    }

    @Test
    fun `extractFirstTwoSentences returns null for empty lines`() {
        val result = SemanticChunker.extractFirstTwoSentences(emptyList())
        assertNull(result)

        val blankResult = SemanticChunker.extractFirstTwoSentences(listOf("", "   ", "# heading"))
        assertNull(blankResult, "Lines with only blank/heading content should yield null")
    }
}