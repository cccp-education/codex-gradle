<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — Guía del consumidor

> Plugin de Gradle para adquisición de documentos: PDF/EPUB → extracción → fragmentación → pgvector RAG.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **Versión**: `0.0.1` · **Grupo**: `education.cccp` · **ID del plugin**: `education.cccp.codex`
- **Build**: `./gradlew build` · **Tests**: `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **Dependencia upstream**: [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — consumida como única fuente de verdad para `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig`.

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | **Español** | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## Qué hace

`codex-gradle` adquiere documentos no estructurados (PDF, EPUB), extrae texto
estructurado + jerarquía del libro, convierte a Markdown/AsciiDoc, realiza
fragmentación semántica, vectoriza con ONNX (`all-MiniLM-L6-v2`), almacena
embeddings en **PostgreSQL + pgvector** sobre **R2DBC**, y expone recuperación
por similitud coseno para alimentar la capa **RAG** y el **Grafo de
Conocimiento** de CCCP Education.

Se sitúa aguas arriba de [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle)
en el ecosistema CCCP Education:

```
document corpus → codex-gradle (READ + RAG) → [codebase-gradle] → koog agents → output
```

Frontera (documentada en `.agents/ARCHITECTURE_BOUNDARY.adoc`): **codex = READ +
RAG**; el plugin hermano `document-gradle` gestiona **WRITE + PUBLISH**. Los dos
plugins N2 son paralelos, no apilados.

## Inicio rápido

### 1. Aplicar el plugin

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. Configurar la conexión pgvector (opcional — se muestran los valores por defecto)

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. Adquirir e ingestar un corpus

```bash
./gradlew transformCorpusToPdf          # autodetectar PDF/EPUB → extraer → fragmentar → JSON
./gradlew collectIngest                 # vectorizar fragmentos → pgvector (lote 32)
./gradlew collectRetrieve --query="..." # similitud coseno top-K
```

## Tareas disponibles

| Tarea | Grupo | Descripción |
|------|-------|-------------|
| `collectText`              | collect  | Extracción de texto en bruto del PDF con metadatos tipográficos |
| `collectBookStructure`     | collect  | Extracción de estructura del PDF (títulos/secciones) → `.adoc` jerárquico |
| `collectEpubStructure`      | collect  | Extracción de estructura EPUB (XHTML → `.adoc` + bloques de código) |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | Vectorización ONNX → pgvector vía R2DBC (lote por defecto 32) |
| `collectRetrieve`          | collect  | Búsqueda semántica por similitud coseno en pgvector (top-K por defecto 10) |
| `transformToJsonLdd`       | transform | Parse `.adoc` vía AsciidoctorJ → JSON LDD |
| `transformToMarkdown`      | transform | Estructura AsciiDoc → Markdown (jerarquía + bloques de código preservados) |
| `transformChunk`           | transform | Fragmentación semántica por secciones (1 fragmento por encabezado) |
| `transformCorpusToPdf`     | transform | Pipeline compuesto (autodetectar PDF/EPUB): extraer → Markdown → fragmentar → JSON |
| `generateCompositeContext` | generate | Búsqueda semántica vía `CodexVectorStore` → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | Agregar fragmentos → KB multi-formato (JSON-L, Markdown, AsciiDoc) |

Las tareas siguen la taxonomía unificada del workspace (4 verbos: GENERATE /
COLLECT / TRANSFORM / DEPLOY) — ver `TAXONOMIE_WORKSPACE.adoc`.

## DSL de extensión

```gradle
codex {
    // Zona de licencia (autodetectada vía LicenseZoneDetector; OSS/CSS/UNKNOWN)
    zone = codex.LicenseZone.OSS

    // Parámetros de conexión pgvector (todos con los valores mostrados por defecto)
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

La tarea `generateCompositeContext` también lee estas propiedades de Gradle:

| Propiedad | Por defecto | Propósito |
|----------|---------|---------|
| `query`  | `"architecture du workspace"` | Consulta de búsqueda semántica |
| `topK`   | `"10"`                         | Número de vecinos más cercanos |

Ejemplo:

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json (compatible con runner)
```

## Requisitos previos

- **Java** 24 (toolchain Kotlin 2.3.20, `jvmTarget = 24`)
- **Gradle** 9.5.1
- **PostgreSQL** 15+ con extensión `pgvector` (para `collectIngest`/`collectRetrieve`)
- **Docker** (para tests de integración con Testcontainers)

## Build y test

```bash
./gradlew build                            # build completo (compila + tests)
./gradlew build -x test                    # solo compilar
./gradlew :codex-plugin:check              # tests (JUnit5 + Cucumber BDD)
./gradlew publishToMavenLocal              # publicación local
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## Solución de problemas

| Síntoma | Solución |
|---------|-----|
| Conflicto de versión `org.jetbrains:annotations` (Gradle fija 13.0) | Ya forzado a `26.0.2-1` en `buildscript`; reejecutar con `--refresh-dependencies` |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Testcontainer de Postgres atascado | `docker rm -f $(docker ps -aq --filter name=postgres)` y reintentar |
| Kover reporta 0 % en clases del plugin | Limitación conocida: el agente on-fly de Kover 0.9.8 no puede interceptar el classloader `ProjectBuilder` de Gradle TestKit. Ver nota en `build.gradle.kts`. |

## Licencia

Apache License 2.0 — ver [LICENSE](../LICENSE).

---

_Parte del ecosistema CCCP Education — `groupId: education.cccp`._