package codex.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * TDD — CDX-CORPUS-2 : `SectionTitleNormalizer` — jointure LDD ↔ chunks ↔
 * graphify robuste aux encodages de titres divergents.
 *
 * Le run S-217 a prouvé que les mêmes sections portent des encodages
 * différents selon le parser :
 *  - LDD (AsciidoctorJ AST) : entités HTML (`&#8217;`, `&#8230;&#8203;`),
 *    footnotes `<sup class="footnote">[...]</sup>`.
 *  - Chunks (SemanticChunker) : apostrophes typographiques (`'`),
 *    macros AsciiDoc `footnote:[102]`, dot leaders ASCII + numéro de page
 *    (`......24`).
 *
 * Sans normalisation, la jointure exacte (case-insensitive) ne matche que
 * 30/111 sections du corpus FPA — avec normalisation, 110/111.
 *
 * Baby-step TDD strict RED (type inexistant) → GREEN → REFACTOR.
 */
class SectionTitleNormalizerTest {

    @Test
    fun `normalizes simple title unchanged`() {
        assertEquals(
            "architecture",
            SectionTitleNormalizer.normalize("Architecture")
        )
    }

    @Test
    fun `lowercases and trims whitespace`() {
        assertEquals(
            "domain-driven design",
            SectionTitleNormalizer.normalize("  Domain-Driven Design  ")
        )
    }

    @Test
    fun `collapses internal whitespace runs`() {
        assertEquals(
            "a b",
            SectionTitleNormalizer.normalize("a \t \n b")
        )
    }

    @Test
    fun `unescapes html entities`() {
        assertEquals(
            "devenir formateur professionnel d'adultes",
            SectionTitleNormalizer.normalize("Devenir Formateur Professionnel d&#8217;Adultes")
        )
    }

    @Test
    fun `unescapes ellipsis html entity`() {
        assertEquals(
            "section",
            SectionTitleNormalizer.normalize("Section&#8230;&#8203;")
        )
    }

    @Test
    fun `strips asciidoc footnote macro`() {
        assertEquals(
            "2-3.2 développer son état d'esprit, le mind set",
            SectionTitleNormalizer.normalize("2-3.2 Développer son état d'esprit, le Mind Set.footnote:[102]")
        )
    }

    @Test
    fun `strips html tags from title`() {
        assertEquals(
            "points clés de l'analyse",
            SectionTitleNormalizer.normalize("Points clés de l'analyse<sup class=\"footnote\">[<a id=\"_footnote\">1</a>]</sup>")
        )
    }

    @Test
    fun `strips trailing dot leaders with page number`() {
        assertEquals(
            "1-1.2 différencier : activité et modules, compétences et séquences",
            SectionTitleNormalizer.normalize("1-1.2 Différencier : activité et modules, compétences et séquences..........24")
        )
    }

    @Test
    fun `strips trailing dot leaders with internal spaces before page number`() {
        assertEquals(
            "1-1.2 différencier : activité et modules, compétences et séquences",
            SectionTitleNormalizer.normalize("1-1.2 Différencier : activité et modules, compétences et séquences&#8230;&#8203; &#8230;&#8203;&#8230;&#8203;&#8230;&#8203;.24")
        )
    }

    @Test
    fun `strips trailing dot leaders without page number`() {
        assertEquals(
            "organiser le contenu du scénario",
            SectionTitleNormalizer.normalize("Organiser le contenu du scénario .............................")
        )
    }

    @Test
    fun `strips trailing unicode ellipsis runs`() {
        assertEquals(
            "devenir formateur professionnel d'adultes",
            SectionTitleNormalizer.normalize("Devenir Formateur Professionnel d'Adultes \u2026\u200b\u2026\u200b\u2026\u200b")
        )
    }

    @Test
    fun `strips asciidoc footnote macro with dots after`() {
        assertEquals(
            "1-1.5 utiliser un vocabulaire (in)approprié aux objectifs",
            SectionTitleNormalizer.normalize("1-1.5 Utiliser un vocabulaire (in)approprié aux objectifs.footnote:[83]. ......")
        )
    }

    @Test
    fun `empty title normalizes to empty string`() {
        assertEquals("", SectionTitleNormalizer.normalize(""))
    }

    @Test
    fun `blank title normalizes to empty string`() {
        assertEquals("", SectionTitleNormalizer.normalize("   \t "))
    }

    @Test
    fun `dot-only title normalizes to empty string`() {
        assertEquals(
            "",
            SectionTitleNormalizer.normalize(".........................")
        )
    }

    @Test
    fun `normalization is idempotent`() {
        val once = SectionTitleNormalizer.normalize("1-1.3 Élaborer un Scénario Pédagogique Global, SPG.......22")
        val twice = SectionTitleNormalizer.normalize(once)
        assertEquals(once, twice, "normalize(normalize(x)) == normalize(x)")
    }

    @Test
    fun `distinct titles keep distinct normal forms`() {
        val a = SectionTitleNormalizer.normalize("Jour 1")
        val b = SectionTitleNormalizer.normalize("Jour 2")
        assertNotEquals(a, b)
    }

    @Test
    fun `real fpa corpus join - ldd title matches chunk sectionPath last segment`() {
        // Reproduction exacte du run S-217 : LDD (AsciidoctorJ) vs chunk (SemanticChunker)
        val lddTitle = "1-1.2 Différencier : activité et modules, compétences et séquences&#8230;&#8203; &#8230;&#8203;&#8230;&#8203;&#8230;&#8203;.24"
        val chunkLastSegment = " 1-1.2 Différencier : activité et modules, compétences et séquences... ..........24"
        assertEquals(
            SectionTitleNormalizer.normalize(chunkLastSegment),
            SectionTitleNormalizer.normalize(lddTitle),
            "both encodings of the same section must share one normal form"
        )
    }

    @Test
    fun `real fpa corpus join - footnote macro vs sup html`() {
        val lddTitle = "2-3.2 Développer son état d'esprit, le mind set.<sup class=\"footnote\">[<a id=\"_footnote_ref\">1</a>]</sup>"
        val chunkLastSegment = " 2-3.2 Développer son état d'esprit, le Mind Set.footnote:[102]"
        assertEquals(
            SectionTitleNormalizer.normalize(chunkLastSegment),
            SectionTitleNormalizer.normalize(lddTitle),
            "footnote encoded by AsciidoctorJ vs AsciiDoc macro must share one normal form"
        )
    }
}