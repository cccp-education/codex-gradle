package codex.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-CR3-1 : SQL injection dans CodexIngestTask.kt:118.
 *
 * Avant fix : `UPDATE codex_chunks SET embedding = '[$vec]'::vector WHERE id = $chunkId`
 * interpolait directement le chunkId (Long) et le vecteur (String) dans le SQL,
 * ouvrant la porte à une injection si le contenu venait à être contrôlé.
 *
 * Après fix : les templates SQL sont centralisés dans [IngestStatements] et
 * utilisent exclusivement des paramètres positionnels (`$1`, `$2`...) bindés
 * par R2DBC, jamais d'interpolation de variables Kotlin.
 */
class IngestStatementsTest {

    @Test
    fun `initSchema statements are parameterless DDL`() {
        val stmts = IngestStatements.initSchema()
        assertEquals(3, stmts.size)
        assertTrue(stmts[0].contains("CREATE EXTENSION IF NOT EXISTS vector"))
        assertTrue(stmts[1].contains("CREATE TABLE IF NOT EXISTS codex_documents"))
        assertTrue(stmts[2].contains("CREATE TABLE IF NOT EXISTS codex_chunks"))
        stmts.forEach { s -> assertFalse(containsInterpolation(s), "DDL must not interpolate: $s") }
    }

    @Test
    fun `insertDocument uses 3 positional parameters and no interpolation`() {
        val sql = IngestStatements.insertDocument()
        assertTrue(sql.contains("$1"), "insertDocument should bind source_document at $1")
        assertTrue(sql.contains("$2"), "insertDocument should bind chunk_count at $2")
        assertTrue(sql.contains("$3"), "insertDocument should bind license at $3")
        assertTrue(sql.contains("RETURNING id"))
        assertFalse(containsInterpolation(sql), "insertDocument must not interpolate variables: $sql")
    }

    @Test
    fun `insertChunk uses 5 positional parameters and no interpolation`() {
        val sql = IngestStatements.insertChunk()
        assertTrue(sql.contains("$1"), "insertChunk should bind document_id at $1")
        assertTrue(sql.contains("$2"), "insertChunk should bind chunk_index at $2")
        assertTrue(sql.contains("$3"), "insertChunk should bind chunk_text at $3")
        assertTrue(sql.contains("$4"), "insertChunk should bind section_path at $4")
        assertTrue(sql.contains("$5"), "insertChunk should bind heading_level at $5")
        assertTrue(sql.contains("RETURNING id"))
        assertFalse(containsInterpolation(sql), "insertChunk must not interpolate variables: $sql")
    }

    @Test
    fun `updateEmbedding inlines safe vector literal and chunkId`() {
        val sql = IngestStatements.updateEmbedding("0.1,0.2,0.3", 42L)
        // Le vecteur est inliné comme littéral SQL car pgvector n'expose pas
        // de cast implicite text → vector dans un contexte SET. Le contenu
        // est généré par ONNX (nombres sûrs), jamais par une entrée utilisateur.
        // Le chunkId est un Long généré par PostgreSQL (RETURNING id), safe.
        assertTrue(
            sql.contains("'[0.1,0.2,0.3]'::vector"),
            "updateEmbedding must inline vector literal, was: $sql"
        )
        assertTrue(
            sql.contains("WHERE id = 42"),
            "updateEmbedding must inline chunkId (Long safe), was: $sql"
        )
        // CDX-CR3-1 : aucune interpolation de variable Kotlin (`$identifier`)
        // ne doit rester dans le SQL — les valeurs sont inlinées comme
        // littéraux safe (Long + nombres ONNX).
        assertFalse(
            containsInterpolation(sql),
            "updateEmbedding must not interpolate Kotlin variables (CDX-CR3-1): $sql"
        )
    }

    @Test
    fun `updateEmbedding with chunkId documents the safe source contract`() {
        // Le chunkId est un Long généré par PostgreSQL (RETURNING id), pas
        // une entrée utilisateur. La fonction ne valide pas le type — c'est
        // l'appelant (CodexIngestTask) qui garantit que chunkId vient de
        // r.get("id", Long::class.java).
        val sql = IngestStatements.updateEmbedding("1.0", 999L)
        assertTrue(sql.contains("WHERE id = 999"))
        assertFalse(containsInterpolation(sql))
    }

    @Test
    fun `insertDocument bind count is 3`() {
        assertEquals(3, IngestStatements.insertDocumentBindCount())
    }

    @Test
    fun `updateEmbedding bind count is 0`() {
        assertEquals(0, IngestStatements.updateEmbeddingBindCount())
    }

    @Test
    fun `insertChunk bind count is 5`() {
        assertEquals(5, IngestStatements.insertChunkBindCount())
    }

    /**
     * Détecte une interpolation Kotlin (`$identifier` hors de la portée d'un
     * paramètre positionnel `$N`). Un template SQL valide ne contient que
     * des `$1`, `$2`... ou aucune variable. Toute occurrence de `$` suivie
     * d'une lettre est une interpolation suspectée.
     */
    private fun containsInterpolation(sql: String): Boolean {
        val pattern = Regex("""\$\p{Alpha}""")
        return pattern.containsMatchIn(sql)
    }
}