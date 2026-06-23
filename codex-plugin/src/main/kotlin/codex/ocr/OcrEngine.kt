package codex.ocr

/**
 * Contrat pour un moteur OCR — transforme une [OcrRequest] en [OcrResult].
 *
 * Implémentations :
 * - [TesseractOcrEngine] : OCR classique sans IA (CLI tesseract)
 *
 * Consommé par codebase-gradle (Queens) via un adapter qui wrap l'engine
 * dans un `VisionProvider` pour la chaîne de fallback Gemini→Ollama→Tesseract.
 */
interface OcrEngine {
    fun process(request: OcrRequest): OcrResult
}