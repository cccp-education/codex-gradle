package codex.tasks

import codex.ocr.DiskCacheStorage
import codex.ocr.HttpOllamaChatClient
import codex.ocr.LlmOcrEngine
import codex.ocr.OcrConfig
import codex.ocr.OcrPipeline
import codex.ocr.OcrRequest
import codex.ocr.OcrResult
import codex.ocr.OcrResultCache
import codex.ocr.TesseractOcrEngine
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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
 * For each image in [inputDir], runs the [OcrPipeline] (LLM → Tesseract fallback)
 * and produces one AsciiDoc file per page in [outputDir] (the N2 ↔ N2 bridge
 * with document-gradle DOC-11). This is the contract consumed by document-gradle
 * `BookAssembler` (New Orleans) to assemble a full book from photos of pages.
 *
 * Inputs:
 * - [inputDir] directory containing image files (.png, .jpg, .jpeg, .tif, .tiff, .bmp)
 * - [ollamaHost] Ollama server host (default: localhost)
 * - [ollamaPort] Ollama server port (default: 11437, rotation range 11437-11465)
 * - [model] LLM vision model (default: gpt-oss:120b-cloud)
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

    @get:OutputDirectory
    @get:Optional
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val ollamaHost: Property<String>

    @get:Input
    @get:Optional
    abstract val ollamaPort: Property<String>

    @get:Input
    @get:Optional
    abstract val model: Property<String>

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

    @TaskAction
    fun collectOcr() {
        val input = inputDir.asFile.get()
        val lang = language.getOrElse("fr")
        val host = ollamaHost.getOrElse("localhost")
        val port = ollamaPort.getOrElse("11437").toInt()
        val modelName = model.getOrElse("gpt-oss:120b-cloud")

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
        logger.lifecycle("[codex]           model=$modelName host=$host port=$port lang=$lang")

        val config = OcrConfig(
            provider = "ollama",
            model = modelName,
            maxTokens = 4096,
            temperature = 0.0,
            endpoint = "http://$host:$port"
        )
        val pipeline = OcrPipeline(
            listOf(
                LlmOcrEngine(HttpOllamaChatClient(host, port), config),
                TesseractOcrEngine()
            )
        )

        // US-CDX-13-1 : cache OCR par hash d'image (économise les tokens LLM).
        val cache = cacheDir.orNull?.let {
            OcrResultCache(DiskCacheStorage(it.asFile))
        }

        val pagesOut = outputDir.orNull?.asFile
        pagesOut?.mkdirs()

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