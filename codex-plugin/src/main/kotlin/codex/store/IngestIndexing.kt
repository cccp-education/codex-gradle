package codex.store

import codex.tasks.DocumentChunk

/**
 * EPIC CDX-CR3-3 — Assignation des `chunk_index` locaux par document.
 *
 * Avant ce fix, [CodexIngestTask] utilisait `chunks.indexOf(chunk)` sur la
 * liste globale des chunks pour déterminer le `chunk_index` à insérer en
 * base. `List.indexOf` s'appuie sur l'égalité structurelle (`equals` du
 * `data class DocumentChunk`) et retourne la **première occurrence** :
 *
 * - deux chunks identiques (même contenu, même sectionPath, même
 *   headingLevel) recevaient le même `chunk_index` → l'ordre de lecture du
 *   corpus était corrompu et la ré-ingestion silencieuse écrasait le
 *   premier chunk au lieu d'ajouter le second ;
 * - l'index était global (tous documents confondus) alors que le schéma
 *   attend un index local au document (`document_id` + `chunk_index`).
 *
 * [IngestIndexing] calcule les index locaux via un simple `withIndex()`
 * par groupe de document, indépendant de l'égalité structurelle des
 * chunks. L'objet est pur (sans état, sans effet de bord) et donc
 * unit-testable sans base de données.
 */
object IngestIndexing {

    /**
     * Associe chaque `sourceDocument` à la liste de ses `chunk_index`
     * locaux (0, 1, 2...), dans l'ordre d'apparition.
     *
     * @param chunks liste globale des chunks (potentiellement multi-documents)
     * @return map `sourceDocument → List<Int>` des index locaux séquentiels
     */
    fun assignLocalIndices(chunks: List<DocumentChunk>): Map<String, List<Int>> =
        chunks
            .groupBy { it.sourceDocument }
            .mapValues { (_, docChunks) -> docChunks.indices.toList() }
}