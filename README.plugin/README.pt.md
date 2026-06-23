<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — Aspectos internos do plugin

> Guia para desenvolvedores e colaboradores do plugin Gradle `codex-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **Versão**: `0.0.1` · **Grupo**: `education.cccp` · **ID do plugin**: `education.cccp.codex`
- **Toolchain**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **Build**: `./gradlew build -x test` · **Testes**: `./gradlew :codex-plugin:check` · **Cobertura**: Kover 0.9.8 (on-fly; ver limitação conhecida abaixo)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | **Português** | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## Disposição de módulos

```
codex-gradle/
├── build.gradle.kts                 # placeholder raiz (plugin readme desabilitado)
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # catálogo raiz (versões de plugins)
└── codex-plugin/
    ├── build.gradle.kts              # módulo do plugin (publicação, signing, kover)
    ├── gradle/libs.versions.toml     # catálogo do módulo (todas as versões de dependências)
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # ponto de entrada do plugin — registra 12 tarefas
        ├── CodexExtension.kt         # extensão `codex { ... }` (zone + pgvector)
        ├── Metadata.kt               # formato de metadados pivot do workspace
        ├── LicenseZoneDetector.kt    # Auto-detectar zona OSS/CSS/UNKNOWN no load
        ├── ocr/                      # contratos OCR (OcrConfig, OcrRequest, OcrResult, engines)
        ├── store/                    # CodexVectorStore — cliente R2DBC pgvector
        └── tasks/                    # 12 implementações de tarefas (+ DocNode, FontStyle)
```

## Tarefas registradas

12 tarefas em 4 grupos de taxonomia (ver `CodexPlugin.kt`):

| Grupo | Tarefas |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## Contratos N0 (de workspace-bom MEMPHIS)

Codex importa `education.cccp:workspace-bom:0.0.1` como BOM de plataforma e
consome diretamente:

| Contrato | Artefato | Fornece |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

Outros contratos N0 disponíveis via o BOM (nem todos importados diretamente por codex):
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`.

## Dependências-chave

- **koog-agents** 1.0.0 — orquestrador agentico (DSL Kotlin StateGraph)
- **langchain4j** 1.14.1 — provedores LLM, RAG, embeddings
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — embeddings de frases ONNX
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — pgvector reativo
- **kotlinx-coroutines** 1.9.0 (core + reactive) — ponte R2DBC
- **Apache PDFBox** 3.0.4 — extração de texto/estrutura de PDF
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — detecção de formato
- **Flexmark** 0.64.8 (`flexmark-all`) — processamento Markdown
- **AsciidoctorJ** 3.0.0 — parse/renderização de AsciiDoc
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — serialização JSON/YAML
- **kotlinx-serialization-json** 1.7.3

O `buildscript` força `org.jetbrains:annotations:26.0.2-1` para sobrescrever o
pin estrito do Gradle (`{strictly 13.0}`) que de outro modo quebraria as
transitivas de koog/flexmark.

## Matriz de testes

As fontes vivem em `codex-plugin/src/test/kotlin/`:

| Escopo | Localização | Contagem |
|------|----------|-------|
| Unitários (JUnit5) | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 classes spec |
| BDD (Cucumber) | `codex/bdd/PipelineSteps.kt` + 5 arquivos `.feature` | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| Integração | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

Todos os testes usam `useJUnitPlatform()`; o logging de testes emite eventos
`FAILED` + `SKIPPED`. O Testcontainers provê containers estilo
`pgvector/pgvector:pg17` via `testcontainers-postgresql` 1.21.4 e `docker-java`
3.7.0 (transporte httpclient5).

**Não** há tarefa dividida `testFast`/`testAll`/`testEpics` nem porta
`koverVerify` definida aqui — a única tarefa `:codex-plugin:check` executa a
suíte completa JUnit5 + Cucumber. O Kover emite relatórios XML + HTML mas não
está conectado ao `check` (`onCheck = false`).

### Limitação de cobertura conhecida

O agente on-fly do Kover 0.9.8 não consegue instrumentar classes carregadas pelo
Gradle TestKit (`ProjectBuilder` roda o plugin num classloader separado). Os
testes de `CodexPlugin`, `CodexExtension`, etc. passam mas a cobertura é
reportada como 0 %. Caminho de upgrade: instrumentação offline do Kover 1.x
(ainda não lançada).

## Ajuste JVM

- **Java**: 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin**: `jvmTarget = JVM_24`, `-Xjsr305=strict`
- Heap: ajustar via `GRADLE_OPTS="-Xmx2g"` para execuções pesadas de ingesta

## Comandos de build

```bash
./gradlew build -x test                  # apenas compilar
./gradlew :codex-plugin:check           # testes completos (JUnit5 + Cucumber)
./gradlew :codex-plugin:test            # apenas testes unitários JUnit5
./gradlew koverHtmlReport               # relatório HTML de cobertura (manual)
./gradlew publishToMavenLocal           # publicação local
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## Pipeline CI

`.github/workflows/test.yml` define um único job **Build & Test**
(`ubuntu-latest`, timeout 15 min):

1. Checkout (`actions/checkout@v4`)
2. Configurar JDK 24 Temurin (`actions/setup-java@v4`)
3. Configurar Gradle (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

**Não** há job de publish-on-tag neste workflow; a publicação no Maven Central é
dirigida pela tarefa `publishAggregationToCentralPortal` invocada manualmente /
em um fluxo de publicação separado (ver seção NMCP em `AGENTS.adoc`).

## Publicação (NMCP)

Configurado em `codex-plugin/build.gradle.kts`:

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- Repositório `mavenCentral()` (não o staging legado do Sonatype)
- `signing { useGpgCmd() }` — omitido quando `CI == "true"` ou a versão termina em `-SNAPSHOT`
- POM declarado em **todas** as `withType<MavenPublication>` (não apenas `pluginMaven`):
  licença Apache 2.0, developer `cccp-education`, SCM → `github.com/cccp-education/codex`
- JARs de Sources + javadoc (`withSourcesJar()`, `withJavadocJar()`)
- Bloco opcional `<relocation>` ativado por `-Prem relocationGroup=<namespace>`
  (preparado para futura migração de `groupId`; atualmente inerte)

Nota: o `settings.gradle.kts` aqui **não** inclui o plugin de settings `nmcp`;
este repositório codex-gradle publica `codex-plugin` diretamente via
`maven-publish` + o fluxo de tarefa `publishAggregationToCentralPortal`
documentado em `AGENTS.adoc`.

## Instâncias Ollama (restrição global)

As portas `11434–11436` são **proibidas**. Rotacionar sobre `11437–11465`
(29 portas). Modelos autorizados: `gpt-oss:120b-cloud`, `gemma4:31b-cloud`.

## Contribuir

1. O build compila: `./gradlew build -x test`
2. Testes verdes: `./gradlew :codex-plugin:check`
3. Sem regressão de CVE: manter o force-pin de `org.jetbrains:annotations` em `26.0.2-1`
4. Seguir convenções DDD (value objects, ports/adapters, sem vazamentos)
5. Respeitar a fronteira: codex = **READ + RAG**; não adicionar lógica WRITE/PUBLISH
   (isso pertence a `document-gradle`)

## Documentação de arquitetura

- [AGENT.adoc](../AGENT.adoc) — Regras absolutas (commits, segredos, classificação)
- [BACKLOG.adoc](../BACKLOG.adoc) — Backlog de publicação EPIC PUB
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — Fronteira Codex↔Document
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — Taxonomia unificada de tarefas
- `gradle/libs.versions.toml` (catálogo do módulo) — versões canônicas de dependências

## Licença

Apache License 2.0 — ver [LICENSE](../LICENSE).

---

_Parte do ecossistema CCCP Education — `groupId: education.cccp`._