package codex.profile

/**
 * EPIC CDX-RC-04-1 — Configuration de connexion pgvector session memory.
 *
 * Data class pur (mirror `CodexVectorStore` / `CodexIngestTask` params)
 * portant les credentials de la base `codex`. La table `codex_learner_profiles`
 * vit dans la même base que `codex_documents`/`codex_chunks` (voir
 * [ProfileStatements.initSchema]) mais est séparée logiquement.
 *
 * Objet pur (sans effet de bord) — unit-testable sans base de données.
 *
 * @property host PostgreSQL host (default: "localhost")
 * @property port PostgreSQL port (default: 5432)
 * @property database database name (default: "codex")
 * @property username PostgreSQL username (default: "codex")
 * @property password PostgreSQL password (default: "codex")
 */
data class ProfileConfig(
    val host: String = "localhost",
    val port: Int = 5432,
    val database: String = "codex",
    val username: String = "codex",
    val password: String = "codex"
)