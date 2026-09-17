package codex.tasks

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * SÉQUENCE-C C-2 — split-brain `DocumentChunk` unification driver.
 *
 * [SemanticChunker] (producer) and the ingest path must share the single
 * N1 store type `codebase.store.DocumentChunk` — not a codex-local duplicate.
 * This RED test drives the removal of `codex.tasks.DocumentChunk`: it asserts
 * the chunker already emits the store type, so the production switch cannot
 * silently keep a second data class alive.
 *
 * The `chunks.json` contract is characterized separately by
 * [ChunkDocumentJsonContractTest] (GREEN both before and after the switch).
 */
class DocumentChunkUnificationTest {

    @Test
    fun `chunker emits the N1 store type`() {
        val chunks = SemanticChunker.chunk(
            "# Title\n\nSome content here.",
            "doc",
            "Apache-2.0"
        )

        assertTrue(chunks.isNotEmpty(), "chunker must produce chunks")
        val first: Any = chunks.first()
        assertTrue(
            first is codebase.store.DocumentChunk,
            "chunks must be codebase.store.DocumentChunk, was ${first::class}"
        )
    }
}
