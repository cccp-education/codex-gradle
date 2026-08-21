package codex.profile

import contracts.runtime.LearnerProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-RC-04-1 : embedding du profil stagiaire.
 *
 * [ProfileEmbedding] calcule le texte à embedder depuis un [LearnerProfile].
 * Sémantique cible : "points faibles + notes pédagogiques" requêtable.
 *
 * Décisions (cadrage S-076) :
 * - Embedding = concaténation `weakPoints.joinToString` + `annotations.values.joinToString`.
 * - PAS d'embedding sur `completedModules` (liste de IDs, pas sémantique).
 * - PAS d'embedding sur `progressionPct`/`comprehensionScore` (numériques,
 *   filtrage SQL direct).
 *
 * Object pur — unit-testable sans ONNX ni base de données.
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