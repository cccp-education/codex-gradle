package codex.ocr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * TDD — US-CDX-13-1 : Persistance OcrResult par page (cache disque, hash image).
 *
 * Loi de l'Économie d'Encre (Regle 5 AGENT.adoc) : une opération coûteuse
 * (LLM OCR) qui a déjà produit un résultat valide et persistant NE DOIT PAS
 * être ré-exécutée sur la même entrée. Le hash de l'image source est la
 * clé d'invalidation — si le hash n'a pas changé, le résultat stocké est
 * réutilisé sans ré-invoquer le LLM.
 *
 * [OcrResultCache] est un objet pur qui délègue les I/O au caller via
 * [CacheStorage] (port injectable) — unit-testable sans disque.
 */
class OcrResultCacheTest {

    @Test
    fun `cache miss returns null when no entry exists`() {
        val storage = InMemoryCacheStorage()
        val cache = OcrResultCache(storage)

        assertNull(cache.lookup("page-001", hashOf("image-bytes")))
    }

    @Test
    fun `cache store then lookup returns the stored result`() {
        val storage = InMemoryCacheStorage()
        val cache = OcrResultCache(storage)
        val result = OcrResult.of("AsciiDoc content", 0.95, "fr", "gpt-oss:120b-cloud")

        cache.store("page-001", hashOf("image-bytes"), result)
        val cached = cache.lookup("page-001", hashOf("image-bytes"))

        assertNotNull(cached)
        assertEquals(result, cached)
    }

    @Test
    fun `cache hit only when hash matches`() {
        val storage = InMemoryCacheStorage()
        val cache = OcrResultCache(storage)
        val result = OcrResult.of("text", 0.9, "fr", "model")

        cache.store("page-001", hashOf("original"), result)

        assertNotNull(cache.lookup("page-001", hashOf("original")))
        assertNull(cache.lookup("page-001", hashOf("modified")), "Hash mismatch must invalidate cache")
    }

    @Test
    fun `cache hit only when pageId matches`() {
        val storage = InMemoryCacheStorage()
        val cache = OcrResultCache(storage)
        val result = OcrResult.of("text", 0.9, "fr", "model")

        cache.store("page-001", hashOf("bytes"), result)

        assertNotNull(cache.lookup("page-001", hashOf("bytes")))
        assertNull(cache.lookup("page-002", hashOf("bytes")), "Different pageId must miss")
    }

    @Test
    fun `cache store overwrites previous entry for same pageId and hash`() {
        val storage = InMemoryCacheStorage()
        val cache = OcrResultCache(storage)
        val first = OcrResult.of("first", 0.8, "fr", "model")
        val second = OcrResult.of("second", 0.95, "fr", "model")

        cache.store("page-001", hashOf("bytes"), first)
        cache.store("page-001", hashOf("bytes"), second)

        val cached = cache.lookup("page-001", hashOf("bytes"))
        assertEquals(second, cached, "Latest store must win")
    }

    @Test
    fun `cache store overwrites when hash changes for same pageId`() {
        val storage = InMemoryCacheStorage()
        val cache = OcrResultCache(storage)
        val original = OcrResult.of("original", 0.8, "fr", "model")
        val reprocessed = OcrResult.of("reprocessed", 0.95, "fr", "model")

        cache.store("page-001", hashOf("v1"), original)
        cache.store("page-001", hashOf("v2"), reprocessed)

        assertNull(cache.lookup("page-001", hashOf("v1")), "Old hash must be invalidated")
        assertNotNull(cache.lookup("page-001", hashOf("v2")))
        assertEquals(reprocessed, cache.lookup("page-001", hashOf("v2")))
    }

    @Test
    fun `cache entry stores the source hash for later comparison`() {
        val storage = InMemoryCacheStorage()
        val cache = OcrResultCache(storage)

        cache.store("page-001", hashOf("bytes"), OcrResult.of("text", 0.9, "fr", "model"))

        val entry = storage.read("page-001")
        assertNotNull(entry)
        assertEquals(hashOf("bytes"), entry!!.sourceHash)
    }

    @Test
    fun `cache entry stores the OcrResult payload`() {
        val storage = InMemoryCacheStorage()
        val cache = OcrResultCache(storage)
        val result = OcrResult.of("structured text", 0.92, "en", "model")

        cache.store("page-001", hashOf("bytes"), result)

        val entry = storage.read("page-001")
        assertNotNull(entry)
        assertEquals(result, entry!!.result)
    }

    @Test
    fun `cache isReusable returns true when hash matches`() {
        val storage = InMemoryCacheStorage()
        val cache = OcrResultCache(storage)

        cache.store("page-001", hashOf("bytes"), OcrResult.of("text", 0.9, "fr", "model"))

        assertTrue(cache.isReusable("page-001", hashOf("bytes")))
        assertFalse(cache.isReusable("page-001", hashOf("different")))
        assertFalse(cache.isReusable("page-unknown", hashOf("bytes")))
    }

    @Test
    fun `cache lookup returns null when storage throws`() {
        val storage = ThrowingCacheStorage()
        val cache = OcrResultCache(storage)

        assertNull(cache.lookup("page-001", hashOf("bytes")), "Storage error must degrade to miss, not throw")
    }

    @Test
    fun `cache store does not throw when storage fails`(@TempDir tempDir: Path) {
        val storage = ThrowingCacheStorage()
        val cache = OcrResultCache(storage)

        cache.store("page-001", hashOf("bytes"), OcrResult.of("text", 0.9, "fr", "model"))
    }

    private fun hashOf(content: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private class InMemoryCacheStorage : CacheStorage {
        private val map = mutableMapOf<String, CacheEntry>()

        override fun read(pageId: String): CacheEntry? = map[pageId]

        override fun write(pageId: String, entry: CacheEntry) {
            map[pageId] = entry
        }
    }

    private class ThrowingCacheStorage : CacheStorage {
        override fun read(pageId: String): CacheEntry? = throw RuntimeException("disk error")

        override fun write(pageId: String, entry: CacheEntry) = throw RuntimeException("disk error")
    }
}