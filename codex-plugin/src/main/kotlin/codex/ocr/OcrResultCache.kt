package codex.ocr

/**
 * Port de stockage du cache OCR — injectable pour testabilité.
 *
 * L'implémentation par défaut ([DiskCacheStorage]) persiste les entrées
 * sur disque (JSON). En test, une implémentation in-memory suffit.
 */
interface CacheStorage {

    /** Lit l'entrée de cache pour une page, ou null si absente. */
    fun read(pageId: String): CacheEntry?

    /** Écrit (ou écrase) l'entrée de cache pour une page. */
    fun write(pageId: String, entry: CacheEntry)
}

/**
 * Entrée de cache persistée — associe le hash de l'image source au
 * résultat OCR produit.
 *
 * @property sourceHash hash SHA-256 du contenu de l'image source
 * @property result le [OcrResult] produit par le pipeline OCR
 */
data class CacheEntry(
    val sourceHash: String,
    val result: OcrResult
)

/**
 * US-CDX-13-1 — Cache OCR par hash d'image (Loi de l'Économie d'Encre).
 *
 * Avant ce cache, [CollectOcrTask] re-OCRoisait toutes les images à
 * chaque exécution, même si l'image n'avait pas changé. Le cache
 * évite de ré-invoquer le LLM (service metered) sur les entrées
 * déjà traitées.
 *
 * La clé d'invalidation est le hash SHA-256 du contenu de l'image.
 * Si le hash stocké correspond au hash courant, le résultat est
 * réutilisé. Sinon, l'image est re-traitée et le cache écrasé.
 *
 * [OcrResultCache] est un objet pur qui délègue les I/O au
 * [CacheStorage] injecté — unit-testable sans disque. En cas
 * d'erreur de stockage (disque plein, permissions), le cache
 * dégrade silencieusement en miss (pas de crash du pipeline).
 */
class OcrResultCache(private val storage: CacheStorage) {

    /**
     * Cherche un résultat en cache pour la page et le hash donnés.
     *
     * @return le [OcrResult] si cache hit (hash match), null sinon
     *         (miss, pageId inconnu, hash différent, ou erreur stockage)
     */
    fun lookup(pageId: String, sourceHash: String): OcrResult? {
        val entry = try {
            storage.read(pageId)
        } catch (e: Exception) {
            null
        } ?: return null
        return if (entry.sourceHash == sourceHash) entry.result else null
    }

    /**
     * Stocke (ou écrase) le résultat OCR pour la page et le hash donnés.
     *
     * En cas d'erreur de stockage, l'écriture est silencieusement ignorée
     * (dégradé — le pipeline continuera sans cache, pas de crash).
     */
    fun store(pageId: String, sourceHash: String, result: OcrResult) {
        try {
            storage.write(pageId, CacheEntry(sourceHash, result))
        } catch (e: Exception) {
            // Dégradé silencieux — le cache est une optimisation, pas une contrainte.
        }
    }

    /**
     * Indique si le cache peut être réutilisé pour la page et le hash donnés.
     *
     * @return true si [lookup] retournerait un résultat non-null
     */
    fun isReusable(pageId: String, sourceHash: String): Boolean =
        lookup(pageId, sourceHash) != null
}