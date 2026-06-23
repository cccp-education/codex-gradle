<!-- master source — other languages are translations of this file -->
# codex-gradle — Plugin Internals

> Developer & contributor guide for the `codex-plugin` Gradle plugin.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **Version**: `0.0.1` · **Group**: `education.cccp` · **Plugin ID**: `education.cccp.codex`
- **Toolchain**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **Build**: `./gradlew build -x test` · **Tests**: `./gradlew :codex-plugin:check` · **Coverage**: Kover 0.9.8 (on-fly; see known limitation below)

🌐 Languages: **EN** | [Français](README.fr.md)

---

## Module layout

```
codex-gradle/
├── build.gradle.kts                 # root placeholder (readme plugin disabled)
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # root catalog (plugin versions)
└── codex-plugin/
    ├── build.gradle.kts              # plugin module (publishing, signing, kover)
    ├── gradle/libs.versions.toml     # module catalog (all dependency versions)
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # Plugin entry point — registers 12 tasks
        ├── CodexExtension.kt         # `codex { ... }` extension (zone + pgvector)
        ├── Metadata.kt               # Workspace pivot metadata format
        ├── LicenseZoneDetector.kt    # Auto-detect OSS/CSS/UNKNOWN zone at load
        ├── ocr/                      # OCR contracts (OcrConfig, OcrRequest, OcrResult, engines)
        ├── store/                    # CodexVectorStore — R2DBC pgvector client
        └── tasks/                    # 12 task implementations (+ DocNode, FontStyle)
```

## Registered tasks

12 tasks across 4 taxonomy groups (see `CodexPlugin.kt`):

| Group | Tasks |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## N0 contracts (from workspace-bom MEMPHIS)

Codex imports `education.cccp:workspace-bom:0.0.1` as a platform BOM and directly
consumes:

| Contract | Artifact | Provides |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

Other N0 contracts available via the BOM (not all directly imported by codex):
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`.

## Key dependencies

- **koog-agents** 1.0.0 — agentic orchestrator (DSL Kotlin StateGraph)
- **langchain4j** 1.14.1 — LLM providers, RAG, embeddings
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — ONNX sentence embeddings
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — reactive pgvector
- **kotlinx-coroutines** 1.9.0 (core + reactive) — R2DBC bridge
- **Apache PDFBox** 3.0.4 — PDF text/structure extraction
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — format detection
- **Flexmark** 0.64.8 (`flexmark-all`) — Markdown processing
- **AsciidoctorJ** 3.0.0 — AsciiDoc parsing/rendering
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — JSON/YAML serialization
- **kotlinx-serialization-json** 1.7.3

`buildscript` forces `org.jetbrains:annotations:26.0.2-1` to override Gradle's
strict pin (`{strictly 13.0}`) which would otherwise break koog/flexmark
transitives.

## Test matrix

Sources live under `codex-plugin/src/test/kotlin/`:

| Scope | Location | Count |
|------|----------|-------|
| Unit (JUnit5) | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 spec classes |
| BDD (Cucumber) | `codex/bdd/PipelineSteps.kt` + 5 `.feature` files | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| Integration | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

All tests use `useJUnitPlatform()`; test logging emits `FAILED` + `SKIPPED` events.
Testcontainers provides `pgvector/pgvector:pg17`-style containers via
`testcontainers-postgresql` 1.21.4 and `docker-java` 3.7.0 (httpclient5 transport).

There is **no** `testFast`/`testAll`/`testEpics` split task and **no**
`koverVerify` gate defined here — the single `:codex-plugin:check` task runs
the full JUnit5 + Cucumber suite. Kover emits XML + HTML reports but is not
wired into `check` (`onCheck = false`).

### Known coverage limitation

Kover 0.9.8 on-fly agent cannot instrument classes loaded by Gradle TestKit
(`ProjectBuilder` runs the plugin in a separate classloader). Tests for
`CodexPlugin`, `CodexExtension`, etc. pass but their coverage is reported as 0 %.
Upgrade path: Kover 1.x offline instrumentation (not yet released).

## JVM tuning

- **Java**: 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin**: `jvmTarget = JVM_24`, `-Xjsr305=strict`
- Heap: tune via `GRADLE_OPTS="-Xmx2g"` for ingestion-heavy runs

## Build commands

```bash
./gradlew build -x test                  # compile only
./gradlew :codex-plugin:check           # full tests (JUnit5 + Cucumber)
./gradlew :codex-plugin:test            # JUnit5 unit tests only
./gradlew koverHtmlReport               # coverage HTML report (manual)
./gradlew publishToMavenLocal           # local publish
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## CI pipeline

`.github/workflows/test.yml` defines a single **Build & Test** job
(`ubuntu-latest`, timeout 15 min):

1. Checkout (`actions/checkout@v4`)
2. Set up JDK 24 Temurin (`actions/setup-java@v4`)
3. Set up Gradle (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

There is **no** publish-on-tag job in this workflow; Maven Central publication
is driven by the `publishAggregationToCentralPortal` task invoked manually /
in a separate publication flow (see NMCP section in `AGENTS.adoc`).

## Publication (NMCP)

Configured in `codex-plugin/build.gradle.kts`:

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- `mavenCentral()` repository (not legacy Sonatype staging)
- `signing { useGpgCmd() }` — skipped when `CI == "true"` or version ends `-SNAPSHOT`
- POM declared on **all** `withType<MavenPublication>` (not just `pluginMaven`):
  Apache 2.0 license, developer `cccp-education`, SCM → `github.com/cccp-education/codex`
- Sources + javadoc JARs (`withSourcesJar()`, `withJavadocJar()`)
- Optional `<relocation>` block activated by `-Prem relocationGroup=<namespace>`
  (prepared for future `groupId` migration; currently inert)

Note: `settings.gradle.kts` here does **not** include the `nmcp` settings plugin;
this codex-gradle repository publishes `codex-plugin` directly via `maven-publish`
+ the `publishAggregationToCentralPortal` task flow documented in `AGENTS.adoc`.

## Ollama instances (global constraint)

Ports `11434–11436` are **forbidden**. Rotate over `11437–11465` (29 ports).
Authorized models: `gpt-oss:120b-cloud`, `gemma4:31b-cloud`.

## Contributing

1. Build compiles: `./gradlew build -x test`
2. Tests green: `./gradlew :codex-plugin:check`
3. No CVE regression: keep `org.jetbrains:annotations` force pinned to `26.0.2-1`
4. Follow DDD conventions (value objects, ports/adapters, no leaks)
5. Respect the boundary: codex = **READ + RAG**; do not add WRITE/PUBLISH logic
   (that belongs to `document-gradle`)

## Architecture docs

- [AGENT.adoc](../AGENT.adoc) — Absolute rules (commits, secrets, classification)
- [BACKLOG.adoc](../BACKLOG.adoc) — EPIC PUB publication backlog
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — Codex↔Document boundary
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — Unified task taxonomy
- `gradle/libs.versions.toml` (module catalog) — canonical dependency versions

## License

Apache License 2.0 — see [LICENSE](../LICENSE).

---

_Part of the CCCP Education ecosystem — `groupId: education.cccp`._