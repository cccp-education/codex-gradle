// ── buildscript resolutionStrategy ────────────────────────────────────────────────
// Gradle 9.5.1 pinne org.jetbrains:annotations:13.0 (Kotlin embedded) en strictly.
// Mais koog-agents (26.0.2-1 via utils-jvm), flexmark (24.0.1), coroutines (23.0.0)
// et d'autres transitives demandent des versions > 13.0 en classpath.
// Les annotations JetBrains sont @Retention(CLASS) : compatibles binairement.
// On force la version la plus haute pour satisfaire toutes les contraintes.
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains:annotations:26.0.2-1")
        }
    }
}

plugins {
    `java-library`
    signing
    `maven-publish`
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.codebase)
    id("codex.gradle-plugin-conventions")
}

group = "education.cccp"
version = libs.versions.doc.pipeline.get()

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
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
    implementation(libs.koog.agents) {
        // Exclusion nécessaire : koog annotions 26.0.2-1 vs Kotlin embedded 13.0
        // Gradle 9.5.1 strict conflict — même pattern que codebase-plugin
        exclude(group = "org.jetbrains", module = "annotations")
    }

    // N0 codebase contracts — source unique de vérité (ContextChannel, ChannelBudget, CompositeContext, CompositeContextConfig)
    implementation("education.cccp:codebase-contracts:0.1.0")

    // RAG/Embedding — ONNX pgvector (R2DBC)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.minilm)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    implementation(libs.r2dbc.spi)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactive)

    testImplementation(platform(libs.testcontainers.bom))
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
    website = "https://github.com/cheroliv/codex"
    vcsUrl  = "https://github.com/cheroliv/codex"
    plugins {
        create("codexDocPipeline") {
            id                  = "codex.doc-pipeline"
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

publishing {
    publications {
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                pom {
                    name.set(gradlePlugin.plugins.getByName("codexDocPipeline").displayName)
                    description.set(gradlePlugin.plugins.getByName("codexDocPipeline").description)
                    url.set(gradlePlugin.website.get())
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cccp-education")
                            name.set("CCCP Education")
                            email.set("cccp.education@gmail.com")
                        }
                    }
                    scm {
                        connection.set(gradlePlugin.vcsUrl.get())
                        developerConnection.set(gradlePlugin.vcsUrl.get())
                        url.set(gradlePlugin.vcsUrl.get())
                    }
                    // RELOCATION : prépare la migration du groupId éducation.cccp →
                    // <futur-domaine>. Activer avec -Prem relocationGroup="io.github.cccp-education"
                    // Effet : injecte <distributionManagement><relocation><groupId>...</groupId></relocation>
                    // dans le POM publié. Les consommateurs existants seront redirigés automatiquement
                    // vers le nouveau groupId lors de la prochaine màj de dépendance.
                    project.findProperty("relocationGroup")?.let { targetGroup ->
                        withXml {
                            val pom = asElement()
                            val doc = pom.ownerDocument
                            val distMgmt = doc.createElement("distributionManagement")
                            val relocation = doc.createElement("relocation")
                            relocation.appendChild(doc.createElement("groupId")).also { it.textContent = targetGroup.toString() }
                            relocation.appendChild(doc.createElement("artifactId")).also { it.textContent = project.name }
                            distMgmt.appendChild(relocation)
                            pom.appendChild(distMgmt)
                        }
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = (if (version.toString().endsWith("-SNAPSHOT"))
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            else
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"))
            credentials {
                username = project.findProperty("ossrhUsername") as? String
                password = project.findProperty("ossrhPassword") as? String
            }
        }
        mavenCentral()
    }
}

signing {
    if (System.getenv("CI") != "true" && !version.toString().endsWith("-SNAPSHOT")) {
        sign(publishing.publications)
    }
    useGpgCmd()
}

java {
    withJavadocJar()
    withSourcesJar()
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
