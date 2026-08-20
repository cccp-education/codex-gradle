package codex.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-CR3-2 : `CodexCompositeContextTask` hardcode `localhost:5432`.
 *
 * Avant fix : `CodexCompositeContextTask.kt:39` instançait `CodexVectorStore()`
 * sans argument, utilisant les defaults `localhost:5432/codex/codex/codex`.
 * La task n'exposait aucune propriété pg — inutilisable hors localhost, et
 * le plugin ne wire pas l'extension `codex { pgvectorHost = ... }` dans cette
 * task contrairement à `collectIngest` et `collectRetrieve`.
 *
 * Après fix : la task expose 5 propriétés `pgHost/pgPort/pgDatabase/pgUser/pgPassword`
 * (annotées `@Input`) et les passe à `CodexVectorStore(host, port, db, user, pass)`.
 * Le plugin wire ces propriétés depuis `CodexExtension` (convention).
 */
class CodexCompositeContextTaskTest {

    @Test
    fun `task is registered and has correct type`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        assertNotNull(task)
        assertTrue(task is CodexCompositeContextTask)
    }

    @Test
    fun `task exposes pgHost property`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        task.pgHost.set("db.internal")
        assertEquals("db.internal", task.pgHost.get())
    }

    @Test
    fun `task exposes pgPort property`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        task.pgPort.set("6543")
        assertEquals("6543", task.pgPort.get())
    }

    @Test
    fun `task exposes pgDatabase property`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        task.pgDatabase.set("codex_prod")
        assertEquals("codex_prod", task.pgDatabase.get())
    }

    @Test
    fun `task exposes pgUser property`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        task.pgUser.set("codex_user")
        assertEquals("codex_user", task.pgUser.get())
    }

    @Test
    fun `task exposes pgPassword property`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        task.pgPassword.set("s3cr3t")
        assertEquals("s3cr3t", task.pgPassword.get())
    }

    @Test
    fun `task defaults match CodexExtension conventions`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        // Avant fix, il n'y avait aucun default — la task instanciait CodexVectorStore()
        // avec localhost:5432 codex/codex/codex en dur dans le code.
        // Après fix, des conventions sont posées pour garantir un comportement
        // par défaut identique à l'ancien (backward compat).
        assertEquals("localhost", task.pgHost.get())
        assertEquals("5432", task.pgPort.get())
        assertEquals("codex", task.pgDatabase.get())
        assertEquals("codex", task.pgUser.get())
        assertEquals("codex", task.pgPassword.get())
    }

    // CDX-4-3 : câblage canal Graphify dans CodexCompositeContextTask.

    @Test
    fun `task exposes optional enrichedJsonFile property`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        // Avant CDX-4-3, la task n'exposait pas de propriété enrichedJsonFile —
        // graphifySection restait hardcodé à "" (canal Graphify muet).
        // Après CDX-4-3, la propriété optionnelle permet de peupler le canal
        // depuis le JSON enrichi produit par enrichJsonLdd.
        assertNotNull(task.enrichedJsonFile)
    }

    @Test
    fun `task enrichedJsonFile is optional - unset does not crash configuration`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("generateCompositeContext", CodexCompositeContextTask::class.java).get()

        // Backward compat : la propriété est @Optional, aucune convention
        // n'est posée. Une task non configurée doit rester instançable.
        assertTrue(!task.enrichedJsonFile.isPresent, "enrichedJsonFile should be optional (no default)")
    }
}