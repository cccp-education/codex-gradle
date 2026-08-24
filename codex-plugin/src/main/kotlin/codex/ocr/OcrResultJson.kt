package codex.ocr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File

/**
 * Jackson serialization helpers for [OcrResult] — kept in codex (N2)
 * because the N0 contract is pure Kotlin (no Jackson dependency).
 *
 * Extracted from the original `OcrResult.Companion` by EPIC
 * CDX-OCR-CONTRACTS US-2 (the companion went to N0 without Jackson).
 */
private val ocrResultMapper: ObjectMapper = ObjectMapper()
    .registerModule(KotlinModule.Builder().build())
    .enable(SerializationFeature.INDENT_OUTPUT)

fun readOcrResultFromJsonFile(file: File): OcrResult =
    ocrResultMapper.readValue(file, OcrResult::class.java)

fun writeOcrResultToJsonFile(dir: File, result: OcrResult): File {
    dir.mkdirs()
    val file = File(dir, "ocr-result.json")
    file.writeText(ocrResultMapper.writeValueAsString(result))
    return file
}