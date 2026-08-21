package codex.profile

import contracts.runtime.LearnerProfile

/**
 * EPIC CDX-RC-04-1 — Embedding du profil stagiaire.
 *
 * Calcule le texte à embedder depuis un [LearnerProfile]. Sémantique cible :
 * "points faibles + notes pédagogiques" requêtable par recherche sémantique.
 *
 * Décisions (cadrage S-076) :
 * - Embedding = concaténation `weakPoints.joinToString` +
 *   `annotations.values.joinToString`.
 * - PAS d'embedding sur `completedModules` (liste de IDs, pas sémantique).
 * - PAS d'embedding sur `progressionPct`/`comprehensionScore` (numériques,
 *   filtrage SQL direct).
 * - PAS d'embedding sur `currentModule` (ID de module, pas sémantique).
 *
 * Objet pur (sans état, sans effet de bord) — unit-testable sans ONNX.
 */
object ProfileEmbedding {

    /**
     * Calcule le texte à embedder depuis un [LearnerProfile].
     *
     * @param profile le profil stagiaire
     * @return la concaténation des weakPoints + annotations values,
     *         ou string vide si les deux sont vides (profil sans signal
     *         sémantique — l'embedding sera alors calculé sur un texte vide,
     *         ce qui produit un vecteur nul/non-requêtable, ce qui est
     *         acceptable : un profil sans weak points ni annotations n'a
     *         pas vocation à être retrouvé par recherche sémantique)
     */
    fun textToEmbed(profile: LearnerProfile): String {
        val weakPointsText = profile.weakPoints.joinToString(separator = " ")
        val annotationsText = profile.annotations.values.joinToString(separator = " ")
        return listOf(weakPointsText, annotationsText)
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
    }
}