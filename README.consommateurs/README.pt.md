<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — Guia do consumidor

> Plugin de Gradle para aquisição de documentos: PDF/EPUB → extração → fragmentação → pgvector RAG.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **Versão**: `0.0.1` · **Grupo**: `education.cccp` · **ID do plugin**: `education.cccp.codex`
- **Build**: `./gradlew build` · **Testes**: `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **Dependência upstream**: [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — consumida como única fonte de verdade para `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig`.

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | **Português** | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## O que faz

O `codex-gradle` adquire documentos não estruturados (PDF, EPUB), extrai texto
estruturado + hierarquia do livro, converte para Markdown/AsciiDoc, realiza
fragmentação semântica, vetoriza com ONNX (`all-MiniLM-L6-v2`), armazena
embeddings no **PostgreSQL + pgvector** sobre **R2DBC**, e expõe recuperação
por similaridade de cosseno para alimentar a camada **RAG** e o **Grafo de
Conhecimento** do CCCP Education.

Ele fica a montante de [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle)
no ecossistema CCCP Education:

```
document corpus → codex-gradle (READ + RAG) → [codebase-gradle] → koog agents → output
```

Fronteira (documentada em `.agents/ARCHITECTURE_BOUNDARY.adoc`): **codex = READ +
RAG**; o plugin irmão `document-gradle` cuida de **WRITE + PUBLISH**. Os dois
plugins N2 são paralelos, não empilhados.

## Início rápido

### 1. Aplicar o plugin

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. Configurar a conexão pgvector (opcional — padrões exibidos)

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. Adquirir e ingerir um corpus

```bash
./gradlew transformCorpusToPdf          # auto-detectar PDF/EPUB → extrair → fragmentar → JSON
./gradlew collectIngest                 # vetorizar fragmentos → pgvector (lote 32)
./gradlew collectRetrieve --query="..." # similaridade de cosseno top-K
```

## Tarefas disponíveis

| Tarefa | Grupo | Descrição |
|------|-------|-------------|
| `collectText`              | collect  | Extração de texto bruto do PDF com metadados tipográficos |
| `collectBookStructure`     | collect  | Extração da estrutura do PDF (títulos/seções) → `.adoc` hierárquico |
| `collectEpubStructure`      | collect  | Extração da estrutura do EPUB (XHTML → `.adoc` + blocos de código) |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | Vetorização ONNX → pgvector via R2DBC (lote padrão 32) |
| `collectRetrieve`          | collect  | Busca semântica por similaridade de cosseno no pgvector (top-K padrão 10) |
| `transformToJsonLdd`       | transform | Parse `.adoc` via AsciidoctorJ → JSON LDD |
| `transformToMarkdown`      | transform | Estrutura AsciiDoc → Markdown (hierarquia + blocos de código preservados) |
| `transformChunk`           | transform | Fragmentação semântica por seção (1 fragmento por cabeçalho) |
| `transformCorpusToPdf`     | transform | Pipeline composto (auto-detectar PDF/EPUB): extrair → Markdown → fragmentar → JSON |
| `generateCompositeContext` | generate | Busca semântica via `CodexVectorStore` → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | Agregar fragmentos → KB multi-formato (JSON-L, Markdown, AsciiDoc) |

As tarefas seguem a taxonomia unificada do workspace (4 verbos: GENERATE /
COLLECT / TRANSFORM / DEPLOY) — ver `TAXONOMIE_WORKSPACE.adoc`.

## DSL de extensão

```gradle
codex {
    // Zona de licença (auto-detectada via LicenseZoneDetector; OSS/CSS/UNKNOWN)
    zone = codex.LicenseZone.OSS

    // Parâmetros de conexão pgvector (todos com os valores padrão exibidos)
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

A tarefa `generateCompositeContext` também lê estas propriedades do Gradle:

| Propriedade | Padrão | Propósito |
|----------|---------|---------|
| `query`  | `"architecture du workspace"` | Consulta de busca semântica |
| `topK`   | `"10"`                         | Número de vizinhos mais próximos |

Exemplo:

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json (compatível com runner)
```

## Pré-requisitos

- **Java** 24 (toolchain Kotlin 2.3.20, `jvmTarget = 24`)
- **Gradle** 9.5.1
- **PostgreSQL** 15+ com extensão `pgvector` (para `collectIngest`/`collectRetrieve`)
- **Docker** (para testes de integração com Testcontainers)

## Build e teste

```bash
./gradlew build                            # build completo (compila + testes)
./gradlew build -x test                    # apenas compilar
./gradlew :codex-plugin:check              # testes (JUnit5 + Cucumber BDD)
./gradlew publishToMavenLocal              # publicação local
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## Solução de problemas

| Sintoma | Correção |
|---------|-----|
| Conflito de versão `org.jetbrains:annotations` (Gradle fixa 13.0) | Já forçado para `26.0.2-1` em `buildscript`; reexecute com `--refresh-dependencies` |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Testcontainer do Postgres travado | `docker rm -f $(docker ps -aq --filter name=postgres)` e tentar novamente |
| Kover reporta 0% nas classes do plugin | Limitação conhecida: o agente on-fly do Kover 0.9.8 não consegue interceptar o classloader `ProjectBuilder` do Gradle TestKit. Ver nota em `build.gradle.kts`. |

## Licença

Apache License 2.0 — ver [LICENSE](../LICENSE).

---

_Parte do ecossistema CCCP Education — `groupId: education.cccp`._