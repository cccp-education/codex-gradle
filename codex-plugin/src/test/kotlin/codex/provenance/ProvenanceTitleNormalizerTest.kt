package codex.provenance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * TDD — CDX-PAGE-PROVENANCE-1 : `ProvenanceTitleNormalizer`.
 *
 * Composes the proven `SectionTitleNormalizer` canon (S-217) with the two OCR
 * refinements measured necessary on the real FPA corpus (cadrage S-223 risk):
 * leading numeric references (from the scans) and trailing punctuation/leaders.
 *
 * Without the refinements the TOC ↔ chunk join tops out at 24.8% on the real
 * corpus (last-segment, base canon only); with them the ancestor walk reaches
 * 70.8% (`FpaPageProvenanceJoinRateTest`).
 *
 * Baby-step TDD strict RED (type inexistant) → GREEN → REFACTOR.
 */
class ProvenanceTitleNormalizerTest {

    @Test
    fun `composes the base canon (entities, dot leaders, lowercase)`() {
        assertEquals(
            "organiser le contenu du scénario",
            ProvenanceTitleNormalizer.normalize("Organiser le contenu du scénario .............")
        )
    }

    @Test
    fun `strips a leading hyphenated numeric reference with a space`() {
        assertEquals(
            "identifier les différents référentiels des métiers de la formation",
            ProvenanceTitleNormalizer.normalize(
                "1-1.1 Identifier les différents référentiels des métiers de la formation :"
            )
        )
    }

    @Test
    fun `strips a leading dotted numeric reference with a space`() {
        assertEquals(
            "identifier et choisir une technique d'animation",
            ProvenanceTitleNormalizer.normalize("2.1.2 Identifier et choisir une technique d'animation")
        )
    }

    @Test
    fun `strips a leading multi-part reference glued to the title`() {
        assertEquals(
            "créer un scénario pédagogique détaillé, le spd",
            ProvenanceTitleNormalizer.normalize("1-2.1créer un scénario pédagogique détaillé, le SPD.")
        )
    }

    @Test
    fun `preserves an in-title abbreviation not followed by a space`() {
        assertEquals(
            "utiliser l'outil les 3c pour élaborer une séance",
            ProvenanceTitleNormalizer.normalize("utiliser l'outil les 3C pour élaborer une séance")
        )
    }

    @Test
    fun `preserves a leading year like reference when glued to a word`() {
        // `3C` glued, no space after the digit run → not a reference
        assertNotEquals(
            "",
            ProvenanceTitleNormalizer.normalize("3c modele")
        )
    }

    @Test
    fun `strips trailing colon from an OCR heading`() {
        assertEquals(
            "historique du titre professionnel",
            ProvenanceTitleNormalizer.normalize("Historique du Titre Professionnel :")
        )
    }

    @Test
    fun `strips trailing dot from an OCR heading`() {
        assertEquals(
            "les compétences",
            ProvenanceTitleNormalizer.normalize("Les compétences.")
        )
    }

    @Test
    fun `blank and dot-only titles normalize to empty`() {
        assertEquals("", ProvenanceTitleNormalizer.normalize("   "))
        assertEquals("", ProvenanceTitleNormalizer.normalize(".........."))
    }

    @Test
    fun `normalization is idempotent`() {
        val once = ProvenanceTitleNormalizer.normalize("1-1.3 Élaborer un Scénario Pédagogique Global, SPG.......26")
        val twice = ProvenanceTitleNormalizer.normalize(once)
        assertEquals(once, twice)
    }

    @Test
    fun `toc title and polluted chunk title share one canonical form`() {
        val tocTitle = "2.1.2 Identifier et choisir une technique d'animation"
        val chunkSegment = "2.1.2 identifier et choisir une technique d'animation"
        assertEquals(
            ProvenanceTitleNormalizer.normalize(tocTitle),
            ProvenanceTitleNormalizer.normalize(chunkSegment)
        )
    }
}
