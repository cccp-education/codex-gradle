package codex.tasks

import codex.ocr.HttpOllamaChatClient
import codex.ocr.LlmOcrEngine
import codex.ocr.OcrConfig
import codex.ocr.OcrPipeline
import codex.ocr.OcrRequest
import codex.ocr.OcrResult
import codex.ocr.TesseractOcrEngine
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Collects OCR results from a directory of page images.
 *
 * For each image in [inputDir], runs the [OcrPipeline] (LLM → Tesseract fallback)
 * and produces a single concatenated AsciiDoc document in [outputFile], with one
 * section per page. This is the contract consumed by document-gradle DOC-11
 * (Pipeline Livre) to assemble a full book from photos of pages.
 *
 * Inputs:
 * - [inputDir] directory containing image files (.png, .jpg, .jpeg, .tif, .tiff, .bmp)
 * - [ollamaHost] Ollama server host (default: localhost)
 * - [ollamaPort] Ollama server port (default: 11437, rotation range 11437-11465)
 * - [model] LLM vision model (default: gpt-oss:120b-cloud)
 * - [language] ISO 639-1 language hint (default: fr)
 *
 * Output:
 * - [outputFile] single AsciiDoc file with all OCRised pages concatenated
 *
 * Each page is emitted as:
 * ----
 * == Page N
 *
 * <structuredText from OCR>
 * ----
 */
@DisableCachingByDefault(because = "LLM OCR call — external metered API, non-cacheable (Loi de l'Économie d'Encre)")
abstract class CollectOcrTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputFile
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

    @TaskAction
    fun collectOcr() {
        val input = inputDir.asFile.get()
        val output = outputFile.asFile.get()
        val lang = language.getOrElse("fr")
        val host = ollamaHost.getOrElse("localhost")
        val port = ollamaPort.getOrElse("11437").toInt()
        val modelName = model.getOrElse("gpt-oss:120b-cloud")

        val images = listImageFiles(input).sortedBy { it.nameWithoutExtension }
        if (images.isEmpty()) {
            logger.lifecycle("[codex] collectOcr : no images in ${input.name} — empty output")
            output.writeText("= [Empty OCR]\n\nNo images found in ${input.name}.\n")
            return
        }

        logger.lifecycle("[codex] collectOcr : ${images.size} images in ${input.name} → ${output.name}")
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

        val sb = StringBuilder()
        sb.appendLine("= OCR Book")
        sb.appendLine()

        images.forEachIndexed { idx, imageFile ->
            val page = idx + 1
            logger.lifecycle("[codex]           page $page/${images.size} — ${imageFile.name}")
            val request = OcrRequest(
                imageData = imageFile.readBytes(),
                format = detectMime(imageFile),
                language = lang
            )
            val result: OcrResult = pipeline.process(request)
            sb.appendLine("== Page $page")
            sb.appendLine()
            sb.appendLine(result.structuredText.ifBlank { "[page vide ou OCR échec]" })
            sb.appendLine()
        }

        output.writeText(sb.toString())
        logger.lifecycle("[codex] ✓ collectOcr done — ${output.length()} bytes, ${images.size} pages")
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
}