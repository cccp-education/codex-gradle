package codex.tasks

import codebase.store.DocumentChunk
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File

/**
 * C-4 (S-233) — characterisation of the TOC-noise detector.
 *
 * Two levels:
 *
 * 1. A deterministic synthetic document (generic TOC entries + real sections)
 *    proves precision and recall of the split through the real
 *    [SemanticChunker] — it runs everywhere, including CI.
 * 2. A real acquired corpus measurement, skip-guarded (pattern
 *    `PageProvenanceJoinRateTest`): the private corpus location is supplied by
 *    the caller through `codex.tocNoise.chunks`; the public repo never
 *    hardcodes business content. Pins a floor so a regression in the detector
 *    is caught on real data.
 *
 * Reads only — never mutates the corpus (Loi de l'Économie d'Encre).
 */
class TocNoiseCharacterizationTest {

    private val synthetic = """
        # Guide

        ## Introduction

        A substantive introduction paragraph explaining the subject matter in detail.

        ## Historique du Titre ..........13

        ## Les compétences ......................14

        ## 1-1.2 Différencier : activité et séquences...

        Real body about differentiation with enough text to be a content chunk.

        ## Vue synoptique

        See chapter 1.1.2 and section 3.3.6 for the details of the approach.
    """.trimIndent()

    @Test
    fun `chunker plus detector isolate the toc entries on a synthetic document`() {
        val chunks = SemanticChunker.chunk(synthetic, "guide", "PROPRIETARY")

        val noisy = chunks.filter { TocNoiseDetector.isTocNoise(it) }
        val clean = chunks.filterNot { TocNoiseDetector.isTocNoise(it) }

        assertTrue(noisy.isNotEmpty(), "the synthetic TOC entries must be detected")
        assertTrue(
            noisy.all { it.sectionPath.contains("..........") || it.content.contains("..........") },
            "every flagged chunk must be a dot-leader heading, was ${noisy.map { it.sectionPath }}"
        )
        assertTrue(
            clean.any { it.sectionPath.contains("Introduction") },
            "a clean heading must not be flagged"
        )
        assertTrue(
            clean.any { it.sectionPath.contains("Différencier") },
            "a real title with an ellipsis must not be flagged"
        )
        assertTrue(
            clean.any { it.sectionPath.contains("Vue synoptique") },
            "dotted prose must not be flagged"
        )
    }

    @Test
    @Tag("integration")
    fun `real corpus toc noise holds above the pin floor`() {
        val file = locate(System.getProperty("codex.tocNoise.chunks"))
        assumeTrue(
            file != null,
            "corpus absent (set codex.tocNoise.chunks) — skipping real TOC-noise measurement"
        )

        val chunks: List<DocumentChunk> = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(DocumentChunk.serializer()), file!!.readText())
        val noisy = chunks.filter { TocNoiseDetector.isTocNoise(it) }

        println("[codex] TOC noise: ${noisy.size}/${chunks.size} chunks flagged doubtful " +
            "(floor 25, all thin)")

        assertTrue(chunks.isNotEmpty(), "the real corpus must yield chunks")
        assertTrue(
            noisy.size >= 25,
            "at least 25 TOC-polluted chunks must be detected — got ${noisy.size}"
        )
        assertTrue(
            noisy.all { it.content.length < 150 },
            "a TOC entry is near-empty; a substantive chunk must never be flagged"
        )
    }

    private fun locate(relative: String?): File? {
        if (relative.isNullOrBlank()) return null
        val direct = File(relative)
        if (direct.exists()) return direct
        var dir = File(".").absoluteFile.parentFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
