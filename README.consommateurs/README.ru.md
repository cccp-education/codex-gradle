<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — Руководство потребителя

> Gradle-плагин для получения документов: PDF/EPUB → извлечение → фрагментация → pgvector RAG.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **Версия**: `0.0.1` · **Группа**: `education.cccp` · **ID плагина**: `education.cccp.codex`
- **Сборка**: `./gradlew build` · **Тесты**: `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **Восходящая зависимость**: [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — используется как единственный источник истины для `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig`.

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | **Русский** | [اردو](README.ur.md)

---

## Что он делает

`codex-gradle` получает неструктурированные документы (PDF, EPUB), извлекает
структурированный текст и иерархию книги, преобразует в Markdown/AsciiDoc,
выполняет семантическую фрагментацию, векторизует с помощью ONNX
(`all-MiniLM-L6-v2`), хранит эмбеддинги в **PostgreSQL + pgvector** через
**R2DBC** и предоставляет поиск по косинусной схожести для питания слоя **RAG**
и **Графа знаний** CCCP Education.

Он находится выше по потоку от [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle)
в экосистеме CCCP Education:

```
document corpus → codex-gradle (READ + RAG) → [codebase-gradle] → koog agents → output
```

Граница (задокументирована в `.agents/ARCHITECTURE_BOUNDARY.adoc`): **codex = READ +
RAG**; родственный плагин `document-gradle` обрабатывает **WRITE + PUBLISH**. Два
N2-плагина параллельны, а не уложены в стек.

## Быстрый старт

### 1. Применить плагин

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. Настроить соединение pgvector (необязательно — показаны значения по умолчанию)

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. Получить и загрузить корпус

```bash
./gradlew transformCorpusToPdf          # автоопределение PDF/EPUB → извлечение → фрагментация → JSON
./gradlew collectIngest                 # векторизация фрагментов → pgvector (размер пакета 32)
./gradlew collectRetrieve --query="..." # косинусная схожесть top-K
```

## Доступные задачи

| Задача | Группа | Описание |
|------|-------|-------------|
| `collectText`              | collect  | Извлечение необработанного текста из PDF с типографскими метаданными |
| `collectBookStructure`     | collect  | Извлечение структуры PDF (заголовки/разделы) → иерархический `.adoc` |
| `collectEpubStructure`      | collect  | Извлечение структуры EPUB (XHTML → `.adoc` + блоки кода) |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | Векторизация ONNX → pgvector через R2DBC (пакет по умолчанию 32) |
| `collectRetrieve`          | collect  | Семантический поиск по косинусной схожести в pgvector (top-K по умолчанию 10) |
| `transformToJsonLdd`       | transform | Разбор `.adoc` через AsciidoctorJ → JSON LDD |
| `transformToMarkdown`      | transform | Структура AsciiDoc → Markdown (иерархия и блоки кода сохранены) |
| `transformChunk`           | transform | Семантическая фрагментация по разделам (1 фрагмент на заголовок) |
| `transformCorpusToPdf`     | transform | Составной конвейер (автоопределение PDF/EPUB): извлечение → Markdown → фрагментация → JSON |
| `generateCompositeContext` | generate | Семантический поиск через `CodexVectorStore` → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | Агрегация фрагментов → KB многоформатов (JSON-L, Markdown, AsciiDoc) |

Задачи следуют единой таксономии рабочего пространства (4 глагола: GENERATE /
COLLECT / TRANSFORM / DEPLOY) — см. `TAXONOMIE_WORKSPACE.adoc`.

## DSL расширения

```gradle
codex {
    // Лицензионная зона (автоопределение через LicenseZoneDetector; OSS/CSS/UNKNOWN)
    zone = codex.LicenseZone.OSS

    // Параметры соединения pgvector (все по умолчанию равны показанным значениям)
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

Задача `generateCompositeContext` также читает следующие свойства Gradle:

| Свойство | По умолчанию | Назначение |
|----------|---------|---------|
| `query`  | `"architecture du workspace"` | Запрос семантического поиска |
| `topK`   | `"10"`                         | Количество ближайших соседей |

Пример:

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json (совместимо с runner)
```

## Предварительные требования

- **Java** 24 (toolchain Kotlin 2.3.20, `jvmTarget = 24`)
- **Gradle** 9.5.1
- **PostgreSQL** 15+ с расширением `pgvector` (для `collectIngest`/`collectRetrieve`)
- **Docker** (для интеграционных тестов на Testcontainers)

## Сборка и тесты

```bash
./gradlew build                            # полная сборка (компиляция + тесты)
./gradlew build -x test                    # только компиляция
./gradlew :codex-plugin:check              # тесты (JUnit5 + Cucumber BDD)
./gradlew publishToMavenLocal              # локальная публикация
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## Устранение неполадок

| Симптом | Решение |
|---------|-----|
| Конфликт версий `org.jetbrains:annotations` (Gradle фиксирует 13.0) | Уже принудительно `26.0.2-1` в `buildscript`; перезапустите с `--refresh-dependencies` |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Postgres Testcontainer завис | `docker rm -f $(docker ps -aq --filter name=postgres)` и повторите |
| Kover показывает 0% на классах плагина | Известное ограничение: on-fly агент Kover 0.9.8 не может перехватить classloader `ProjectBuilder` Gradle TestKit. См. примечание в `build.gradle.kts`. |

## Лицензия

Apache License 2.0 — см. [LICENSE](../LICENSE).

---

_Часть экосистемы CCCP Education — `groupId: education.cccp`._