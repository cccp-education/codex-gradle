package codex.licence

import codex.LicenseZone
import java.io.File

/**
 * Routes a document to the physical output directory matching its license zone.
 *
 *  - [LicenseZone.OSS]     -> `baseDir/OSS/`
 *  - [LicenseZone.CSS]     -> `baseDir/office/`
 *  - [LicenseZone.UNKNOWN] -> `baseDir/office/` (closed by default — precaution)
 *
 * The router is a pure object: it only computes the target path. It does NOT
 * create directories on disk — callers are responsible for materializing the
 * resolved directory.
 */
object LicenseRouter {

    /**
     * Resolves the output directory for the given license zone.
     *
     * @param zone the license zone detected from the document.
     * @param baseDir the base directory under which zone subdirectories live.
     * @return the resolved target directory (not created on disk).
     */
    fun route(zone: LicenseZone, baseDir: File): File = when (zone) {
        LicenseZone.OSS -> File(baseDir, "OSS")
        LicenseZone.CSS, LicenseZone.UNKNOWN -> File(baseDir, "office")
    }
}