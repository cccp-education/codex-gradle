package codex.profile

/**
 * EPIC CDX-RC-04-1 — Templates SQL du pont RAG session memory.
 *
 * Centralise les templates SQL de la table `codex_learner_profiles`
 * (pattern `IngestStatements` CDX-CR3-1). Aucune interpolation de variable
 * Kotlin n'est autorisée — uniquement des paramètres positionnels (`$1`...)
 * bindés par R2DBC, ou des littéraux safe (vector généré par ONNX,
 * composite key String validée non-blank par `LearnerProfile.init`).
 *
 * La table `codex_learner_profiles` est séparée de `codex_documents`/
 * `codex_chunks` : le profil stagiaire n'est pas un document du corpus,
 * c'est de la mémoire de session. Mélanger les deux casserait la recherche
 * sémantique (un Retrieve sur un module pédagogique retournerait le profil
 * du stagiaire — bruit). Clé composite `(learner_id, formation_id)`.
 */
object ProfileStatements {

    /**
     * DDL d'initialisation du schéma session memory (extension + table dédiée).
     *
     * L'extension `vector` est créée `IF NOT EXISTS` — elle peut déjà exister
     * si le pipeline documentaire (`IngestStatements.initSchema`) a déjà tourné
     * sur la même base `codex`.
     */
    fun initSchema(): List<String> = listOf(
        "CREATE EXTENSION IF NOT EXISTS vector",
        """
        CREATE TABLE IF NOT EXISTS codex_learner_profiles (
            learner_id TEXT NOT NULL,
            formation_id TEXT NOT NULL,
            completed_modules JSONB NOT NULL DEFAULT '[]',
            current_module TEXT,
            progression_pct DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            comprehension_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            weak_points JSONB NOT NULL DEFAULT '[]',
            last_interaction_at TEXT,
            annotations JSONB NOT NULL DEFAULT '{}',
            embedding vector(384),
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            PRIMARY KEY (learner_id, formation_id)
        )
        """.trimIndent()
    )

    /**
     * UPSERT du profil stagiaire sur la clé composite `(learner_id, formation_id)`.
     *
     * Binds (9) : (1=learner_id, 2=formation_id, 3=completed_modules JSONB,
     * 4=current_module, 5=progression_pct, 6=comprehension_score,
     * 7=weak_points JSONB, 8=last_interaction_at, 9=annotations JSONB).
     *
     * L'embedding n'est PAS set ici — il est calculé par ONNX puis mis à jour
     * via [updateEmbedding] (limitation R2DBC pgvector : pas de cast implicite
     * text→vector en SET, le vecteur est inliné comme littéral safe).
     */
    fun upsertProfile(): String =
        """
        INSERT INTO codex_learner_profiles (
            learner_id, formation_id, completed_modules, current_module,
            progression_pct, comprehension_score, weak_points,
            last_interaction_at, annotations
        ) VALUES ($1, $2, $3::jsonb, $4, $5, $6, $7::jsonb, $8, $9::jsonb)
        ON CONFLICT (learner_id, formation_id) DO UPDATE SET
            completed_modules = EXCLUDED.completed_modules,
            current_module = EXCLUDED.current_module,
            progression_pct = EXCLUDED.progression_pct,
            comprehension_score = EXCLUDED.comprehension_score,
            weak_points = EXCLUDED.weak_points,
            last_interaction_at = EXCLUDED.last_interaction_at,
            annotations = EXCLUDED.annotations,
            updated_at = NOW()
        """.trimIndent()

    /** Nombre de binds attendus pour [upsertProfile]. */
    fun upsertProfileBindCount(): Int = 9

    /**
     * UPDATE de l'embedding du profil.
     *
     * Le vecteur est inliné comme littéral safe (nombres joinus par `,`,
     * générés par ONNX — aucun métacaractère SQL). La clé composite est
     * inlinée (String validée non-blank par `LearnerProfile.init`).
     *
     * @param vectorLiteral le littéral vectoriel safe (nombres joinus par `,`)
     * @param learnerId l'id stagiaire (String non-blank validé)
     * @param formationId l'id formation (String non-blank validé)
     */
    fun updateEmbedding(vectorLiteral: String, learnerId: String, formationId: String): String =
        "UPDATE codex_learner_profiles SET embedding = '[$vectorLiteral]'::vector, updated_at = NOW() WHERE learner_id = '$learnerId' AND formation_id = '$formationId'"

    /** Nombre de binds attendus pour [updateEmbedding] (aucun — littéraux safe). */
    fun updateEmbeddingBindCount(): Int = 0

    /**
     * SELECT d'un profil par clé composite.
     *
     * Binds (2) : (1=learner_id, 2=formation_id).
     */
    fun selectProfile(): String =
        """
        SELECT
            learner_id, formation_id, completed_modules, current_module,
            progression_pct, comprehension_score, weak_points,
            last_interaction_at, annotations
        FROM codex_learner_profiles
        WHERE learner_id = $1 AND formation_id = $2
        """.trimIndent()

    /** Nombre de binds attendus pour [selectProfile]. */
    fun selectProfileBindCount(): Int = 2
}