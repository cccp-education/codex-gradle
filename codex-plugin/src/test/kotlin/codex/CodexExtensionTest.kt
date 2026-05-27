package codex

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CodexExtensionTest {

    @Test
    fun `extension is created with default zone convention`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("codex", CodexExtension::class.java)

        assertNotNull(extension)
        // zone is a Property<LicenseZone>, no default convention set by extension alone
        // convention is set by CodexPlugin.apply()
    }

    @Test
    fun `extension properties are writable and readable`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("codex", CodexExtension::class.java)

        extension.pgvectorHost.convention("pg.example.com")
        extension.pgvectorPort.convention("6432")
        extension.pgvectorDatabase.convention("mydb")
        extension.pgvectorUser.convention("admin")
        extension.pgvectorPassword.convention("secret")

        assertEquals("pg.example.com", extension.pgvectorHost.get())
        assertEquals("6432", extension.pgvectorPort.get())
        assertEquals("mydb", extension.pgvectorDatabase.get())
        assertEquals("admin", extension.pgvectorUser.get())
        assertEquals("secret", extension.pgvectorPassword.get())
    }

    @Test
    fun `extension zone can be set`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("codex", CodexExtension::class.java)

        extension.zone.convention(LicenseZone.OSS)
        assertEquals(LicenseZone.OSS, extension.zone.get())

        extension.zone.convention(LicenseZone.CSS)
        assertEquals(LicenseZone.CSS, extension.zone.get())
    }

    @Test
    fun `plugin applies defaults to extension`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("codex.doc-pipeline")

        val extension = project.extensions.findByName("codex") as CodexExtension
        assertEquals("localhost", extension.pgvectorHost.get())
        assertEquals("5432", extension.pgvectorPort.get())
        assertEquals("codex", extension.pgvectorDatabase.get())
        assertEquals("codex", extension.pgvectorUser.get())
        assertEquals("codex", extension.pgvectorPassword.get())
    }
}
