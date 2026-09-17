package codex.tasks

import codebase.store.DoubtExposure
import codebase.store.RagVectorStore
import codebase.store.RetrieveResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CDX-DOUBT-BRIDGE-2 — the composite-context task must expose the doubt.
 *
 * Before this US, [CodexCompositeContextTask] queried with `searchBlocking`
 * (a SELECT without the additive `confidence`/`doubtful` columns) and joined
 * the Docs channel by hand — the doubt never reached `composite-context.json`,
 * so the N3 consumer could cite shaky OCR text unknowingly.
 *
 * The N1 socle already exposes doubt (`searchWithDoubtBlocking`,
 * `DoubtExposure`). The task now delegates both: it retrieves doubt-aware and
 * renders the Docs channel through the socle exposure policy (annotate by
 * default, exclude in opt-in).
 *
 * This suite pins the delegation through a recording subclass of the `open`
 * [RagVectorStore] (no Docker).
 *
 * Baby-step TDD RED -> GREEN -> REFACTOR.
 */
class CodexCompositeContextDoubtTest {

    /** Recording in-memory subclass — no pgvector, no R2DBC, no Docker. */
    private class RecordingRagStore(
        private val results: List<RetrieveResult>,
    ) : RagVectorStore() {
        var searchWithDoubtCalled = false

        override suspend fun searchWithDoubt(query: String, topK: Int): List<RetrieveResult> {
            searchWithDoubtCalled = true
            return results
        }
    }

    private fun task() =
        ProjectBuilder.builder().build()
            .tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

    private fun result(text: String, doubtful: Boolean, confidence: Double = 1.0) =
        RetrieveResult(
            chunkId = 1L, chunkIndex = 0, chunkText = text,
            sectionPath = "Ch1", headingLevel = 1, sourceDocument = "book",
            similarity = 0.8, confidence = confidence, doubtful = doubtful,
        )

    @Test
    fun `retrieval delegates to the doubt-aware socle search`() = kotlinx.coroutines.runBlocking {
        val store = RecordingRagStore(listOf(result("clean text", doubtful = false)))

        task().searchDoubtAware(store, "query", 5)

        assertTrue(store.searchWithDoubtCalled, "the doubt-aware searchWithDoubt must be used")
    }

    @Test
    fun `docs channel annotates a doubtful chunk by default`() {
        val results = listOf(
            result("clean section", doubtful = false),
            result("shaky OCR section", doubtful = true, confidence = 0.0),
        )

        val docs = task().buildDocsContent(results, excludeDoubtful = false)

        assertTrue(docs.contains("clean section"))
        assertTrue(docs.contains("shaky OCR section"), "doubtful chunk is kept and annotated by default")
        assertTrue(docs.contains(DoubtExposure.DOUBT_MARKER), "the doubt marker must be present")
    }

    @Test
    fun `docs channel excludes a doubtful chunk when opted in`() {
        val results = listOf(
            result("clean section", doubtful = false),
            result("shaky OCR section", doubtful = true, confidence = 0.0),
        )

        val docs = task().buildDocsContent(results, excludeDoubtful = true)

        assertTrue(docs.contains("clean section"))
        assertFalse(docs.contains("shaky OCR section"), "doubtful chunk must be dropped when opted in")
    }

    @Test
    fun `task exposes an optional excludeDoubtfulDocs property defaulting to false`() {
        val task = task()

        // Backward compat : the Docs channel still annotates (keeps) by default.
        assertEquals(false, task.excludeDoubtfulDocs.getOrElse(false))
    }
}
