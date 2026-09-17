package codex.tasks

import codebase.store.DocumentChunk
import codex.ocr.OcrQualityReport
import codex.provenance.PageProvenanceReport
import codex.provenance.PageProvenanceResolver
import codex.provenance.TocPageIndex
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Thin Gradle wrapper that writes the page provenance sidecar
 * (`page-provenance.json`) for a chunk corpus.
 *
 * Closes the acquisition chain: the chunk knows it is doubtful but not from
 * which page; the acquisition report (`OcrQualityReport`, S-218) knows which
 * page is doubtful but not which chunks derive from it. This task joins the
 * two halves through the TOC — a **derived join** (zéro DDL, zéro
 * re-vectorisation — Loi de l'Économie d'Encre).
 *
 * All logic lives in the pure [PageProvenanceResolver] (unit-tested in
 * isolation); this task only reads three files and writes one.
 *
 * Inputs:
 * - [chunksFile] the semantic chunks JSON (`List<DocumentChunk>`)
 * - [tocFile] the book TOC AsciiDoc table (`Référence | Titre | Page | Fichier`)
 * - [qualityReportFile] optional acquisition report JSON — degraded silent
 *   when absent (Économie d'Encre: never force a re-OCR to localise a doubt)
 *
 * Output:
 * - [pageProvenanceFile] `page-provenance.json` ([PageProvenanceReport])
 */
@DisableCachingByDefault(because = "derived join over acquisition artefacts — cheap, non-cacheable")
abstract class CollectPageProvenanceTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val chunksFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tocFile: RegularFileProperty

    /**
     * Optional acquisition-quality report (`OcrQualityReport`). Absent by
     * default: the provenance resolves the pages from the TOC alone, and the
     * acquisition doubt is an enrichment, never a prerequisite.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val qualityReportFile: RegularFileProperty

    @get:OutputFile
    abstract val pageProvenanceFile: RegularFileProperty

    private val json = Json { ignoreUnknownKeys = true }

    @TaskAction
    fun collectPageProvenance() {
        val chunksInput = chunksFile.asFile.get()
        val tocInput = tocFile.asFile.get()
        val output = pageProvenanceFile.asFile.get()

        logger.lifecycle("[codex] collectPageProvenance : ${chunksInput.name} + ${tocInput.name} → ${output.name}")

        val sourceDocument = chunksInput.nameWithoutExtension
        val chunks = json.decodeFromString(
            ListSerializer(DocumentChunk.serializer()),
            chunksInput.readText()
        )
        val tocSections = TocPageIndex.parse(tocInput)
        val qualityReport = readQualityReport()

        val provenance = PageProvenanceResolver.resolve(chunks, tocSections, qualityReport)
        val report = PageProvenanceReport.of(sourceDocument, provenance)

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val prettyJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        output.parentFile?.mkdirs()
        output.writeText(prettyJson.encodeToString(PageProvenanceReport.serializer(), report))

        logger.lifecycle(
            "[codex] ✓ collectPageProvenance — ${report.resolvedCount}/${report.chunkCount} chunks resolved " +
                "(join rate ${"%.3f".format(report.joinRate)}) → ${output.name}"
        )
    }

    /**
     * Reads the optional acquisition report, degrading silently (empty report)
     * when it is absent or unreadable — the page localisation must never fail
     * the build (cadrage S-223 risk mitigation).
     */
    private fun readQualityReport(): OcrQualityReport? {
        val file = qualityReportFile.orNull?.asFile ?: return null
        if (!file.exists()) return null
        return try {
            json.decodeFromString(OcrQualityReport.serializer(), file.readText())
        } catch (e: Exception) {
            logger.warn("[codex] quality report unreadable ({}), degrading to no doubt", e.message)
            null
        }
    }
}
