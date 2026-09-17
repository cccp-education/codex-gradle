package codex.provenance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * TDD — CDX-PAGE-PROVENANCE-1 : `TocPageIndex`.
 *
 * The TOC is the source of truth for the section → page mapping
 * (`Référence | Titre | Page | Fichier`, the AsciiDoc table format of
 * document-gradle `BookTocParser`). Parsing is pure and deterministic
 * (Loi de l'Économie d'Encre) — the same TOC always yields the same
 * sections, and no file is mutated.
 *
 * Baby-step TDD strict RED (type inexistant) → GREEN → REFACTOR.
 */
class TocPageIndexTest {

    @Test
    fun `parses a single-page row`() {
        val sections = TocPageIndex.parse(
            "| 1.2.1 | Créer un Scénario Pédagogique Détaillé (SPD) | 39 | 039.pdf"
        )

        assertEquals(1, sections.size)
        assertEquals("1.2.1", sections[0].ref)
        assertEquals("Créer un Scénario Pédagogique Détaillé (SPD)", sections[0].title)
        assertEquals(listOf(39), sections[0].pages)
    }

    @Test
    fun `parses a multi-page row into an ordered page list`() {
        val sections = TocPageIndex.parse(
            "| 1.0.1 | Devenir Formateur Professionnel d'Adultes | 5, 6, 7, 8 | 005.pdf, 006.pdf, 007.pdf, 008.pdf"
        )

        assertEquals(listOf(5, 6, 7, 8), sections[0].pages)
    }

    @Test
    fun `keeps nested references`() {
        val sections = TocPageIndex.parse(
            "| 1.0.2.6.1 | Présentation de l'activité type 1 | 19 | 019.pdf"
        )

        assertEquals("1.0.2.6.1", sections[0].ref)
    }

    @Test
    fun `skips the header row`() {
        val sections = TocPageIndex.parse(
            "| Référence | Sujet / Titre de la section | Page | Fichier"
        )

        assertTrue(sections.isEmpty())
    }

    @Test
    fun `skips rows with a non-numeric reference`() {
        val sections = TocPageIndex.parse(
            "| not-a-ref | Some title | 5 | 005.pdf"
        )

        assertTrue(sections.isEmpty())
    }

    @Test
    fun `skips rows with a blank title`() {
        val sections = TocPageIndex.parse(
            "| 1.0.1 |   | 5 | 005.pdf"
        )

        assertTrue(sections.isEmpty())
    }

    @Test
    fun `skips rows without a numeric page`() {
        val sections = TocPageIndex.parse(
            "| 1.0.1 | Some title | not-a-page | 005.pdf"
        )

        assertTrue(sections.isEmpty())
    }

    @Test
    fun `skips non-table lines and rows with too few cells`() {
        val sections = TocPageIndex.parse(
            """
            = Table des matières
            | 1.0.1 | Only two cells
            | 1.0.2 | Valid title | 6 | 006.pdf
            """.trimIndent()
        )

        assertEquals(1, sections.size)
        assertEquals("1.0.2", sections[0].ref)
    }

    @Test
    fun `parses a toc file from disk`(@TempDir dir: File) {
        val toc = File(dir, "toc.adoc")
        toc.writeText(
            """
            [cols="1,3,1,1", options="header"]
            |===
            | Référence | Sujet / Titre de la section | Page | Fichier
            | 1.2.1 | Créer un Scénario Pédagogique Détaillé (SPD) | 39 | 039.pdf
            |===
            """.trimIndent()
        )

        val sections = TocPageIndex.parse(toc)
        assertEquals(1, sections.size)
        assertEquals(listOf(39), sections[0].pages)
    }

    @Test
    fun `missing toc file yields an empty section list`(@TempDir dir: File) {
        assertTrue(TocPageIndex.parse(File(dir, "absent.adoc")).isEmpty())
    }

    @Test
    fun `page index keys on the normalized title`() {
        val index = TocPageIndex.pageIndex(
            listOf(TocSection(ref = "1.2.1.1", title = "Organiser le contenu du scénario", pages = listOf(40)))
        )

        assertEquals(listOf(40), index["organiser le contenu du scénario"])
    }

    @Test
    fun `page index normalizes polluted titles with dot leaders`() {
        val index = TocPageIndex.pageIndex(
            listOf(TocSection(ref = "1.1.3", title = "Élaborer un SPG..........26", pages = listOf(26)))
        )

        assertEquals(listOf(26), index["élaborer un spg"])
    }

    @Test
    fun `page index unions pages of duplicate titles in order`() {
        val index = TocPageIndex.pageIndex(
            listOf(
                TocSection(ref = "1.0.2.1", title = "Les compétences", pages = listOf(14)),
                TocSection(ref = "1.0.2.2", title = "Les compétences", pages = listOf(15)),
            )
        )

        assertEquals(listOf(14, 15), index["les compétences"])
    }

    @Test
    fun `page index ignores titles that normalize to empty`() {
        val index = TocPageIndex.pageIndex(
            listOf(TocSection(ref = "1.0.1", title = "..........", pages = listOf(5)))
        )

        assertTrue(index.isEmpty())
    }
}
