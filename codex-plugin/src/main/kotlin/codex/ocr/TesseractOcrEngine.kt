package codex.ocr

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Moteur OCR Tesseract — OCR classique sans IA.
 *
 * Dernier maillon de la chaîne de fallback OCR :
 * Gemini (cloud IA) → Ollama (cloud IA) → Tesseract (local, sans IA).
 *
 * Wrap le CLI `tesseract` via [ProcessBuilder]. Si tesseract n'est pas installé
 * ou échoue, retourne un [OcrResult] avec texte vide et confiance 0 (best-effort,
 * ne jette pas).
 *
 * @param tesseractPath chemin du binaire tesseract (défaut : "tesseract" dans le PATH)
 */
class TesseractOcrEngine(
    private val tesseractPath: String = "tesseract"
) : OcrEngine {

    private val log = LoggerFactory.getLogger(TesseractOcrEngine::class.java)

    override fun process(request: OcrRequest): OcrResult {
        if (request.imageData.isEmpty()) {
            return emptyResult(request)
        }

        val tmpDir: Path? = try {
            Files.createTempDirectory("tesseract-ocr")
        } catch (e: Exception) {
            log.warn("[Tesseract] Cannot create temp dir: {}", e.message)
            return emptyResult(request)
        }

        val ext = when (request.format) {
            "image/png" -> ".png"
            "image/jpeg" -> ".jpg"
            "image/gif" -> ".gif"
            "image/bmp" -> ".bmp"
            "image/tiff" -> ".tiff"
            else -> ".png"
        }
        val inputFile = tmpDir!!.resolve("input$ext")
        val outputFileBase = tmpDir.resolve("output").toString()
        val langCode = mapLanguage(request.language)

        return try {
            Files.write(inputFile, request.imageData)
            val process = ProcessBuilder(
                tesseractPath, inputFile.toString(), outputFileBase, "-l", langCode
            ).redirectErrorStream(true)

            val proc = process.start()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                val output = proc.inputStream.bufferedReader().readText()
                log.warn("[Tesseract] Process exited with code {} — output: {}", exitCode, output)
                return emptyResult(request)
            }

            val outputFile = Path.of("$outputFileBase.txt")
            if (!Files.exists(outputFile)) {
                log.warn("[Tesseract] Output file not created: {}", outputFile)
                return emptyResult(request)
            }
            val text = Files.readString(outputFile)
            OcrResult(
                structuredText = text,
                confidence = if (text.isNotEmpty()) 0.7 else 0.0,
                language = request.language,
                sourceFormat = request.format,
                generatedAt = Instant.now().toString(),
                model = "tesseract",
                metadata = mapOf("engine" to "tesseract", "langCode" to langCode)
            )
        } catch (e: Exception) {
            log.warn("[Tesseract] OCR failed: {}", e.message)
            emptyResult(request)
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    private fun emptyResult(request: OcrRequest): OcrResult = OcrResult(
        structuredText = "",
        confidence = 0.0,
        language = request.language,
        sourceFormat = request.format,
        generatedAt = Instant.now().toString(),
        model = "tesseract",
        metadata = mapOf("engine" to "tesseract", "status" to "failed")
    )

    private fun mapLanguage(language: String): String = when (language.lowercase()) {
        "fr", "fra", "french" -> "fra"
        "en", "eng", "english" -> "eng"
        "de", "deu", "german" -> "deu"
        "es", "spa", "spanish" -> "spa"
        "it", "ita", "italian" -> "ita"
        "auto" -> "eng"
        else -> language.lowercase().take(3)
    }
}