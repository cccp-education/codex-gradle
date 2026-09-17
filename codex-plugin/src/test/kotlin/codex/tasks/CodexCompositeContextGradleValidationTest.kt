package codex.tasks

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional (Gradle TestKit) — CDX-CONTEXT-HARDENING-1.
 *
 * The S-220 bug was a *Gradle input validation* failure: the plugin wired the
 * `enrichedJsonFile` `@InputFile` unconditionally, so a standalone
 * `generateCompositeContext` failed with `Input file does not exist` before
 * the task action ever ran. `ProjectBuilder` does not run that validation, so
 * this suite exercises a real Gradle build.
 *
 * The task action would need a live pgvector; the probe overrides it with a
 * `doFirst` sentinel. Reaching the sentinel proves the input validation
 * passed — before the fix the build failed before `doFirst`.
 */
class CodexCompositeContextGradleValidationTest {

    @TempDir
    lateinit var projectDir: File

    private fun settingsFile() = File(projectDir, "settings.gradle.kts").apply {
        writeText("""rootProject.name = "codex-hardening-probe"""")
    }

    private fun buildFile() = File(projectDir, "build.gradle.kts").apply {
        writeText(
            """
            plugins {
                id("education.cccp.codex")
            }

            // Probe: reaching the action proves Gradle input validation passed.
            // The real action needs pgvector — it is never reached.
            tasks.named("generateCompositeContext") {
                doFirst { throw GradleException("HARDENING_PROBE_REACHED") }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `standalone generateCompositeContext passes input validation without enrichJsonLdd`() {
        settingsFile()
        buildFile()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateCompositeContext", "--stacktrace")
            .buildAndFail()

        val output = result.output
        assertTrue(
            output.contains("HARDENING_PROBE_REACHED"),
            "the task action must be reached (input validation passed), was:\n$output",
        )
        assertTrue(
            !output.contains("Input file does not exist"),
            "the tolerant collection must not fail input validation, was:\n$output",
        )
    }

    @Test
    fun `enrichJsonLdd stays an optional producer - generateCompositeContext has no forced dependency`() {
        settingsFile()
        buildFile()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateCompositeContext", "--dry-run")
            .build()

        val output = result.output
        assertTrue(
            output.contains(":generateCompositeContext"),
            "generateCompositeContext must be scheduled, was:\n$output",
        )
        assertTrue(
            !output.contains(":enrichJsonLdd"),
            "generateCompositeContext must not force enrichJsonLdd (Économie d'Encre), was:\n$output",
        )
    }
}
