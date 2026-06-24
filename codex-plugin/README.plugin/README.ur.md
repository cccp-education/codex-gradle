<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — پلگ ان کے اندرونی پہلو

> `codex-plugin` Gradle پلگ ان کے لیے ڈویلپرز اور شرکاء کی رہنمائی۔

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **ورژن**: `0.0.1` · **گروپ**: `education.cccp` · **پلگ ان ID**: `education.cccp.codex`
- **ٹول چین**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **بلڈ**: `./gradlew build -x test` · **ٹیسٹ**: `./gradlew :codex-plugin:check` · **کورج**: Kover 0.9.8 (on-fly؛ نیچے معروف حد دیکھیں)

🌐 زبانیں: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | **اردو**

---

## ماڈیول کا ترتیب

```
codex-gradle/
├── build.gradle.kts                 # روٹ پلیس ہولڈر (readme پلگ ان غیر فعال)
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # روٹ کیٹلاگ (پلگ ان ورژن)
└── codex-plugin/
    ├── build.gradle.kts              # پلگ ان ماڈیول (اشاعت، سائننگ، kover)
    ├── gradle/libs.versions.toml     # ماڈیول کیٹلاگ (تمام انحصاری ورژن)
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # پلگ ان کا داخلہ نقطہ — 12 کام رجسٹر کرتا ہے
        ├── CodexExtension.kt         # `codex { ... }` توسیع (zone + pgvector)
        ├── Metadata.kt               # ورک اسپیس پوٹ میٹاڈیٹا فارمیٹ
        ├── LicenseZoneDetector.kt    # لوڈ پر خودکار شناخت OSS/CSS/UNKNOWN زون
        ├── ocr/                      # OCR معاہدے (OcrConfig, OcrRequest, OcrResult, engines)
        ├── store/                    # CodexVectorStore — R2DBC pgvector کلائنٹ
        └── tasks/                    # 12 کام کی تنفیذ (+ DocNode, FontStyle)
```

## رجسٹرڈ کام

4 درجہ بندی گروپوں میں 12 کام (`CodexPlugin.kt` دیکھیں):

| گروپ | کام |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## N0 معاہدے (workspace-bom MEMPHIS سے)

Codex `education.cccp:workspace-bom:0.0.1` کو پلیٹ فارم BOM کے طور پر درآمد کرتا ہے اور براہ راست استعمال کرتا ہے:

| معاہدہ | آرٹیفیکٹ | فراہم کرتا ہے |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

BOM کے ذریعے دستیاب دیگر N0 معاہدے (تمام codex کی براہ راست درآمد نہیں):
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`۔

## کلیدی انحصارات

- **koog-agents** 1.0.0 — ایجنٹک آرکیسٹریٹر (DSL Kotlin StateGraph)
- **langchain4j** 1.14.1 — LLM فراہم کنندگان، RAG، ایمبیڈنگز
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — ONNX جملہ ایمبیڈنگز
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — ریکٹیو pgvector
- **kotlinx-coroutines** 1.9.0 (core + reactive) — R2DBC پل
- **Apache PDFBox** 3.0.4 — PDF متن/ساخت استخراج
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — فارمیٹ تشخیص
- **Flexmark** 0.64.8 (`flexmark-all`) — Markdown پروسیسنگ
- **AsciidoctorJ** 3.0.0 — AsciiDoc پارس/رینڈر
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — JSON/YAML سیریلائزیشن
- **kotlinx-serialization-json** 1.7.3

`buildscript` `org.jetbrains:annotations:26.0.2-1` کو Gradle کی سخت پن
(`{strictly 13.0}`) پر مجبور کرتا ہے، ورنہ koog/flexmark ٹرانزٹوز ٹوٹ جاتے۔

## ٹیسٹ میٹرکس

ذرائع `codex-plugin/src/test/kotlin/` کے تحت:

| دائرہ | مقام | تعداد |
|------|----------|-------|
| یونٹ (JUnit5) | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 spec کلاسز |
| BDD (Cucumber) | `codex/bdd/PipelineSteps.kt` + 5 `.feature` فائلیں | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| انضمام | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

تمام ٹیسٹ `useJUnitPlatform()` استعمال کرتے ہیں؛ ٹیسٹ لاگنگ `FAILED` + `SKIPPED` واقعات خارج کرتی ہے۔
Testcontainers `testcontainers-postgresql` 1.21.4 اور `docker-java` 3.7.0
(httpclient5 ٹرانسپورٹ) کے ذریعے `pgvector/pgvector:pg17`-طرز کے کنٹینرز فراہم کرتا ہے۔

یہاں کوئی `testFast`/`testAll`/`testEpics` تقسیم کام نہیں اور کوئی
`koverVerify` گیٹ وضاحت نہیں — واحد `:codex-plugin:check` کام مکمل
JUnit5 + Cucumber سویٹ چلاتا ہے۔ Kover XML + HTML رپورٹس خارج کرتا ہے لیکن
`check` سے منسلک نہیں (`onCheck = false`)۔

### معروف کورج کی حد

Kover 0.9.8 on-fly ایجنٹ Gradle TestKit کے ذریعے لوڈ کیے گئے کلاسز کو
انسٹرومینٹ نہیں کر سکتا (`ProjectBuilder` پلگ ان کو علیحدہ کلاس لوڈر میں چلاتا ہے)۔
`CodexPlugin`, `CodexExtension` وغیرہ کے ٹیسٹ پاس ہوتے ہیں لیکن ان کا کورج 0%
رپورٹ ہوتا ہے۔ اپ گریڈ راستہ: Kover 1.x آف لائن انسٹرومینٹیشن (ابھی جاری نہیں)۔

## JVM ٹیوننگ

- **Java**: 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin**: `jvmTarget = JVM_24`, `-Xjsr305=strict`
- ہیپ: `GRADLE_OPTS="-Xmx2g"` کے ذریعے انجسٹ-بھاری رنز کے لیے ٹیون کریں

## بلڈ کمانڈز

```bash
./gradlew build -x test                  # صرف کمپائل
./gradlew :codex-plugin:check           # مکمل ٹیسٹ (JUnit5 + Cucumber)
./gradlew :codex-plugin:test            # صرف JUnit5 یونٹ ٹیسٹ
./gradlew koverHtmlReport               # کورج HTML رپورٹ (مینول)
./gradlew publishToMavenLocal           # مقامی اشاعت
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## CI پائپ لائن

`.github/workflows/test.yml` ایک **Build & Test** کام وضاحت کرتا ہے
(`ubuntu-latest`, ٹائم آؤٹ 15 منٹ):

1. Checkout (`actions/checkout@v4`)
2. JDK 24 Temurin سیٹ اپ (`actions/setup-java@v4`)
3. Gradle سیٹ اپ (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

اس ورک فلو میں کوئی پبلش-آن-ٹیگ کام نہیں؛ Maven Central اشاعت
`publishAggregationToCentralPortal` کام کے ذریعے مینول/علیحدہ اشاعت کے
پر بہار میں چلائی جاتی ہے (`AGENTS.adoc` میں NMCP سیکشن دیکھیں)۔

## اشاعت (NMCP)

`codex-plugin/build.gradle.kts` میں تشکیل شدہ:

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- `mavenCentral()` ریپوزیٹری (لیجیسی Sonatype staging نہیں)
- `signing { useGpgCmd() }` — جب `CI == "true"` یا ورژن `-SNAPSHOT` پر ختم ہو تو چھوڑا جاتا ہے
- POM **تمام** `withType<MavenPublication>` پر بیان کردہ (صرف `pluginMaven` نہیں):
  Apache 2.0 لائسنس، ڈویلپر `cccp-education`, SCM → `github.com/cccp-education/codex`
- Sources + javadoc JARs (`withSourcesJar()`, `withJavadocJar()`)
- اختیاری `<relocation>` بلاک `-Prem relocationGroup=<namespace>` سے فعال
  (مستقبل کے `groupId` مائیگریشن کے لیے تیار؛ فی الحال غیر فعال)

نوٹ: یہاں `settings.gradle.kts` `nmcp` سیٹنگز پلگ ان شامل نہیں کرتا؛
یہ codex-gradle ریپوزیٹری `codex-plugin` کو `maven-publish` +
`AGENTS.adoc` میں دستاویز کردہ `publishAggregationToCentralPortal` کام کے
پر بہار کے ذریعے براہ راست شائع کرتی ہے۔

## Ollama انسٹانسز (عالمی پابندی)

پورٹ `11434–11436` **ممنوع** ہیں۔ `11437–11465` (29 پورٹس) پر گھمائیں۔
اجازت یافتہ ماڈلز: `gpt-oss:120b-cloud`, `gemma4:31b-cloud`۔

## شراکت

1. بلڈ کمپائل ہو: `./gradlew build -x test`
2. ٹیسٹ سبز ہوں: `./gradlew :codex-plugin:check`
3. کوئی CVE ریگریشن نہیں: `org.jetbrains:annotations` فورس پن `26.0.2-1` رکھیں
4. DDD روایات پر عمل کریں (value objects, ports/adapters, کوئی لیک نہیں)
5. حد کی پاسداری: codex = **READ + RAG**; WRITE/PUBLISH منطق نہ جوڑیں
   (وہ `document-gradle` کا کام ہے)

## فن تعمیر دستاویزات

- [AGENT.adoc](../AGENT.adoc) — مطلق قواعد (کمٹس، راز، درجہ بندی)
- [BACKLOG.adoc](../BACKLOG.adoc) — EPIC PUB اشاعت بیک لاگ
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — Codex↔Document حد
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — متحدہ کام درجہ بندی
- `gradle/libs.versions.toml` (ماڈیول کیٹلاگ) — کینونیکل انحصاری ورژن

## لائسنس

Apache License 2.0 — دیکھیں [LICENSE](../LICENSE)۔

---

_CCCP Education ماحولیاتی نظام کا حصہ — `groupId: education.cccp`۔_