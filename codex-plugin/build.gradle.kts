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
// mais Gradle impose toujours 13.0 → les transitives (flexmark 24.0.1)
// sont bloquées. force() est la seule parade.
buildscript {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains:annotations:26.0.2-1")
            force("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.21")
        }
    }
}

group = "education.cccp"
version = libs.versions.codex.plugin.get()

dependencies {
    // Import BOM
    implementation(platform("education.cccp:workspace-bom:0.0.23"))

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

    // koog Agentic Orchestrator — supprimé S-076 (dépendance morte : zéro usage dans src)
    // implementation(libs.koog.agents)

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
    testImplementation(libs.cucumber.junit.platform.engine)
    // Dedicated Cucumber runners (pattern S-082 — @Suite + @SelectClasspathResource)
    testImplementation(libs.junit.platform.suite)

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

// ── CDX-5-3 — Dedicated Cucumber runner for licence routing (pattern S-082) ─────
// Scoped to CodexLicenceCucumberRunner so only codex_export_routed.feature runs,
// not the full src/test/resources/features/*.feature suite.
// Overrides cucumber.features from junit-platform.properties (which points to
// the full features dir for the default cucumberTest task).
val cucumberTestLicence by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the codex_export_routed.feature Cucumber suite (CDX-5-3 licence routing)"
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = configurations.getByName("testRuntimeClasspath") +
        sourceSets.getByName("test").output +
        sourceSets.getByName("main").output
    useJUnitPlatform {
        excludeEngines("junit-jupiter")
    }
    filter {
        includeTestsMatching("codex.bdd.CodexLicenceCucumberRunner")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.features", "src/test/resources/features/codex_export_routed.feature")
    systemProperty("cucumber.filter.tags", "@licence and @routing and not @wip and not @integration")
    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
}

// ── CDX-3-3 — Dedicated Cucumber runner for ontology derivation (pattern S-082) ─
// Scoped to CodexOntologyCucumberRunner so only codex_derive_ontology.feature runs,
// not the full src/test/resources/features/*.feature suite.
// Overrides cucumber.features from junit-platform.properties (which points to
// the full features dir for the default cucumberTest task).
val cucumberTestOntology by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the codex_derive_ontology.feature Cucumber suite (CDX-3-3 ontology derivation)"
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = configurations.getByName("testRuntimeClasspath") +
        sourceSets.getByName("test").output +
        sourceSets.getByName("main").output
    useJUnitPlatform {
        excludeEngines("junit-jupiter")
    }
    filter {
        includeTestsMatching("codex.bdd.CodexOntologyCucumberRunner")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.features", "src/test/resources/features/codex_derive_ontology.feature")
    systemProperty("cucumber.filter.tags", "@ontology and @derivation and not @wip and not @integration")
    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
}

// ── CDX-4-4 — Dedicated Cucumber runner for JSON LDD enrichment (pattern S-082) ─
// Scoped to CodexEnrichmentCucumberRunner so only codex_enrich_json_ldd.feature runs,
// not the full src/test/resources/features/*.feature suite.
// Overrides cucumber.features from junit-platform.properties (which points to
// the full features dir for the default cucumberTest task).
val cucumberTestEnrichment by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the codex_enrich_json_ldd.feature Cucumber suite (CDX-4-4 enrichment)"
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = configurations.getByName("testRuntimeClasspath") +
        sourceSets.getByName("test").output +
        sourceSets.getByName("main").output
    useJUnitPlatform {
        excludeEngines("junit-jupiter")
    }
    filter {
        includeTestsMatching("codex.bdd.CodexEnrichmentCucumberRunner")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.features", "src/test/resources/features/codex_enrich_json_ldd.feature")
    systemProperty("cucumber.filter.tags", "@enrichment and not @wip and not @integration")
    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
}

// ── CDX-6-3 — Dedicated Cucumber runner for pipeline licence routing (pattern S-082) ─
// Scoped to CodexLicencePipelineCucumberRunner so only codex_licence_routing.feature runs,
// not the full src/test/resources/features/*.feature suite.
// Overrides cucumber.features from junit-platform.properties (which points to
// the full features dir for the default cucumberTest task).
val cucumberTestPipelineRouting by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the codex_licence_routing.feature Cucumber suite (CDX-6-3 pipeline routing)"
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = configurations.getByName("testRuntimeClasspath") +
        sourceSets.getByName("test").output +
        sourceSets.getByName("main").output
    useJUnitPlatform {
        excludeEngines("junit-jupiter")
    }
    filter {
        includeTestsMatching("codex.bdd.CodexLicencePipelineCucumberRunner")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.features", "src/test/resources/features/codex_licence_routing.feature")
    systemProperty("cucumber.filter.tags", "@licence and @routing and not @wip and not @integration")
    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
}

// CDX-UNIFY-2 : dedicated Cucumber suite for codex_pipeline_unified_chunking.feature.
// Scoped to CodexPipelineUnifyCucumberRunner so only the unified-chunking feature runs,
// not the full src/test/resources/features/*.feature suite.
// Pattern S-082 inline — mirrors cucumberTestPipelineRouting above.
val cucumberTestPipelineUnify by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the codex_pipeline_unified_chunking.feature Cucumber suite (CDX-UNIFY-2)"
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = configurations.getByName("testRuntimeClasspath") +
        sourceSets.getByName("test").output +
        sourceSets.getByName("main").output
    useJUnitPlatform {
        excludeEngines("junit-jupiter")
    }
    filter {
        includeTestsMatching("codex.bdd.CodexPipelineUnifyCucumberRunner")
    }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    systemProperty("cucumber.features", "src/test/resources/features/codex_pipeline_unified_chunking.feature")
    systemProperty("cucumber.filter.tags", "@pipeline and @unify and not @wip and not @integration")
    shouldRunAfter(tasks.named("test"))
    outputs.upToDateWhen { false }
}