package codex.tasks

import codebase.store.DocumentChunk
import codebase.store.DoubtMetadata
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * C-4 (S-233) — TOC pollution detection at chunking time.
 *
 * The acquired corpus (FPA run S-216) carries table-of-contents entries that
 * the OCR rendered as headings with dot leaders (`Title ..........14`). They
 * become near-empty chunks that pollute retrieval. This pure object
 * characterises such a heading so the ingestion can flag the chunk doubtful
 * (zero re-vectorisation — Loi de l'Économie d'Encre).
 *
 * Baby-step TDD strict RED -> GREEN -> REFACTOR.
 */
class TocNoiseDetectorTest {

    private fun chunk(content: String, path: String = "Book > Chapter") = DocumentChunk(
        id = "chk-1", sourceDocument = "livre", sectionPath = path,
        headingLevel = 2, content = content, license = "PROPRIETARY",
    )

    @Test
    fun `flags a dot leader heading with a trailing page number`() {
        assertTrue(TocNoiseDetector.isTocHeading("## Historique du Titre ..........13"))
    }

    @Test
    fun `flags a dot leader heading without a page number`() {
        assertTrue(TocNoiseDetector.isTocHeading("### Les compétences. ......................"))
    }

    @Test
    fun `does not flag a normal heading`() {
        assertFalse(TocNoiseDetector.isTocHeading("## Introduction à l'andragogie"))
    }

    @Test
    fun `does not flag a three dot ellipsis in a real title`() {
        // A real section title can end with an ellipsis — that is not a leader.
        assertFalse(TocNoiseDetector.isTocHeading("## 1-1.2 Différencier : activité et séquences..."))
    }

    @Test
    fun `does not flag dotted coordinates in a real title`() {
        assertFalse(TocNoiseDetector.isTocHeading("## 3.3.6 Modérer son exposition aux ondes"))
    }

    @Test
    fun `does not flag dotted prose in the body`() {
        val prose = "## Vue synoptique\nSee chapitre 1.1.2 and section 3.3.6 for details."
        assertFalse(TocNoiseDetector.isTocNoise(chunk(prose)))
    }

    @Test
    fun `flags a chunk whose heading is a toc entry`() {
        assertTrue(TocNoiseDetector.isTocNoise(chunk("## Merci : ......................3")))
    }

    @Test
    fun `does not flag a clean chunk`() {
        assertFalse(TocNoiseDetector.isTocNoise(chunk("## Real section\nSubstantive body content.")))
    }

    @Test
    fun `flags any toc heading among several lines`() {
        val multi = "## Real section\nBody.\n### Historique du Titre ..........13"
        assertTrue(TocNoiseDetector.isTocNoise(chunk(multi)))
    }

    @Test
    fun `doubtFor flags a toc chunk doubtful with zero confidence`() {
        val doubt = TocNoiseDetector.doubtFor(chunk("## Merci : ......................3"))
        assertTrue(doubt.doubtful)
        assertTrue(doubt.confidence == TocNoiseDetector.TOC_NOISE_CONFIDENCE)
    }

    @Test
    fun `doubtFor leaves a clean chunk at default doubt`() {
        val doubt = TocNoiseDetector.doubtFor(chunk("## Real section\nBody content."))
        assertFalse(doubt.doubtful)
        assertTrue(doubt.confidence == DoubtMetadata.MAX_CONFIDENCE)
    }

    @Test
    fun `markDoubt flags a toc chunk doubtful`() {
        val marked = TocNoiseDetector.markDoubt(listOf(chunk("## Merci : ......................3")))
        assertTrue(marked.single().doubt.doubtful)
        assertTrue(marked.single().doubt.confidence == TocNoiseDetector.TOC_NOISE_CONFIDENCE)
    }

    @Test
    fun `markDoubt keeps the illisible policy for a non-toc chunk`() {
        val marked = TocNoiseDetector.markDoubt(listOf(chunk("## Real section\n[ILLISIBLE]\nbody")))
        assertTrue(marked.single().doubt.doubtful, "the base DoubtPolicy still flags [ILLISIBLE]")
        assertTrue(marked.single().doubt.confidence == 0.0)
    }

    @Test
    fun `markDoubt keeps a clean chunk at full confidence`() {
        val marked = TocNoiseDetector.markDoubt(listOf(chunk("## Real section\nBody.")))
        assertFalse(marked.single().doubt.doubtful)
        assertTrue(marked.single().doubt.confidence == DoubtMetadata.MAX_CONFIDENCE)
    }
}
