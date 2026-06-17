package codex

import codex.tasks.AsciiDocToJsonLddTask
import codex.tasks.ChunkDocumentTask
import codex.tasks.CodexIngestTask
import codex.tasks.CodexPipelineTask
import codex.tasks.CodexRetrieveTask
import codex.tasks.ConvertToMarkdownTask
import codex.tasks.ExportKnowledgeBaseTask
import codex.tasks.ExtractBookStructureTask
import codex.tasks.ExtractEpubStructureTask
import codex.tasks.ExtractTextTask
import codex.tasks.ImportBookSqlTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodexPluginTest {

    @Test
    fun `plugin applies successfully and registers extension`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val extension = project.extensions.findByName("codex")
        assertNotNull(extension)
        assertTrue(extension is CodexExtension)
    }

    @Test
    fun `plugin registers all 10 tasks with correct types`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        // collect tasks (5)
        val collectText = project.tasks.findByName("collectText")
        assertNotNull(collectText, "collectText should be registered")
        assertTrue(collectText is ExtractTextTask)

        val collectBookStructure = project.tasks.findByName("collectBookStructure")
        assertNotNull(collectBookStructure, "collectBookStructure should be registered")
        assertTrue(collectBookStructure is ExtractBookStructureTask)

        val collectEpubStructure = project.tasks.findByName("collectEpubStructure")
        assertNotNull(collectEpubStructure, "collectEpubStructure should be registered")
        assertTrue(collectEpubStructure is ExtractEpubStructureTask)

        val collectBookSql = project.tasks.findByName("collectBookSql")
        assertNotNull(collectBookSql, "collectBookSql should be registered")
        assertTrue(collectBookSql is ImportBookSqlTask)

        val collectIngest = project.tasks.findByName("collectIngest")
        assertNotNull(collectIngest, "collectIngest should be registered")
        assertTrue(collectIngest is CodexIngestTask)

        val collectRetrieve = project.tasks.findByName("collectRetrieve")
        assertNotNull(collectRetrieve, "collectRetrieve should be registered")
        assertTrue(collectRetrieve is CodexRetrieveTask)

        // transform tasks (4)
        val transformToJsonLdd = project.tasks.findByName("transformToJsonLdd")
        assertNotNull(transformToJsonLdd, "transformToJsonLdd should be registered")
        assertTrue(transformToJsonLdd is AsciiDocToJsonLddTask)

        val transformToMarkdown = project.tasks.findByName("transformToMarkdown")
        assertNotNull(transformToMarkdown, "transformToMarkdown should be registered")
        assertTrue(transformToMarkdown is ConvertToMarkdownTask)

        val transformChunk = project.tasks.findByName("transformChunk")
        assertNotNull(transformChunk, "transformChunk should be registered")
        assertTrue(transformChunk is ChunkDocumentTask)

        val transformCorpusToPdf = project.tasks.findByName("transformCorpusToPdf")
        assertNotNull(transformCorpusToPdf, "transformCorpusToPdf should be registered")
        assertTrue(transformCorpusToPdf is CodexPipelineTask)

        // deploy tasks (1)
        val deployKnowledgeBase = project.tasks.findByName("deployKnowledgeBase")
        assertNotNull(deployKnowledgeBase, "deployKnowledgeBase should be registered")
        assertTrue(deployKnowledgeBase is ExportKnowledgeBaseTask)
    }

    @Test
    fun `collectText task has correct group and description`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("collectText")
        assertNotNull(task)
        assertEquals("collect", task!!.group)
        assertTrue(task.description!!.contains("Extrait le texte brut"))
    }

    @Test
    fun `transformToJsonLdd task has correct group and description`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("transformToJsonLdd")
        assertNotNull(task)
        assertEquals("transform", task!!.group)
        assertTrue(task.description!!.contains("AsciidoctorJ"))
    }

    @Test
    fun `deployKnowledgeBase task has correct group and description`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val task = project.tasks.findByName("deployKnowledgeBase")
        assertNotNull(task)
        assertEquals("deploy", task!!.group)
        assertTrue(task.description!!.contains("multi-format"))
    }

    @Test
    fun `plugin sets pgvector conventions on ingest task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val ingestTask = project.tasks.findByName("collectIngest") as CodexIngestTask
        assertEquals("localhost", ingestTask.pgHost.get())
        assertEquals("5432", ingestTask.pgPort.get())
        assertEquals("codex", ingestTask.pgDatabase.get())
        assertEquals("codex", ingestTask.pgUser.get())
        assertEquals("codex", ingestTask.pgPassword.get())
        assertEquals("32", ingestTask.batchSize.get())
    }

    @Test
    fun `plugin sets pgvector conventions on retrieve task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("education.cccp.codex")

        val retrieveTask = project.tasks.findByName("collectRetrieve") as CodexRetrieveTask
        assertEquals("10", retrieveTask.topK.get())
        assertEquals("localhost", retrieveTask.pgHost.get())
        assertEquals("5432", retrieveTask.pgPort.get())
        assertEquals("codex", retrieveTask.pgDatabase.get())
        assertEquals("codex", retrieveTask.pgUser.get())
        assertEquals("codex", retrieveTask.pgPassword.get())
    }
}
