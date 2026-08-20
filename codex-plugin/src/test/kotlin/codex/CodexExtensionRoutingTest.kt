package codex

import codex.tasks.CodexPipelineTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-6-2 : DSL `codex { licenceRouting = true }` + wiring.
 *
 * Avant CDX-6-2 : `CodexExtension` n'expose pas `licenceRouting`.
 * `CodexPipelineTask.licenceRouting` n'est pas wire depuis l'extension.
 *
 * Après CDX-6-2 : `CodexExtension` +1 property `licenceRouting: Property<Boolean>`
 * (default false). `CodexPlugin` wire `transformCorpusToPdf.licenceRouting`
 * depuis `extension.licenceRouting`. Quand `true`, la pipeline route selon
 * `PdfLicenseDetector`. Quand `false`, comportement actuel (backward compat).
 *
 * Baby-step TDD strict : RED → GREEN → REFACTOR.
 */
class CodexExtensionRoutingTest {

    @Test
    fun `extension exposes licenceRouting property`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("codex", CodexExtension::class.java)

        assertNotNull(extension.licenceRouting, "CodexExtension should expose licenceRouting property")
    }

    @Test
    fun `extension licenceRouting is writable and readable`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("codex", CodexExtension::class.java)

        extension.licenceRouting.convention(true)
        assertTrue(extension.licenceRouting.get(), "licenceRouting should be true after convention set")
    }

    @Test
    fun `plugin sets licenceRouting convention to false on extension`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val extension = project.extensions.findByName("codex") as CodexExtension
        assertFalse(extension.licenceRouting.get(), "Plugin should default licenceRouting to false (backward compat)")
    }

    @Test
    fun `plugin wires extension licenceRouting to transformCorpusToPdf task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val extension = project.extensions.findByName("codex") as CodexExtension
        extension.licenceRouting.set(true)

        val task = project.tasks.findByName("transformCorpusToPdf") as CodexPipelineTask
        assertTrue(task.licenceRouting.get(), "transformCorpusToPdf.licenceRouting should reflect extension value")
    }

    @Test
    fun `plugin wires extension fallbackZone to transformCorpusToPdf task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val extension = project.extensions.findByName("codex") as CodexExtension
        extension.zone.set(LicenseZone.OSS)

        val task = project.tasks.findByName("transformCorpusToPdf") as CodexPipelineTask
        assertEquals(LicenseZone.OSS, task.fallbackZone.get(), "fallbackZone should be wired from extension.zone")
    }
}