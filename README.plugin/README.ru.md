<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — Внутреннее устройство плагина

> Руководство для разработчиков и контрибьюторов плагина Gradle `codex-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **Версия**: `0.0.1` · **Группа**: `education.cccp` · **ID плагина**: `education.cccp.codex`
- **Toolchain**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **Сборка**: `./gradlew build -x test` · **Тесты**: `./gradlew :codex-plugin:check` · **Покрытие**: Kover 0.9.8 (on-fly; см. известное ограничение ниже)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | **Русский** | [اردو](README.ur.md)

---

## Расположение модулей

```
codex-gradle/
├── build.gradle.kts                 # корневой плейсхолдер (readme-плагин отключён)
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # корневой каталог (версии плагинов)
└── codex-plugin/
    ├── build.gradle.kts              # модуль плагина (публикация, подписание, kover)
    ├── gradle/libs.versions.toml     # каталог модуля (все версии зависимостей)
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # точка входа плагина — регистрирует 12 задач
        ├── CodexExtension.kt         # расширение `codex { ... }` (zone + pgvector)
        ├── Metadata.kt               # формат pivot-метаданных рабочего пространства
        ├── LicenseZoneDetector.kt    # Автоопределение зоны OSS/CSS/UNKNOWN при загрузке
        ├── ocr/                      # контракты OCR (OcrConfig, OcrRequest, OcrResult, engines)
        ├── store/                    # CodexVectorStore — R2DBC pgvector-клиент
        └── tasks/                    # 12 реализаций задач (+ DocNode, FontStyle)
```

## Зарегистрированные задачи

12 задач в 4 таксономических группах (см. `CodexPlugin.kt`):

| Группа | Задачи |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## N0-контракты (из workspace-bom MEMPHIS)

Codex импортирует `education.cccp:workspace-bom:0.0.1` как платформенный BOM и
напрямую потребляет:

| Контракт | Артефакт | Предоставляет |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

Другие N0-контракты, доступные через BOM (не все импортируются codex напрямую):
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`.

## Ключевые зависимости

- **koog-agents** 1.0.0 — агентный оркестратор (DSL Kotlin StateGraph)
- **langchain4j** 1.14.1 — LLM-провайдеры, RAG, эмбеддинги
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — ONNX-эмбеддинги предложений
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — реактивный pgvector
- **kotlinx-coroutines** 1.9.0 (core + reactive) — мост R2DBC
- **Apache PDFBox** 3.0.4 — извлечение текста/структуры PDF
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — определение формата
- **Flexmark** 0.64.8 (`flexmark-all`) — обработка Markdown
- **AsciidoctorJ** 3.0.0 — парсинг/рендеринг AsciiDoc
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — сериализация JSON/YAML
- **kotlinx-serialization-json** 1.7.3

`buildscript` принудительно устанавливает `org.jetbrains:annotations:26.0.2-1`
чтобы перекрыть строгий пин Gradle (`{strictly 13.0}`), который иначе сломал бы
транзитивные зависимости koog/flexmark.

## Матрица тестов

Исходники находятся в `codex-plugin/src/test/kotlin/`:

| Область | Расположение | Количество |
|------|----------|-------|
| Модульные (JUnit5) | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 spec-классов |
| BDD (Cucumber) | `codex/bdd/PipelineSteps.kt` + 5 файлов `.feature` | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| Интеграционные | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

Все тесты используют `useJUnitPlatform()`; логирование тестов выводит события
`FAILED` + `SKIPPED`. Testcontainers предоставляет контейнеры в стиле
`pgvector/pgvector:pg17` через `testcontainers-postgresql` 1.21.4 и
`docker-java` 3.7.0 (транспорт httpclient5).

Здесь **нет** разделённой задачи `testFast`/`testAll`/`testEpics` и **нет**
шлюза `koverVerify` — единственная задача `:codex-plugin:check` запускает
полный набор JUnit5 + Cucumber. Kover выдаёт XML + HTML-отчёты, но не
подключён к `check` (`onCheck = false`).

### Известное ограничение покрытия

On-fly-агент Kover 0.9.8 не может инструментировать классы, загруженные Gradle
TestKit (`ProjectBuilder` запускает плагин в отдельном classloader). Тесты для
`CodexPlugin`, `CodexExtension` и т. д. проходят, но их покрытие
сообщается как 0 %. Путь обновления: офлайн-инструментирование Kover 1.x
(ещё не выпущено).

## Настройка JVM

- **Java**: 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin**: `jvmTarget = JVM_24`, `-Xjsr305=strict`
- Куча: настройка через `GRADLE_OPTS="-Xmx2g"` для тяжёлых загрузок

## Команды сборки

```bash
./gradlew build -x test                  # только компиляция
./gradlew :codex-plugin:check           # полные тесты (JUnit5 + Cucumber)
./gradlew :codex-plugin:test            # только модульные тесты JUnit5
./gradlew koverHtmlReport               # HTML-отчёт покрытия (вручную)
./gradlew publishToMavenLocal           # локальная публикация
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## CI-пайплайн

`.github/workflows/test.yml` определяет единственную задачу **Build & Test**
(`ubuntu-latest`, таймаут 15 мин):

1. Checkout (`actions/checkout@v4`)
2. Настроить JDK 24 Temurin (`actions/setup-java@v4`)
3. Настроить Gradle (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

В этом рабочем процессе **нет** задачи публикации-по-тегу; публикация в Maven
Central управляется задачей `publishAggregationToCentralPortal`, вызываемой
вручную / в отдельном потоке публикации (см. раздел NMCP в `AGENTS.adoc`).

## Публикация (NMCP)

Настроено в `codex-plugin/build.gradle.kts`:

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- Репозиторий `mavenCentral()` (не устаревший Sonatype staging)
- `signing { useGpgCmd() }` — пропускается при `CI == "true"` или если версия оканчивается на `-SNAPSHOT`
- POM объявлен для **всех** `withType<MavenPublication>` (не только `pluginMaven`):
  лицензия Apache 2.0, разработчик `cccp-education`, SCM → `github.com/cccp-education/codex`
- JAR-ы Sources + javadoc (`withSourcesJar()`, `withJavadocJar()`)
- Опциональный блок `<relocation>`, активируемый через `-Prem relocationGroup=<namespace>`
  (подготовлен для будущей миграции `groupId`; в настоящее время неактивен)

Примечание: `settings.gradle.kts` здесь **не** включает settings-плагин `nmcp`;
этот репозиторий codex-gradle публикует `codex-plugin` напрямую через
`maven-publish` + поток задачи `publishAggregationToCentralPortal`,
задокументированный в `AGENTS.adoc`.

## Экземпляры Ollama (глобальное ограничение)

Порты `11434–11436` **запрещены**. Ротация по `11437–11465` (29 портов).
Авторизованные модели: `gpt-oss:120b-cloud`, `gemma4:31b-cloud`.

## Контрибьюшн

1. Сборка компилируется: `./gradlew build -x test`
2. Тесты зелёные: `./gradlew :codex-plugin:check`
3. Нет регрессии CVE: держать force-pin `org.jetbrains:annotations` на `26.0.2-1`
4. Следовать DDD-конвенциям (value objects, ports/adapters, без утечек)
5. Соблюдать границу: codex = **READ + RAG**; не добавлять логику WRITE/PUBLISH
   (это относится к `document-gradle`)

## Архитектурная документация

- [AGENT.adoc](../AGENT.adoc) — Абсолютные правила (коммиты, секреты, классификация)
- [BACKLOG.adoc](../BACKLOG.adoc) — Бэклог публикации EPIC PUB
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — Граница Codex↔Document
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — Единая таксономия задач
- `gradle/libs.versions.toml` (каталог модуля) — канонические версии зависимостей

## Лицензия

Apache License 2.0 — см. [LICENSE](../LICENSE).

---

_Часть экосистемы CCCP Education — `groupId: education.cccp`._