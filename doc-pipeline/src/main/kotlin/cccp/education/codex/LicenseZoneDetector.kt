package codex

/**
 * License zone types detected by the codex pipeline.
 *
 * @property OSS Apache 2.0 license (open-source code under foundry/public/)
 * @property CSS Proprietary license (closed-source code under foundry/private/)
 * @property UNKNOWN Unrecognized zone
 */
enum class LicenseZone { OSS, CSS, UNKNOWN }

/**
 * Detects the license zone from the project's physical path.
 *
 * Uses path pattern matching to determine whether the code belongs
 * to the OSS (public), CSS (private), or unknown zone.
 * OSS zones are systematically tagged Apache-2.0,
 * CSS zones are tagged PROPRIETARY.
 */
object LicenseZoneDetector {

    /**
     * Detects the license zone from a project directory path.
     *
     * @param projectDir absolute path to the project directory (e.g. "/workspace/foundry/public/codex-gradle")
     * @return [LicenseZone.OSS] if the path contains "foundry/public/",
     *         [LicenseZone.CSS] if "foundry/private/",
     *         [LicenseZone.UNKNOWN] otherwise
     */
    fun detect(projectDir: String): LicenseZone {
        val normalized = projectDir.replace("\\", "/")
        return when {
            normalized.contains("/foundry/public/") -> LicenseZone.OSS
            normalized.contains("/foundry/private/") -> LicenseZone.CSS
            else -> LicenseZone.UNKNOWN
        }
    }

    /**
     * Converts a license zone to a human-readable license name.
     *
     * @param zone the license zone to convert
     * @return "Apache-2.0" for OSS, "PROPRIETARY" for CSS, "UNKNOWN" otherwise
     */
    fun toLicenseName(zone: LicenseZone): String = when (zone) {
        LicenseZone.OSS -> "Apache-2.0"
        LicenseZone.CSS -> "PROPRIETARY"
        LicenseZone.UNKNOWN -> "UNKNOWN"
    }
}
