package codex.ocr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File

/**
 * Implémentation disque de [CacheStorage] — persiste chaque entrée de
 * cache comme un fichier JSON `<pageId>.ocr-cache.json` dans un
 * répertoire dédié.
 *
 * Format JSON :
 * ```json
 * {
 *   "sourceHash": "<sha256>",
 *   "result": { ... OcrResult ... }
 * }
 * ```
 *
 * En cas d'erreur de lecture (fichier corrompu, format invalide),
 * [read] retourne null (dégradé miss) — le pipeline re-OCRoisera
 * l'image plutôt que de crasher.
 */
class DiskCacheStorage(private val cacheDir: File) : CacheStorage {

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .enable(SerializationFeature.INDENT_OUTPUT)

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    override fun read(pageId: String): CacheEntry? {
        val file = cacheFile(pageId)
        if (!file.exists()) return null
        return try {
            mapper.readValue(file, CacheEntry::class.java)
        } catch (e: Exception) {
            null
        }
    }

    override fun write(pageId: String, entry: CacheEntry) {
        val file = cacheFile(pageId)
        mapper.writeValue(file, entry)
    }

    private fun cacheFile(pageId: String): File =
        File(cacheDir, "$pageId.ocr-cache.json")
}