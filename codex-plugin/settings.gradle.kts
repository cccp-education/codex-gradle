@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
// CNV-16 — JitPack mirror for the education.cccp.build.* conventions
// (bypasses Sonatype / Gradle Plugin Portal). The JitPack tag is pinned while
// the canonical plugin declarations may stay on the released version.
pluginManagement.repositories.maven("https://jitpack.io")
pluginManagement.resolutionStrategy.eachPlugin {
    if (requested.id.id.startsWith("education.cccp.build.")) {
        useModule("com.github.cccp-education.conventions-gradle:conventions-plugin:0.0.16")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
    id("com.gradleup.nmcp.settings").version("1.5.0")
}

val globalProps =
    java.util.Properties().also {
        val globalFile = file(System.getProperty("user.home") + "/.gradle/gradle.properties")
        if (globalFile.exists()) it.load(globalFile.inputStream())
    }

nmcpSettings {
    centralPortal {
        username = globalProps.getProperty("ossrhUsername") ?: ""
        password = globalProps.getProperty("ossrhPassword") ?: ""
        publishingType = "AUTOMATIC"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        // CNV-16 — JitPack channel (Central suspended until 2026-12-01).
        maven("https://jitpack.io")
    }
}

// ── MEM-CAT-ROLLOUT-2 — Catalog workspace published (MEMPHIS): single pin per borough (D4) ──
// CNV-16 — version source = the catalogue **deployed on JitPack** (tag `v0.0.133`),
// so the build version equals the published artefact version.
// `com.github.cccp-education.workspace-bom:workspace-catalog`.
dependencyResolutionManagement {
    versionCatalogs {
        create("ws") {
            from("com.github.cccp-education.workspace-bom:workspace-catalog:v0.0.133")
        }
    }
}

rootProject.name = "codex-plugin"
