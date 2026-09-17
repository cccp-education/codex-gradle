package codex.tasks

import codex.ocr.DiskCacheStorage
import codex.ocr.OcrEngine
import codex.ocr.OcrPipeline
import codex.ocr.OcrQualityAnalyzer
import codex.ocr.OcrQualityIssue
import codex.ocr.OcrQualityReport
import codex.ocr.OcrRequest
import codex.ocr.OcrResult
import codex.ocr.OcrResultCache
import codex.ocr.TesseractOcrEngine
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.security.MessageDigest

/**
 * Collects OCR results from a directory of page images.
 *
 * For each image in [inputDir], runs the [OcrPipeline] and produces one
 * AsciiDoc file per page in [outputDir] (the N2 ↔ N2 bridge with
 * document-gradle DOC-11). This is the contract consumed by document-gradle
 * `BookAssembler` (New Orleans) to assemble a full book from photos of pages.
 *
 * Engine chain (CDX-OCR-1 boundary rule):
 * - If [aiEngine] is injected by the composition root, the chain is
 *   AI engine → Tesseract (fallback order preserved).
 * - Without injection, the chain degrades to Tesseract-only (functional).
 *   AI-assisted OCR is actioned by the codebase socle — codex never wires
 *   an AI engine itself.
 *
 * Inputs:
 * - [inputDir] directory containing image files (.png, .jpg, .jpeg, .tif, .tiff, .bmp)
 * - [aiEngine] optional injected AI OCR port ([OcrEngine]) — see boundary rule above
 * - [language] ISO 639-1 language hint (default: fr)
 *
 * Outputs:
 * - [outputDir] primary output — one `.adoc` file per page, named `NNN-<pageId>.adoc`
 *   (zero-padded 3-digit page number + image name without extension). The leading
 *   digits are the contract consumed by document-gradle `PageOrder.fromFileName`
 *   to order pages in the assembled book. Each file contains the `structuredText`
 *   of the OcrResult (no `== Page N` header — the header is carried by the file name).
 * - [outputFile] legacy output — single concatenated AsciiDoc document with all
 *   pages merged as `== Page N` sections. Preserved for backward compatibility.
 *   When both [outputDir] and [outputFile] are set, both are written.
 */
@DisableCachingByDefault(because = "LLM OCR call — external metered API, non-cacheable (Loi de l'Économie d'Encre)")
abstract class CollectOcrTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    /**
     * Primary output directory for OCR results.
     * 
     * Contains one `.adoc` file per processed image, named using the contract:
     * `%03d-%s.adoc` where:
     * - `%03d` is the zero-padded 3-digit page number (starting from 001)
     * - `%s` is the original image filename without extension
     * 
     * This naming convention is consumed by document-gradle's [BookAssembler]
     * to preserve the original page order when assembling the final book.
     * The files contain only the structured OCR text (no AsciiDoc headers).
     */
    @get:OutputDirectory
    @get:Optional
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val language: Property<String>

    // US-CDX-13-1 : cache disque optionnel pour éviter de re-OCRoiser
    // les images dont le hash n'a pas changé (Loi de l'Économie d'Encre).
    // Si non configuré, aucun cache — comportement original préservé.
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cacheDir: DirectoryProperty

    // CDX-OCR-1 : port AI OCR injectable (boundary rule — AI-assisted OCR is
    // actioned by the codebase socle, not by codex). The port is the existing
    // [OcrEngine] interface; the AI engine (e.g. a VisionProvider adapter from
    // codebase) is wired by the composition root. Unset by default → degraded
    // Tesseract-only mode (functional, backward compatible).
    @get:Internal
    abstract val aiEngine: Property<OcrEngine>

    // OCR-QUALITY-2 : optional acquisition-quality report. When set, the task
    // analyses each page (illisible marker, too-short body, real confidence
    // below threshold, ghost image reference) and writes a JSON [OcrQualityReport]
    // so the human iteration and the downstream RAG ingestion (OCR-QUALITY-4)
    // can target the doubtful pages. Unset by default → zero behaviour change.
    @get:OutputFile
    @get:Optional
    abstract val qualityReportFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val lowConfidenceThreshold: Property<Double>

    @TaskAction
    fun collectOcr() {
        val input = inputDir.asFile.get()
        val lang = language.getOrElse("fr")

        val images = listImageFiles(input).sortedBy { it.nameWithoutExtension }
        if (images.isEmpty()) {
            logger.lifecycle("[codex] collectOcr : no images in ${input.name}")
            // Legacy outputFile compat: write empty marker
            outputFile.orNull?.asFile?.let { out ->
                out.writeText("= [Empty OCR]\n\nNo images found in ${input.name}.\n")
            }
            return
        }

        logger.lifecycle("[codex] collectOcr : ${images.size} images in ${input.name}")

        val pipeline = aiEngine.orNull?.let { ai ->
            logger.lifecycle("[codex]           chain=AI(${ai.javaClass.simpleName}) → tesseract")
            OcrPipeline(listOf(ai, TesseractOcrEngine()))
        } ?: run {
            logger.lifecycle("[codex]           chain=tesseract-only (no AI engine injected)")
            OcrPipeline(listOf(TesseractOcrEngine()))
        }

        // US-CDX-13-1 : cache OCR par hash d'image (économise les tokens LLM).
        val cache = cacheDir.orNull?.let {
            OcrResultCache(DiskCacheStorage(it.asFile))
        }

        val pagesOut = outputDir.orNull?.asFile
        pagesOut?.mkdirs()

        // OCR-QUALITY-2 : optional acquisition-quality collection.
        val qualityIssues = mutableListOf<OcrQualityIssue>()
        val qualityThreshold = lowConfidenceThreshold.orNull ?: OcrQualityAnalyzer.DEFAULT_LOW_CONFIDENCE

        // Legacy outputFile (concatenated) — preserved for backward compatibility
        val legacyOut = outputFile.orNull?.asFile
        val sb = StringBuilder()
        if (legacyOut != null) {
            sb.appendLine("= OCR Book")
            sb.appendLine()
        }

        images.forEachIndexed { idx, imageFile ->
            val page = idx + 1
            val pageId = imageFile.nameWithoutExtension
            val imageHash = sha256(imageFile.readBytes())
            logger.lifecycle("[codex]           page $page/${images.size} — ${imageFile.name}")

            // Cache hit → réutilisation sans appel LLM (Économie d'Encre)
            val cached = cache?.lookup(pageId, imageHash)
            val result: OcrResult = if (cached != null) {
                logger.lifecycle("[codex]             ✓ cache hit (hash=$imageHash) — skip LLM")
                cached
            } else {
                // Cache miss → appel pipeline LLM → Tesseract fallback
                val request = OcrRequest(
                    imageData = imageFile.readBytes(),
                    format = detectMime(imageFile),
                    language = lang
                )
                pipeline.process(request).also { fresh ->
                    cache?.store(pageId, imageHash, fresh)
                }
            }

            val structured = result.structuredText.ifBlank { "[page vide ou OCR échec]" }

            // OCR-QUALITY-2 : analyse the real signals (confidence + text) for doubt.
            qualityIssues += OcrQualityAnalyzer.analyze(
                pageId = pageId,
                imageFile = imageFile.name,
                text = result.structuredText,
                confidence = result.confidence,
                pageDir = input,
                lowConfidenceThreshold = qualityThreshold,
            )

            // Primary output: one .adoc file per page in outputDir
            // Named NNN-<pageId>.adoc (zero-padded 3-digit prefix for PageOrder contract)
            if (pagesOut != null) {
                val pageFileName = "%03d-%s.adoc".format(page, pageId)
                File(pagesOut, pageFileName).writeText(structured)
            }

            // Legacy outputFile: concatenated with "== Page N" headers
            if (legacyOut != null) {
                sb.appendLine("== Page $page")
                sb.appendLine()
                sb.appendLine(structured)
                sb.appendLine()
            }
        }

        legacyOut?.writeText(sb.toString())

        // OCR-QUALITY-2 : write the JSON quality report when requested.
        qualityReportFile.orNull?.asFile?.let { reportFile ->
            val report = OcrQualityReport(imagesScanned = images.size, issues = qualityIssues)
            val json = Json { prettyPrint = true; prettyPrintIndent = "  " }
            reportFile.parentFile?.mkdirs()
            reportFile.writeText(json.encodeToString(OcrQualityReport.serializer(), report))
            logger.lifecycle(
                "[codex] quality report : ${qualityIssues.size} issue(s) over ${images.size} page(s)" +
                    " → ${reportFile.name}"
            )
        }

        val targets = buildString {
            if (pagesOut != null) append(" → dir ${pagesOut.name}")
            if (legacyOut != null) append(" → file ${legacyOut.name}")
        }
        logger.lifecycle("[codex] ✓ collectOcr done — ${images.size} pages$targets")
    }

    private fun listImageFiles(dir: File): List<File> {
        val extensions = setOf("png", "jpg", "jpeg", "tif", "tiff", "bmp", "gif")
        return dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in extensions } ?: emptyList()
    }

    private fun detectMime(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "tif", "tiff" -> "image/tiff"
        else -> "image/png"
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}