package codex

import org.gradle.api.provider.Property

/**
 * Gradle extension `codex { ... }` for configuring the document pipeline.
 *
 * Allows setting the license zone and PostgreSQL/pgvector connection parameters.
 *
 * ```kotlin
 * codex {
 *     zone = LicenseZone.OSS
 *     pgvectorHost = "localhost"
 *     pgvectorPort = "5432"
 * }
 * ```
 *
 * @property zone project license zone (OSS/CSS/UNKNOWN)
 * @property pgvectorHost PostgreSQL/pgvector server host (default: "localhost")
 * @property pgvectorPort PostgreSQL/pgvector server port (default: "5432")
 * @property pgvectorDatabase database name (default: "codex")
 * @property pgvectorUser PostgreSQL username (default: "codex")
 * @property pgvectorPassword PostgreSQL password (default: "codex")
 */
abstract class CodexExtension {
    abstract val zone: Property<LicenseZone>

    abstract val pgvectorHost: Property<String>
    abstract val pgvectorPort: Property<String>
    abstract val pgvectorDatabase: Property<String>
    abstract val pgvectorUser: Property<String>
    abstract val pgvectorPassword: Property<String>
}
