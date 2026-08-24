package codex.ocr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File

/**
 * Jackson serialization helpers for [OcrRequest] — kept in codex (N2)
 * because the N0 contract is pure Kotlin (no Jackson dependency).
 *
 * Extracted from the original `OcrRequest.Companion` by EPIC
 * CDX-OCR-CONTRACTS US-2 (the companion went to N0 without Jackson).
 */
private val ocrRequestMapper: ObjectMapper = ObjectMapper()
    .registerModule(KotlinModule.Builder().build())
    .enable(SerializationFeature.INDENT_OUTPUT)

fun readOcrRequestFromJsonFile(file: File): OcrRequest =
    ocrRequestMapper.readValue(file, OcrRequest::class.java)

fun writeOcrRequestToJsonFile(dir: File, request: OcrRequest): File {
    dir.mkdirs()
    val file = File(dir, "ocr-request.json")
    file.writeText(ocrRequestMapper.writeValueAsString(request))
    return file
}