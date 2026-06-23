<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — Aspectos internos del plugin

> Guía para desarrolladores y contribuyentes del plugin de Gradle `codex-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **Versión**: `0.0.1` · **Grupo**: `education.cccp` · **ID del plugin**: `education.cccp.codex`
- **Toolchain**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **Build**: `./gradlew build -x test` · **Tests**: `./gradlew :codex-plugin:check` · **Cobertura**: Kover 0.9.8 (on-fly; ver limitación conocida más abajo)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | **Español** | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## Disposición de módulos

```
codex-gradle/
├── build.gradle.kts                 # placeholder raíz (plugin readme deshabilitado)
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # catálogo raíz (versiones de plugins)
└── codex-plugin/
    ├── build.gradle.kts              # módulo del plugin (publicación, signing, kover)
    ├── gradle/libs.versions.toml     # catálogo de módulo (todas las versiones de dependencias)
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # punto de entrada del plugin — registra 12 tareas
        ├── CodexExtension.kt         # extensión `codex { ... }` (zone + pgvector)
        ├── Metadata.kt               # formato de metadatos pivot del workspace
        ├── LicenseZoneDetector.kt    # Auto-detectar zona OSS/CSS/UNKNOWN al cargar
        ├── ocr/                      # contratos OCR (OcrConfig, OcrRequest, OcrResult, engines)
        ├── store/                    # CodexVectorStore — cliente R2DBC pgvector
        └── tasks/                    # 12 implementaciones de tareas (+ DocNode, FontStyle)
```

## Tareas registradas

12 tareas en 4 grupos de taxonomía (ver `CodexPlugin.kt`):

| Grupo | Tareas |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## Contratos N0 (de workspace-bom MEMPHIS)

Codex importa `education.cccp:workspace-bom:0.0.1` como BOM de plataforma y
consume directamente:

| Contrato | Artefacto | Proporciona |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

Otros contratos N0 disponibles vía el BOM (no todos importados directamente por codex):
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`.

## Dependencias clave

- **koog-agents** 1.0.0 — orquestador agentico (DSL Kotlin StateGraph)
- **langchain4j** 1.14.1 — proveedores LLM, RAG, embeddings
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — embeddings de frases ONNX
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — pgvector reactivo
- **kotlinx-coroutines** 1.9.0 (core + reactive) — puente R2DBC
- **Apache PDFBox** 3.0.4 — extracción de texto/estructura de PDF
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — detección de formato
- **Flexmark** 0.64.8 (`flexmark-all`) — procesamiento Markdown
- **AsciidoctorJ** 3.0.0 — parseo/renderizado de AsciiDoc
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — serialización JSON/YAML
- **kotlinx-serialization-json** 1.7.3

`buildscript` fuerza `org.jetbrains:annotations:26.0.2-1` para sobrescribir el
pin estricto de Gradle (`{strictly 13.0}`) que de otro modo rompería las
transitivas de koog/flexmark.

## Matriz de tests

Las fuentes viven en `codex-plugin/src/test/kotlin/`:

| Ámbito | Ubicación | Cantidad |
|------|----------|-------|
| Unitarios (JUnit5) | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 clases spec |
| BDD (Cucumber) | `codex/bdd/PipelineSteps.kt` + 5 archivos `.feature` | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| Integración | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

Todos los tests usan `useJUnitPlatform()`; el logging de tests emite eventos
`FAILED` + `SKIPPED`. Testcontainers provee contenedores tipo
`pgvector/pgvector:pg17` vía `testcontainers-postgresql` 1.21.4 y `docker-java`
3.7.0 (transporte httpclient5).

**No** hay tarea dividida `testFast`/`testAll`/`testEpics` ni puerta
`koverVerify` definida aquí — la única tarea `:codex-plugin:check` ejecuta el
suite completo JUnit5 + Cucumber. Kover emite reportes XML + HTML pero no está
conectado a `check` (`onCheck = false`).

### Limitación de cobertura conocida

El agente on-fly de Kover 0.9.8 no puede instrumentar clases cargadas por Gradle
TestKit (`ProjectBuilder` ejecuta el plugin en un classloader separado). Los
tests de `CodexPlugin`, `CodexExtension`, etc. pasan pero su cobertura se
reporta como 0 %. Ruta de actualización: instrumentación offline de Kover 1.x
(aún no liberada).

## Afinación JVM

- **Java**: 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin**: `jvmTarget = JVM_24`, `-Xjsr305=strict`
- Heap: ajustar con `GRADLE_OPTS="-Xmx2g"` para ejecuciones pesadas de ingesta

## Comandos de build

```bash
./gradlew build -x test                  # solo compilar
./gradlew :codex-plugin:check           # tests completos (JUnit5 + Cucumber)
./gradlew :codex-plugin:test            # solo tests unitarios JUnit5
./gradlew koverHtmlReport               # reporte HTML de cobertura (manual)
./gradlew publishToMavenLocal           # publicación local
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## Pipeline CI

`.github/workflows/test.yml` define un único job **Build & Test**
(`ubuntu-latest`, timeout 15 min):

1. Checkout (`actions/checkout@v4`)
2. Configurar JDK 24 Temurin (`actions/setup-java@v4`)
3. Configurar Gradle (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

**No** hay job de publish-on-tag en este workflow; la publicación a Maven
Central es dirigida por la tarea `publishAggregationToCentralPortal` invocada
manualmente / en un flujo de publicación aparte (ver sección NMCP en
`AGENTS.adoc`).

## Publicación (NMCP)

Configurado en `codex-plugin/build.gradle.kts`:

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- Repositorio `mavenCentral()` (no el staging legacy de Sonatype)
- `signing { useGpgCmd() }` — se omite cuando `CI == "true"` o la versión termina en `-SNAPSHOT`
- POM declarado en **todas** las `withType<MavenPublication>` (no solo `pluginMaven`):
  licencia Apache 2.0, developer `cccp-education`, SCM → `github.com/cccp-education/codex`
- JARs de Sources + javadoc (`withSourcesJar()`, `withJavadocJar()`)
- Bloque opcional `<relocation>` activado por `-Prem relocationGroup=<namespace>`
  (preparado para futura migración de `groupId`; actualmente inerte)

Nota: el `settings.gradle.kts` aquí **no** incluye el plugin de ajustes `nmcp`;
este repositorio codex-gradle publica `codex-plugin` directamente vía
`maven-publish` + el flujo de tarea `publishAggregationToCentralPortal`
documentado en `AGENTS.adoc`.

## Instancias Ollama (restricción global)

Los puertos `11434–11436` están **prohibidos**. Rotar sobre `11437–11465`
(29 puertos). Modelos autorizados: `gpt-oss:120b-cloud`, `gemma4:31b-cloud`.

## Contribuir

1. El build compila: `./gradlew build -x test`
2. Tests en verde: `./gradlew :codex-plugin:check`
3. Sin regresión de CVE: mantener el force-pin de `org.jetbrains:annotations` en `26.0.2-1`
4. Seguir convenciones DDD (value objects, ports/adapters, sin fugas)
5. Respetar la frontera: codex = **READ + RAG**; no añadir lógica WRITE/PUBLISH
   (eso corresponde a `document-gradle`)

## Documentación de arquitectura

- [AGENT.adoc](../AGENT.adoc) — Reglas absolutas (commits, secretos, clasificación)
- [BACKLOG.adoc](../BACKLOG.adoc) — Backlog de publicación EPIC PUB
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — Frontera Codex↔Document
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — Taxonomía unificada de tareas
- `gradle/libs.versions.toml` (catálogo de módulo) — versiones canónicas de dependencias

## Licencia

Apache License 2.0 — ver [LICENSE](../LICENSE).

---

_Parte del ecosistema CCCP Education — `groupId: education.cccp`._