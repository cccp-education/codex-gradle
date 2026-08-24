package codex

import codex.tasks.AsciiDocToJsonLddTask
import codex.tasks.ChunkDocumentTask
import codex.tasks.CodexIngestTask
import codex.tasks.CodexPipelineTask
import codex.tasks.CodexCompositeContextTask
import codex.tasks.CodexRetrieveTask
import codex.tasks.CollectOcrTask
import codex.tasks.ConvertToMarkdownTask
import codex.tasks.DeployKnowledgeBaseRoutedTask
import codex.tasks.DeriveOntologyTask
import codex.tasks.EnrichJsonLddTask
import codex.tasks.ExportKnowledgeBaseTask
import codex.tasks.ExtractBookStructureTask
import codex.tasks.ExtractEpubStructureTask
import codex.tasks.ExtractTextTask
import codex.tasks.ImportBookSqlTask
import codex.tasks.PersistLearnerProfileTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin for unstructured document acquisition and transformation.
 *
 * Registers 14 tasks organized into 4 unified taxonomy groups:
 *
 * **COLLECT group**:
 * - `collectText` — raw text extraction from PDF
 * - `collectBookStructure` — hierarchical structure extraction from PDF
 * - `collectEpubStructure` — EPUB structure extraction
 * - `collectBookSql` — DDL/INSERT PostgreSQL generation
 * - `collectIngest` — ONNX vectorization + pgvector storage
 * - `collectRetrieve` — cosine similarity semantic search
 * - `collectOcr` — LLM OCR pipeline on image directory → AsciiDoc (consumed by DOC-11)
 *
 * **GENERATE group**:
 * - `generateCompositeContext` — semantic search via CodexVectorStore → composite-context.json
 *
 * **TRANSFORM group**:
 * - `transformToJsonLdd` — AsciiDoc → JSON LDD
 * - `transformToMarkdown` — AsciiDoc → Markdown
 * - `transformChunk` — semantic section chunking
 * - `transformCorpusToPdf` — composite pipeline auto-detecting PDF/EPUB
 * - `deriveOntology` — SQL LMD → ontology mapping (bounded contexts, aggregates, value objects)
 * - `enrichJsonLdd` — JSON LDD + RAG chunks + Graphify → enriched LDD nodes
 *
 * **DEPLOY group**:
 * - `deployKnowledgeBase` — multi-format export (JSON-L, Markdown, AsciiDoc)
 *
 * **CODEX-MEMORY group**:
 * - `persistLearnerProfile` — persist a learner profile JSON to pgvector via SessionMemoryContract RAG bridge
 *
 * Configures a [CodexExtension] for pgvector connection parameters.
 * Automatically detects the license zone ([LicenseZoneDetector]) at load time.
 */
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
        extension.ocrLanguage.convention("fr")
        extension.licenceRouting.convention(false)

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
            "deployKnowledgeBaseRouted",
            DeployKnowledgeBaseRoutedTask::class.java
        ) {
            it.group = "deploy"
            it.description = "Route la base de connaissance vers OSS/ ou office/ selon la licence detectee dans le PDF source"
            it.fallbackZone.convention(zone)
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
            // CDX-6-2 : wiring licenceRouting from extension (default false, backward compat).
            it.licenceRouting.convention(extension.licenceRouting)
            it.fallbackZone.convention(extension.zone)
        }

        project.tasks.register(
            "generateCompositeContext",
            CodexCompositeContextTask::class.java
        ) {
            it.group = "generate"
            it.description = "Semantic search via CodexVectorStore → composite-context.json (runner-compatible)"
            it.query.set(project.providers.gradleProperty("query").orElse("architecture du workspace"))
            it.topK.set(project.providers.gradleProperty("topK").orElse("10"))
            // CDX-CR3-2 : wiring pg depuis l'extension (avant fix, hardcodé localhost:5432)
            it.pgHost.convention(extension.pgvectorHost)
            it.pgPort.convention(extension.pgvectorPort)
            it.pgDatabase.convention(extension.pgvectorDatabase)
            it.pgUser.convention(extension.pgvectorUser)
            it.pgPassword.convention(extension.pgvectorPassword)
            it.outputFile.set(project.layout.buildDirectory.file("codex/composite-context.json"))
            // CDX-4-3 : câblage canal Graphify — enrichedJsonFile consomme
            // la sortie de `enrichJsonLdd` (List<EnrichedLddNode> JSON).
            // Backward compat : la propriété reste @Optional, non configurée
            // → graphifySection = "" (canal muet, comportement précédent).
            it.enrichedJsonFile.set(project.layout.buildDirectory.file("codex/enriched-ldd.json"))
        }

        project.tasks.register(
            "collectOcr",
            CollectOcrTask::class.java
        ) {
            it.group = "collect"
            it.description = "OCR pipeline (AI engine → Tesseract fallback) sur un dossier d'images → AsciiDoc pages (consumed by document-gradle DOC-11)"
            it.language.convention(extension.ocrLanguage)
            // US-CDX-13-3 : outputDir primary output (one .adoc file per page, N2↔N2 bridge)
            it.outputDir.convention(project.layout.buildDirectory.dir("codex/ocr-pages"))
        }

        project.tasks.register(
            "deriveOntology",
            DeriveOntologyTask::class.java
        ) {
            it.group = "transform"
            it.description = "SQL LMD + LDD → ontology mapping JSON (bounded contexts, aggregates, value objects) — computed from DDL"
            it.outputFile.set(project.layout.buildDirectory.file("codex/ontology-mapping.json"))
        }

        project.tasks.register(
            "enrichJsonLdd",
            EnrichJsonLddTask::class.java
        ) {
            it.group = "transform"
            it.description = "JSON LDD + RAG chunks + Graphify graph.json → enriched LDD nodes JSON (cross-source annotation)"
            it.outputFile.set(project.layout.buildDirectory.file("codex/enriched-ldd.json"))
        }

        project.tasks.register(
            "persistLearnerProfile",
            PersistLearnerProfileTask::class.java
        ) {
            it.group = "codex-memory"
            it.description = "Persiste un profil stagiaire JSON dans pgvector via le pont RAG SessionMemoryContract (memoire de session)"
            it.pgHost.convention(extension.pgvectorHost)
            it.pgPort.convention(extension.pgvectorPort)
            it.pgDatabase.convention(extension.pgvectorDatabase)
            it.pgUser.convention(extension.pgvectorUser)
            it.pgPassword.convention(extension.pgvectorPassword)
        }
    }
}
