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
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodexPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val zone = LicenseZoneDetector.detect(project.projectDir.absolutePath)
        val license = LicenseZoneDetector.toLicenseName(zone)

        project.logger.lifecycle("[codex] Plugin chargé — pipeline doc activé (zone: $zone, licence: $license)")

        val extension = project.extensions.create("codex", CodexExtension::class.java)
        extension.zone.convention(zone)
        extension.pgvectorHost.convention("localhost")
        extension.pgvectorPort.convention("5432")
        extension.pgvectorDatabase.convention("codex")
        extension.pgvectorUser.convention("codex")
        extension.pgvectorPassword.convention("codex")

        project.tasks.register(
            "collectText",
            ExtractTextTask::class.java
        ) {
            it.group = "collect"
            it.description = "Extrait le texte brut structuré d'un PDF avec métadonnées typographiques"
        }

        project.tasks.register(
            "collectBookStructure",
            ExtractBookStructureTask::class.java
        ) {
            it.group = "collect"
            it.description = "Extrait la structure d'un PDF (titres, sections) et produit un .adoc hiérarchique"
        }

        project.tasks.register(
            "collectEpubStructure",
            ExtractEpubStructureTask::class.java
        ) {
            it.group = "collect"
            it.description = "Extrait la structure d'un EPUB (XHTML → .adoc avec hiérarchie et blocs de code)"
        }

        project.tasks.register(
            "transformToJsonLdd",
            AsciiDocToJsonLddTask::class.java
        ) {
            it.group = "transform"
            it.description = "Parse un .adoc via AsciidoctorJ → JSON LDD structuré"
        }

        project.tasks.register(
            "collectBookSql",
            ImportBookSqlTask::class.java
        ) {
            it.group = "collect"
            it.description = "JSON LDD → DDL + INSERT PostgreSQL"
        }

        project.tasks.register(
            "transformToMarkdown",
            ConvertToMarkdownTask::class.java
        ) {
            it.group = "transform"
            it.description = "Convertit un .adoc structure en Markdown avec hierarchie et blocs de code preserves"
        }

        project.tasks.register(
            "transformChunk",
            ChunkDocumentTask::class.java
        ) {
            it.group = "transform"
            it.description = "Decoupe un document Markdown en chunks semantiques par section (1 chunk par heading)"
            it.licenseName.convention(license)
        }

        project.tasks.register(
            "deployKnowledgeBase",
            ExportKnowledgeBaseTask::class.java
        ) {
            it.group = "deploy"
            it.description = "Agrege les chunks en base de connaissance multi-format (JSON-L, Markdown, AsciiDoc)"
        }

        project.tasks.register(
            "collectIngest",
            CodexIngestTask::class.java
        ) {
            it.group = "collect"
            it.description = "Vectorise les chunks avec ONNX AllMiniLmL6V2 et les stocke dans pgvector via R2DBC"
            it.pgHost.convention(extension.pgvectorHost)
            it.pgPort.convention(extension.pgvectorPort)
            it.pgDatabase.convention(extension.pgvectorDatabase)
            it.pgUser.convention(extension.pgvectorUser)
            it.pgPassword.convention(extension.pgvectorPassword)
            it.batchSize.convention("32")
        }

        project.tasks.register(
            "collectRetrieve",
            CodexRetrieveTask::class.java
        ) {
            it.group = "collect"
            it.description = "Recherche semantique cosine similarity dans pgvector — corpus documentaire requetable"
            it.topK.convention("10")
            it.pgHost.convention(extension.pgvectorHost)
            it.pgPort.convention(extension.pgvectorPort)
            it.pgDatabase.convention(extension.pgvectorDatabase)
            it.pgUser.convention(extension.pgvectorUser)
            it.pgPassword.convention(extension.pgvectorPassword)
        }

        project.tasks.register(
            "transformCorpusToPdf",
            CodexPipelineTask::class.java
        ) {
            it.group = "transform"
            it.description = "Pipeline composite auto-detection PDF/EPUB : extraction → Markdown → chunking → resultat JSON"
            it.licenseName.convention(license)
            it.pgHost.convention(extension.pgvectorHost)
            it.pgPort.convention(extension.pgvectorPort)
            it.pgDatabase.convention(extension.pgvectorDatabase)
            it.pgUser.convention(extension.pgvectorUser)
            it.pgPassword.convention(extension.pgvectorPassword)
            it.batchSize.convention("32")
        }
    }
}
