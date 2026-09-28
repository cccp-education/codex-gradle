package codex.profile

import contracts.runtime.LearnerProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-RC-04-1: `codex.profile` embedding.
 *
 * [ProfileEmbedding] computes the text to embed from a [LearnerProfile].
 * Target semantics: "weak points + annotations" queryable.
 *
 * Decisions (cadrage S-076):
 * - Embedding = concatenation `weakPoints.joinToString` + `annotations.values.joinToString`.
 * - NO embedding on `completedModules` (list of IDs, not semantic).
 * - NO embedding on `progressionPct`/`comprehensionScore` (numeric,
 *   direct SQL filtering).
 *
 * Pure object — unit-testable without ONNX or a database.
 */
class ProfileEmbeddingTest {

    @Test
    fun `empty profile produces empty embedding text`() {
        val profile = LearnerProfile("learner-1", "formation-A")
        assertEquals("", ProfileEmbedding.textToEmbed(profile))
    }

    @Test
    fun `weakPoints are joined into embedding text`() {
        val profile = LearnerProfile(
            "learner-1", "formation-A",
            weakPoints = listOf("gradients", "backprop")
        )
        val text = ProfileEmbedding.textToEmbed(profile)
        assertTrue(text.contains("gradients"), "weakPoints in embedding: $text")
        assertTrue(text.contains("backprop"), "weakPoints in embedding: $text")
    }

    @Test
    fun `annotations values are joined into embedding text`() {
        val profile = LearnerProfile(
            "learner-1", "formation-A",
            annotations = mapOf("module-3" to "struggled with regularization", "module-5" to "good intuition")
        )
        val text = ProfileEmbedding.textToEmbed(profile)
        assertTrue(text.contains("struggled with regularization"), "annotations in embedding: $text")
        assertTrue(text.contains("good intuition"), "annotations in embedding: $text")
    }

    @Test
    fun `weakPoints and annotations are concatenated`() {
        val profile = LearnerProfile(
            "learner-1", "formation-A",
            weakPoints = listOf("gradients"),
            annotations = mapOf("m3" to "struggled")
        )
        val text = ProfileEmbedding.textToEmbed(profile)
        assertTrue(text.contains("gradients"), "weakPoints: $text")
        assertTrue(text.contains("struggled"), "annotations: $text")
    }

    @Test
    fun `completedModules are NOT embedded`() {
        val profile = LearnerProfile(
            "learner-1", "formation-A",
            completedModules = listOf("mod-1", "mod-2", "mod-3")
        )
        val text = ProfileEmbedding.textToEmbed(profile)
        assertEquals("", text, "completedModules must not be embedded: $text")
    }

    @Test
    fun `progressionPct and comprehensionScore are NOT embedded`() {
        val profile = LearnerProfile(
            "learner-1", "formation-A",
            progressionPct = 75.0,
            comprehensionScore = 42.0
        )
        val text = ProfileEmbedding.textToEmbed(profile)
        assertEquals("", text, "numerics must not be embedded: $text")
    }

    @Test
    fun `currentModule is NOT embedded`() {
        val profile = LearnerProfile(
            "learner-1", "formation-A",
            currentModule = "module-7"
        )
        val text = ProfileEmbedding.textToEmbed(profile)
        assertEquals("", text, "currentModule (ID) must not be embedded: $text")
    }

    @Test
    fun `embedding text is deterministic for same profile`() {
        val profile = LearnerProfile(
            "learner-1", "formation-A",
            weakPoints = listOf("gradients", "backprop"),
            annotations = mapOf("m3" to "struggled")
        )
        val text1 = ProfileEmbedding.textToEmbed(profile)
        val text2 = ProfileEmbedding.textToEmbed(profile)
        assertEquals(text1, text2, "deterministic embedding text")
    }
}