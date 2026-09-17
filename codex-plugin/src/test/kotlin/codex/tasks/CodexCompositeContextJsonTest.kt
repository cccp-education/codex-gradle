package codex.tasks

import codebase.store.RetrieveResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CodexCompositeContextJson — serialization seam for the N3 contract.
 *
 * Discovered during the S-220 dogfooding run: `generateCompositeContext`
 * threw `Serializer for class 'Any' is not found` at execution time — the
 * task wrote `Map<String, Any>` through kotlinx.serialization, which has no
 * serializer for `Any` (the neighbouring `ExportKnowledgeBaseTask` already
 * uses `JsonObject`/`JsonPrimitive` for this reason).
 *
 * No test drove `execute()` end-to-end, so the N3 contract
 * (`composite-context.json`) was broken in production while `check` stayed
 * green. The JSON building is now extracted into pure seams and pinned here.
 *
 * Baby-step TDD RED -> GREEN -> REFACTOR.
 */
class CodexCompositeContextJsonTest {

    private fun task() =
        ProjectBuilder.builder().build()
            .tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

    private fun result(
        id: Long,
        text: String,
        doubtful: Boolean,
        confidence: Double = 1.0,
    ) = RetrieveResult(
        chunkId = id, chunkIndex = 0, chunkText = text,
        sectionPath = "Ch1 > Sec", headingLevel = 2, sourceDocument = "book",
        similarity = 0.8123, confidence = confidence, doubtful = doubtful,
    )

    @Test
    fun `composite json is serializable and carries the doubt fields`() {
        val results = listOf(
            result(1L, "clean section", doubtful = false),
            result(2L, "shaky OCR section", doubtful = true, confidence = 0.0),
        )

        val raw = task().buildCompositeJson(results, query = "scénario pédagogique", topK = 12)

        val root = Json.parseToJsonElement(raw).jsonObject
        assertEquals("brooklyn", root["source"]!!.jsonPrimitive.content)
        assertEquals("scénario pédagogique", root["query"]!!.jsonPrimitive.content)
        assertEquals("12", root["topK"]!!.jsonPrimitive.content)
        assertEquals("2", root["count"]!!.jsonPrimitive.content)

        val entries = root["entries"]!!.jsonArray
        assertEquals(2, entries.size)
        val doubtfulEntry = entries[1].jsonObject
        assertEquals("true", doubtfulEntry["doubtful"]!!.jsonPrimitive.content)
        assertEquals(0.0, doubtfulEntry["confidence"]!!.jsonPrimitive.content.toDouble())
        assertTrue(doubtfulEntry["chunkText"]!!.jsonPrimitive.content.contains("shaky OCR section"))
    }

    @Test
    fun `composite json truncates chunk text to 500 chars`() {
        val long = "x".repeat(900)
        val raw = task().buildCompositeJson(listOf(result(1L, long, doubtful = false)), "q", 5)

        val text = Json.parseToJsonElement(raw).jsonObject["entries"]!!
            .jsonArray[0].jsonObject["chunkText"]!!.jsonPrimitive.content
        assertEquals(500, text.length)
    }

    @Test
    fun `vibecoding json carries the docs section and budget`() {
        val raw = task().buildVibecodingJson(
            docsSection = "CONTEXTE_DOCS body",
            query = "q",
            topK = 5,
            count = 3,
        )

        val root = Json.parseToJsonElement(raw).jsonObject
        assertEquals("brooklyn", root["source"]!!.jsonPrimitive.content)
        assertEquals("CONTEXTE_DOCS body", root["docsSection"]!!.jsonPrimitive.content)
        assertEquals("3", root["count"]!!.jsonPrimitive.content)
        val budget = root["budget"]!!.jsonObject
        assertEquals("8000", budget["totalTokenBudget"]!!.jsonPrimitive.content)
        assertEquals(0.10, budget["docs"]!!.jsonPrimitive.content.toDouble())
    }
}
