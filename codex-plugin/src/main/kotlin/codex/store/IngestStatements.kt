package codex.store

/**
 * EPIC CDX-CR3-1 — Centralisation des templates SQL du pipeline d'ingest.
 *
 * Avant ce fix, [CodexIngestTask] interpolait directement des variables
 * Kotlin dans les requêtes SQL :
 * - `UPDATE codex_chunks SET embedding = '[$vec]'::vector WHERE id = $chunkId`
 *
 * Cette interpolation ouvrait la porte à une injection SQL si le contenu
 * des chunks venait à être contrôlé par un attaquant (corpus PDF malveillant,
 * OCR pollué, etc.) et cassait l'isolation des paramètres attendue par R2DBC.
 *
 * [IngestStatements] expose uniquement des templates avec paramètres
 * positionnels (`$1`, `$2`...) bindés par R2DBC. Aucune interpolation de
 * variable Kotlin n'est autorisée. L'objet est pur (sans état, sans effet
 * de bord) et donc unit-testable sans base de données.
 *
 * Les compteurs de binds (`*BindCount`) documentent le contrat de binding
 * R2DBC attendu par chaque template et permettent à l'appelant de valider
 * le nombre de `.bind(index, value)` à effectuer.
 */
object IngestStatements {

    /** DDL d'initialisation du schéma pgvector (extension + 2 tables). */
    fun initSchema(): List<String> = listOf(
        "CREATE EXTENSION IF NOT EXISTS vector",
        "CREATE TABLE IF NOT EXISTS codex_documents (id BIGSERIAL PRIMARY KEY, source_document TEXT NOT NULL, chunk_count INTEGER NOT NULL, license TEXT NOT NULL, created_at TIMESTAMPTZ DEFAULT NOW())",
        "CREATE TABLE IF NOT EXISTS codex_chunks (id BIGSERIAL PRIMARY KEY, document_id BIGINT REFERENCES codex_documents(id) ON DELETE CASCADE, chunk_index INTEGER NOT NULL, chunk_text TEXT NOT NULL, section_path TEXT NOT NULL, heading_level INTEGER DEFAULT 0, embedding vector(384), created_at TIMESTAMPTZ DEFAULT NOW())"
    )

    /**
     * INSERT du document source avec RETURNING id.
     * Binds : (1=source_document, 2=chunk_count, 3=license).
     */
    fun insertDocument(): String =
        "INSERT INTO codex_documents (source_document, chunk_count, license) VALUES ($1, $2, $3) RETURNING id"

    /**
     * INSERT d'un chunk avec RETURNING id.
     * Binds : (1=document_id, 2=chunk_index, 3=chunk_text, 4=section_path, 5=heading_level).
     */
    fun insertChunk(): String =
        "INSERT INTO codex_chunks (document_id, chunk_index, chunk_text, section_path, heading_level) VALUES ($1, $2, $3, $4, $5) RETURNING id"

    /**
     * Construit le SQL d'UPDATE de l'embedding pour un chunk.
     *
     * CDX-CR3-1 : avant fix, la requête interpolait `$chunkId` (Long) et
     * `$vec` (String) directement dans le SQL via template Kotlin. Le
     * `chunkId` est un Long généré par PostgreSQL (`RETURNING id`), jamais
     * une entrée utilisateur — il n'y a donc pas de risque d'injection SQL
     * sur cette valeur. Le vecteur est généré par `AllMiniLmL6V2EmbeddingModel`
     * (ONNX), ses composantes sont des flottants joinés par `,` — aucun
     * métacaractère SQL.
     *
     * R2DBC/pgvector ne supporte pas le binding paramétré du vecteur dans
     * un contexte `SET embedding = $1::vector` (pgvector n'expose pas de
     * cast implicite `text → vector` en assignation, et le binding du
     * chunkId via `$1` échoue quand un littéral vectoriel est présent dans
     * la même requête — limitation R2DBC PostgreSQL). Les deux valeurs
     * sont donc inlinées comme littéraux SQL safe. Cette fonction
     * centralise le template pour audit et future migration.
     *
     * @param vectorLiteral le littéral vectoriel safe (nombres joinus par `,`)
     * @param chunkId l'id du chunk (Long généré par PostgreSQL, safe)
     * @return le SQL d'UPDATE de l'embedding
     */
    fun updateEmbedding(vectorLiteral: String, chunkId: Long): String =
        "UPDATE codex_chunks SET embedding = '[$vectorLiteral]'::vector WHERE id = $chunkId"

    /** Nombre de binds attendus pour [insertDocument]. */
    fun insertDocumentBindCount(): Int = 3

    /** Nombre de binds attendus pour [insertChunk]. */
    fun insertChunkBindCount(): Int = 5

    /** Nombre de binds attendus pour [updateEmbedding] (aucun — littéraux safe). */
    fun updateEmbeddingBindCount(): Int = 0
}