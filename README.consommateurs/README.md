<!-- master source — other languages are translations of this file -->
# codex-gradle — Consumer Guide

> Document-acquisition Gradle plugin: PDF/EPUB → extraction → chunking → pgvector RAG.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **Version**: `0.0.1` · **Group**: `education.cccp` · **Plugin ID**: `education.cccp.codex`
- **Build**: `./gradlew build` · **Tests**: `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **Upstream dependency**: [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — consumed as the single source of truth for `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig`.

🌐 Languages: **EN** | [Français](README.fr.md)

---

## What it does

`codex-gradle` acquires unstructured documents (PDF, EPUB), extracts structured
text + book hierarchy, converts to Markdown/AsciiDoc, performs semantic chunking,
vectorizes with ONNX (`all-MiniLM-L6-v2`), stores embeddings in **PostgreSQL +
pgvector** over **R2DBC**, and exposes cosine-similarity retrieval to feed the
CCCP Education **RAG** layer and **Knowledge Graph**.

It sits upstream of [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle)
in the CCCP Education ecosystem:

```
document corpus → codex-gradle (READ + RAG) → [codebase-gradle] → koog agents → output
```

Boundary (documented in `.agents/ARCHITECTURE_BOUNDARY.adoc`): **codex = READ +
RAG**; the sibling `document-gradle` plugin handles **WRITE + PUBLISH**. The two
N2 plugins are parallel, not stacked.

## Quick Start

### 1. Apply the plugin

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. Configure pgvector connection (optional — defaults shown)

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. Acquire & ingest a corpus

```bash
./gradlew transformCorpusToPdf          # auto-detect PDF/EPUB → extract → chunk → JSON
./gradlew collectIngest                 # vectorize chunks → pgvector (batch size 32)
./gradlew collectRetrieve --query="..." # cosine similarity top-K
```

## Available tasks

| Task | Group | Description |
|------|-------|-------------|
| `collectText`              | collect  | Raw text extraction from PDF with typographic metadata |
| `collectBookStructure`     | collect  | PDF structure extraction (titles/sections) → hierarchical `.adoc` |
| `collectEpubStructure`      | collect  | EPUB structure extraction (XHTML → `.adoc` + code blocks) |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | ONNX vectorization → pgvector via R2DBC (default batch 32) |
| `collectRetrieve`          | collect  | Cosine similarity semantic search in pgvector (default top-K 10) |
| `transformToJsonLdd`       | transform | Parse `.adoc` via AsciidoctorJ → JSON LDD |
| `transformToMarkdown`      | transform | AsciiDoc structure → Markdown (hierarchy + code blocks preserved) |
| `transformChunk`           | transform | Semantic section chunking (1 chunk per heading) |
| `transformCorpusToPdf`     | transform | Composite pipeline (auto-detect PDF/EPUB): extract → Markdown → chunk → JSON |
| `generateCompositeContext` | generate | Semantic search via `CodexVectorStore` → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | Aggregate chunks → multi-format KB (JSON-L, Markdown, AsciiDoc) |

Tasks follow the unified workspace taxonomy (4 verbs: GENERATE / COLLECT /
TRANSFORM / DEPLOY) — see `TAXONOMIE_WORKSPACE.adoc`.

## Extension DSL

```gradle
codex {
    // License zone (auto-detected via LicenseZoneDetector; OSS/CSS/UNKNOWN)
    zone = codex.LicenseZone.OSS

    // pgvector connection parameters (all default to the values shown)
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

The `generateCompositeContext` task also reads these Gradle properties:

| Property | Default | Purpose |
|----------|---------|---------|
| `query`  | `"architecture du workspace"` | Semantic search query |
| `topK`   | `"10"`                         | Number of nearest neighbours |

Example:

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json (runner-compatible)
```

## Prerequisites

- **Java** 24 (Kotlin 2.3.20 toolchain, `jvmTarget = 24`)
- **Gradle** 9.5.1
- **PostgreSQL** 15+ with `pgvector` extension (for `collectIngest`/`collectRetrieve`)
- **Docker** (for Testcontainers-backed integration tests)

## Build & test

```bash
./gradlew build                            # full build (compiles + tests)
./gradlew build -x test                    # compile only
./gradlew :codex-plugin:check              # tests (JUnit5 + Cucumber BDD)
./gradlew publishToMavenLocal              # local publish
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `org.jetbrains:annotations` version conflict (Gradle pins 13.0) | Already forced to `26.0.2-1` in `buildscript`; rerun with `--refresh-dependencies` |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Postgres Testcontainer stuck | `docker rm -f $(docker ps -aq --filter name=postgres)` then retry |
| Kover reports 0% on plugin classes | Known limitation: Kover 0.9.8 on-fly agent cannot intercept Gradle TestKit `ProjectBuilder` classloader. See note in `build.gradle.kts`. |

## License

Apache License 2.0 — see [LICENSE](../LICENSE).

---

_Part of the CCCP Education ecosystem — `groupId: education.cccp`._