package codex.ocr

/**
 * OCR fallback pipeline — orchestrates a chain of [OcrEngine] instances.
 *
 * Engines are tried in declaration order. The first engine producing a
 * non-empty [OcrResult.structuredText] with confidence > 0 wins. If an
 * engine returns empty text or zero confidence, the pipeline falls back
 * to the next one.
 *
 * Chain composition is the caller's responsibility. Since CDX-OCR-1
 * (boundary rule — AI-assisted OCR belongs to the codebase socle):
 * - with an injected AI engine: AI engine → [TesseractOcrEngine]
 * - without injection (degraded, functional): [TesseractOcrEngine] alone
 *
 * If all engines fail, returns an empty [OcrResult] with zero confidence.
 *
 * @property engines ordered list of OCR engines (fallback chain)
 */
class OcrPipeline(
    private val engines: List<OcrEngine>
) {

    /**
     * Runs the fallback chain on the given [request].
     *
     * @return the first successful [OcrResult], or an empty result if all engines fail
     */
    fun process(request: OcrRequest): OcrResult {
        for (engine in engines) {
            val result = engine.process(request)
            if (result.structuredText.isNotEmpty() && result.confidence > 0.0) {
                return result
            }
        }
        return OcrResult.of(
            text = "",
            confidence = 0.0,
            language = request.language,
            model = "pipeline",
            metadata = mapOf("engine" to "pipeline", "status" to "all-engines-failed")
        )
    }
}