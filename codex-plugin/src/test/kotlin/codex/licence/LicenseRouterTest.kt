package codex.licence

import codex.LicenseZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * TDD — EPIC CDX-5-1 : Domaine `codex.licence` + `LicenseRouter`.
 *
 * `LicenseRouter.route(zone, baseDir)` resolves the physical output directory
 * for the given license zone:
 *
 *  - [LicenseZone.OSS]     -> `baseDir/OSS/`
 *  - [LicenseZone.CSS]     -> `baseDir/office/`
 *  - [LicenseZone.UNKNOWN] -> `baseDir/office/` (closed by default — precaution)
 *
 * The router is an object pur: deterministic, side-effect free. It only
 * computes the target path; it does NOT create directories on disk.
 *
 * Baby-step TDD strict RED (type absent) -> GREEN -> REFACTOR.
 */
class LicenseRouterTest {

    private val base = File("/workspace/output")

    @Test
    fun `OSS zone routes to OSS directory`() {
        assertEquals(File(base, "OSS"), LicenseRouter.route(LicenseZone.OSS, base))
    }

    @Test
    fun `CSS zone routes to office directory`() {
        assertEquals(File(base, "office"), LicenseRouter.route(LicenseZone.CSS, base))
    }

    @Test
    fun `UNKNOWN zone routes to office directory as precaution`() {
        assertEquals(File(base, "office"), LicenseRouter.route(LicenseZone.UNKNOWN, base))
    }

    @Test
    fun `route does not create directories`() {
        val tempBase = File(System.getProperty("java.io.tmpdir"), "codex-router-test-${System.nanoTime()}")
        assertEquals(File(tempBase, "OSS"), LicenseRouter.route(LicenseZone.OSS, tempBase))
        // The router must not have created anything on disk.
        assertEquals(false, tempBase.exists())
    }
}