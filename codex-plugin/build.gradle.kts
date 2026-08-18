plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.kover)
    alias(libs.plugins.codebase)
    id("education.cccp.build.gradle-plugin") version "0.0.2"
    id("education.cccp.build.publishing") version "0.0.2"
}

// ── buildscript resolutionStrategy ────────────────────────────────────────────────
// Gradle 9.x pinne org.jetbrains:annotations:{strictly 13.0} via son Kotlin embed.
// codebase-plugin ne contraint plus annotations depuis la republo 0.0.2 locale,
// mais Gradle impose toujours 13.0 → les transitives (koog 26.0.2-1, flexmark 24.0.1)
// sont bloquées. force() est la seule parade.
buildscript {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains:annotations:26.0.2-1")
        }
    }
}

group = "education.cccp"
version = libs.versions.codex.plugin.get()

dependencies {
    // Import BOM
    implementation(platform("education.cccp:workspace-bom:0.0.21"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)

    // PDF extraction
    implementation(libs.pdfbox)
    implementation(libs.tika.core)
    implementation(libs.tika.parsers.standard)

    // Document conversion
    implementation(libs.flexmark.all)
    implementation(libs.asciidoctorj)

    // Sérialisation
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)

    // koog Agentic Orchestrator
    implementation(libs.koog.agents)

    // N0 codebase contracts — source unique de vérité (ContextChannel, ChannelBudget, CompositeContext, CompositeContextConfig)
    implementation("education.cccp:codebase-contracts:0.0.2")

    // RAG/Embedding — ONNX pgvector (R2DBC)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.minilm)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    implementation(libs.r2dbc.spi)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactive)

    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.docker.java.core)
    testRuntimeOnly(libs.docker.java.transport.httpclient5)
    testRuntimeOnly(libs.logback.classic)
    testRuntimeOnly(libs.slf4j.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())

    // Cucumber BDD
    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.java8)
    testRuntimeOnly(libs.cucumber.junit.platform.engine)

    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    website = "https://cccp.education/"
    vcsUrl  = "https://github.com/cccp-education/codex"
    plugins {
        create("codexDocPipeline") {
            id                  = "education.cccp.codex"
            implementationClass = "codex.CodexPlugin"
            displayName         = "Codex — Pipeline d'acquisition de documents"
            description         = """
                Pipeline Gradle d'acquisition de documents PDF/EPUB pour
                alimenter la base de connaissance RAG + Knowledge Graph.
                Extraction typographique, conversion Markdown/AsciiDoc,
                chunking sémantique, export structuré (JSON-L, Markdown, AsciiDoc).
            """.trimIndent()
            tags = listOf(
                "pdf", "epub", "markdown", "asciidoc",
                "rag", "knowledge-graph", "text-extraction",
                "chunking", "kotlin"
            )
        }
    }
}

publishingConventions {
    publicationType = "PLUGIN"
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set(gradlePlugin.plugins.getByName("codexDocPipeline").displayName)
                description.set(gradlePlugin.plugins.getByName("codexDocPipeline").description)
            }
        }
    }
    repositories {
        mavenCentral()
        maven {
            name = "localRepo"
            url = uri(rootProject.layout.buildDirectory.dir("local-repo"))
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

kover {
    reports {
        total {
            xml { onCheck = false }
            html { onCheck = false }
        }
    }
}

// NOTE: Kover 0.9.8 does not support offline instrumentation (same limitation as 0.9.1).
// Gradle TestKit (ProjectBuilder) loads plugin bytecode in a separate
// classloader that the on-fly agent cannot intercept.
// This means tests for CodexPlugin, CodexExtension etc. pass but
// their coverage is not counted by Kover.
// Upgrade to Kover 1.x+ for offline instrumentation support (when released).