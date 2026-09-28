package codex.profile

import contracts.runtime.LearnerProfile

/**
 * EPIC CDX-RC-04-1 — Embedding of the learner profile.
 *
 * Computes the text to embed from a [LearnerProfile]. Target semantics:
 * "weak points + annotations" queryable by semantic search.
 *
 * Decisions (cadrage S-076):
 * - Embedding = concatenation `weakPoints.joinToString` +
 *   `annotations.values.joinToString`.
 * - NO embedding on `completedModules` (list of IDs, not semantic).
 * - NO embedding on `progressionPct`/`comprehensionScore` (numeric,
 *   direct SQL filtering).
 * - NO embedding on `currentModule` (module ID, not semantic).
 *
 * Pure object (stateless, side-effect free) — unit-testable without ONNX.
 */
object ProfileEmbedding {

    /**
     * Computes the text to embed from a [LearnerProfile].
     *
     * @param profile the learner profile
     * @return the concatenation of weakPoints + annotation values,
     *         or an empty string if both are empty (a profile with no
     *         semantic signal — the embedding is then computed on an empty
     *         text, producing a null/non-queryable vector, which is
     *         acceptable: a profile with neither weak points nor annotations
     *         is not meant to be found by semantic search)
     */
    fun textToEmbed(profile: LearnerProfile): String {
        val weakPointsText = profile.weakPoints.joinToString(separator = " ")
        val annotationsText = profile.annotations.values.joinToString(separator = " ")
        return listOf(weakPointsText, annotationsText)
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
    }
}