package codex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.text.Charsets.UTF_8

/**
 * MEM-CAT-ROLLOUT-2 (S-029, cross-borough MEMPHIS) — publication hygiene guard.
 *
 * D3: the plugin self version is derived from the version catalog — the ws
 * catalog (`ws.versions.codex.plugin.get()`) is the cross-borough source of truth.
 * D4: the borough pins the catalog once in settings.gradle.kts.
 * D5 hygiene: the local toml self version and the ws catalog version must agree,
 * and the platform pin must match the ws catalog BOM version.
 */
class CodexPluginPublicationTest {
    private val pluginDir = File(System.getProperty("user.dir")).absoluteFile

    /**
     * C-3 (S-232) — the *published* workspace catalog version is *injected by
     * Gradle* (`ws.versions.*`), never read from a neighbour repository's working
     * tree. The old fallback (`../workspace-bom/gradle/libs.versions.toml`) is
     * racy between parallel sessions and absent from an isolated CI checkout —
     * same failure mode graphify-gradle D5-RACE (S-029) and bakery
     * BKY-CI-ISOLATION (S-243) fixed. A missing property is an explicit error,
     * never a silent green.
     */
    private fun publishedCatalogVersion(property: String): String =
        System.getProperty(property)
            ?: error("$property is not set — run through Gradle (build.gradle.kts injects the published ws catalog version)")

    @Test
    fun `plugin version matches root consumer catalog version`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val versionLine =
            buildScript
                .lineSequence()
                .first { it.trimStart().startsWith("version =") }

        // MEM-CAT-ROLLOUT-2 (D3) — self version derived from the published workspace catalog.
        assertThat(versionLine)
            .withFailMessage("build.gradle.kts version must derive from the published workspace catalog (ws.versions.codex.plugin)")
            .contains("ws.versions.codex.plugin.get()")

        // Hygiene (D5): local toml self version must match the published ws catalog version.
        val pluginCatalogVersion = codexVersionFrom(pluginDir.resolve("gradle/libs.versions.toml").readText(UTF_8))
        val wsCatalogVersion = publishedCatalogVersion("codex.publishedCatalog.codexVersion")

        assertThat(pluginCatalogVersion)
            .withFailMessage("plugin catalog codex-plugin version ($pluginCatalogVersion) must match ws catalog codex-plugin version ($wsCatalogVersion)")
            .isEqualTo(wsCatalogVersion)
    }

    @Test
    fun `workspace bom platform pin matches ws catalog bom version`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)
        val wsBomVersion = publishedCatalogVersion("codex.publishedCatalog.bomVersion")

        assertThat(buildScript)
            .withFailMessage("workspace-bom platform pin must use the ws catalog BOM version ($wsBomVersion)")
            .contains("""platform("education.cccp:workspace-bom:$wsBomVersion")""")
    }

    private fun codexVersionFrom(content: String): String =
        content
            .lineSequence()
            .map { it.substringBefore('#').trim() }
            .first { it.startsWith("codex-plugin =") }
            .substringAfter("\"")
            .substringBefore("\"")

    @Test
    fun `plugin group and id are stable for publication`() {
        val buildScript = pluginDir.resolve("build.gradle.kts").readText(UTF_8)

        // The plugin id is declared inline in the gradlePlugin block (gradlePlugin.plugins codexDocPipeline).
        val idLine =
            buildScript
                .lineSequence()
                .first { it.trimStart().startsWith("id ") && it.contains("education.cccp.codex") }

        assertThat(buildScript).contains("group = \"education.cccp\"")
        assertThat(idLine.substringAfter("\"").substringBefore("\"")).isEqualTo("education.cccp.codex")
    }
}