package codex

import codex.tasks.CodexCompositeContextTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — CDX-DOUBT-BRIDGE-2 : DSL `codex { excludeDoubtfulDocs = true }` + wiring.
 *
 * Before this US, `CodexExtension` exposed no doubt policy and
 * `generateCompositeContext` always kept (and failed to annotate) doubtful
 * chunks. After, the extension carries `excludeDoubtfulDocs` (default false)
 * and the plugin wires it to [CodexCompositeContextTask].
 *
 * Baby-step TDD strict : RED -> GREEN -> REFACTOR.
 */
class CodexExtensionDoubtTest {

    @Test
    fun `extension exposes excludeDoubtfulDocs property`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("codex", CodexExtension::class.java)

        assertNotNull(extension.excludeDoubtfulDocs, "CodexExtension should expose excludeDoubtfulDocs")
    }

    @Test
    fun `plugin defaults excludeDoubtfulDocs to false on extension`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val extension = project.extensions.findByName("codex") as CodexExtension
        assertFalse(extension.excludeDoubtfulDocs.get(), "default false — annotate and keep (backward compat)")
    }

    @Test
    fun `plugin wires extension excludeDoubtfulDocs to generateCompositeContext task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val extension = project.extensions.findByName("codex") as CodexExtension
        extension.excludeDoubtfulDocs.set(true)

        val task = project.tasks.findByName("generateCompositeContext") as CodexCompositeContextTask
        assertTrue(task.excludeDoubtfulDocs.get(), "generateCompositeContext.excludeDoubtfulDocs should reflect extension")
    }
}
