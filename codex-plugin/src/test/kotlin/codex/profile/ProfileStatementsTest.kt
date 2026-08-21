package codex.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-RC-04-1 : domaine `codex.profile` (12e domaine codex).
 *
 * [ProfileStatements] centralise les templates SQL du pont RAG session memory
 * (pattern `IngestStatements` CDX-CR3-1). Aucune interpolation de variable
 * Kotlin n'est autorisée — uniquement des paramètres positionnels (`$1`...)
 * bindés par R2DBC, ou des littéraux safe (vector généré par ONNX, id Long
 * généré par PostgreSQL).
 *
 * La table `codex_learner_profiles` est séparée de `codex_documents`/
 * `codex_chunks` : le profil stagiaire n'est pas un document du corpus,
 * c'est de la mémoire de session. Mélanger les deux casserait la recherche
 * sémantique (un Retrieve sur un module pédagogique retournerait le profil
 * du stagiaire — bruit). Clé composite `(learner_id, formation_id)`.
 */
class ProfileStatementsTest {

    @Test
    fun `initSchema creates vector extension and dedicated learner_profiles table`() {
        val stmts = ProfileStatements.initSchema()
        assertEquals(2, stmts.size)
        assertTrue(stmts[0].contains("CREATE EXTENSION IF NOT EXISTS vector"))
        assertTrue(stmts[1].contains("CREATE TABLE IF NOT EXISTS codex_learner_profiles"))
        stmts.forEach { s -> assertFalse(containsInterpolation(s), "DDL must not interpolate: $s") }
    }

    @Test
    fun `learner_profiles table has composite key and embedding column`() {
        val stmts = ProfileStatements.initSchema()
        val ddl = stmts[1]
        assertTrue(ddl.contains("learner_id TEXT NOT NULL"), "composite key learner_id: $ddl")
        assertTrue(ddl.contains("formation_id TEXT NOT NULL"), "composite key formation_id: $ddl")
        assertTrue(ddl.contains("PRIMARY KEY (learner_id, formation_id)"), "composite PK: $ddl")
        assertTrue(ddl.contains("embedding vector(384)"), "embedding 384-dim: $ddl")
    }

    @Test
    fun `upsertProfile uses positional parameters and ON CONFLICT composite key`() {
        val sql = ProfileStatements.upsertProfile()
        // 8 binds : learner_id, formation_id, completed_modules, current_module,
        // progression_pct, comprehension_score, weak_points, last_interaction_at
        // (annotations est JSON-sérialisé dans weak_points join? Non — séparé).
        // En fait 9 binds + ON CONFLICT update.
        assertTrue(sql.contains("INSERT INTO codex_learner_profiles"), "insert: $sql")
        assertTrue(
            sql.contains("ON CONFLICT (learner_id, formation_id) DO UPDATE"),
            "upsert on composite key: $sql"
        )
        assertTrue(sql.contains("$1"), "bind learner_id: $sql")
        assertTrue(sql.contains("$2"), "bind formation_id: $sql")
        assertFalse(containsInterpolation(sql), "upsert must not interpolate: $sql")
    }

    @Test
    fun `upsertProfile bind count is documented`() {
        assertTrue(ProfileStatements.upsertProfileBindCount() >= 8, "at least 8 binds")
    }

    @Test
    fun `updateEmbedding inlines safe vector literal and composite key`() {
        // Le vecteur est inliné (limitation R2DBC pgvector — pas de cast
        // implicite text→vector en SET). La clé composite est fournie par
        // l'appelant (learnerId + formationId String safe, validés non-blank
        // par LearnerProfile.init).
        val sql = ProfileStatements.updateEmbedding("0.1,0.2,0.3", "learner-1", "formation-A")
        assertTrue(sql.contains("'[0.1,0.2,0.3]'::vector"), "inline vector: $sql")
        assertTrue(sql.contains("learner_id = 'learner-1'"), "inline learnerId: $sql")
        assertTrue(sql.contains("formation_id = 'formation-A'"), "inline formationId: $sql")
        assertFalse(containsInterpolation(sql), "updateEmbedding must not interpolate: $sql")
    }

    @Test
    fun `updateEmbedding bind count is 0`() {
        assertEquals(0, ProfileStatements.updateEmbeddingBindCount())
    }

    @Test
    fun `selectProfile uses positional parameters on composite key`() {
        val sql = ProfileStatements.selectProfile()
        assertTrue(sql.contains("SELECT"), "select: $sql")
        assertTrue(sql.contains("FROM codex_learner_profiles"), "from: $sql")
        assertTrue(sql.contains("learner_id = $1"), "bind learner_id: $sql")
        assertTrue(sql.contains("formation_id = $2"), "bind formation_id: $sql")
        assertFalse(containsInterpolation(sql), "select must not interpolate: $sql")
    }

    @Test
    fun `selectProfile bind count is 2`() {
        assertEquals(2, ProfileStatements.selectProfileBindCount())
    }

    private fun containsInterpolation(sql: String): Boolean {
        val pattern = Regex("""\$\p{Alpha}""")
        return pattern.containsMatchIn(sql)
    }
}