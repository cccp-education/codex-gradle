<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — الداخلية البرمجية للإضافة

> دليل المطوّرين والمساهمين لإضافة Gradle `codex-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **الإصدار**: `0.0.1` · **المجموعة**: `education.cccp` · **معرف الإضافة**: `education.cccp.codex`
- **سلسلة الأدوات**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **البناء**: `./gradlew build -x test` · **الاختبارات**: `./gradlew :codex-plugin:check` · **التغطية**: Kover 0.9.8 (on-fly؛ انظر القيد المعروف أدناه)

🌐 اللغات: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | **العربية** | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## تخطيط الوحدات

```
codex-gradle/
├── build.gradle.kts                 # عنصر الجذر النائب (إضافة readme معطّلة)
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # كتالوج الجذر (إصدارات الإضافات)
└── codex-plugin/
    ├── build.gradle.kts              # وحدة الإضافة (النشر، التوقيع، kover)
    ├── gradle/libs.versions.toml     # كتالوج الوحدة (جميع إصدارات الاعتماديات)
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # نقطة دخول الإضافة — تسجل 12 مهمة
        ├── CodexExtension.kt         # توسعة `codex { ... }` (zone + pgvector)
        ├── Metadata.kt               # تنسيق البيانات الوصفية المحورية لمساحة العمل
        ├── LicenseZoneDetector.kt    # كشف تلقائي للمنطقة OSS/CSS/UNKNOWN عند التحميل
        ├── ocr/                      # عقود OCR (OcrConfig, OcrRequest, OcrResult, engines)
        ├── store/                    # CodexVectorStore — عميل R2DBC pgvector
        └── tasks/                    # 12 تنفيذًا للمهام (+ DocNode, FontStyle)
```

## المهام المسجلة

12 مهمة ضمن 4 مجموعات تصنيف (راجع `CodexPlugin.kt`):

| المجموعة | المهام |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## عقود N0 (من workspace-bom MEMPHIS)

يستورد Codex `education.cccp:workspace-bom:0.0.1` كـ BOM منصة ويستهلك مباشرة:

| العقد | الأداة | يوفّر |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

عقود N0 أخرى متاحة عبر BOM (ليس كلها مستوردة مباشرة بواسطة codex):
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`.

## الاعتماديات الرئيسية

- **koog-agents** 1.0.0 — منسّق وكيلي (DSL Kotlin StateGraph)
- **langchain4j** 1.14.1 — مزوّدو LLM، RAG، التضمينات
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — تضمينات جمل ONNX
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — pgvector تفاعلي
- **kotlinx-coroutines** 1.9.0 (core + reactive) — جسر R2DBC
- **Apache PDFBox** 3.0.4 — استخراج نص/بنية PDF
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — كشف الصيغة
- **Flexmark** 0.64.8 (`flexmark-all`) — معالجة Markdown
- **AsciidoctorJ** 3.0.0 — تحليل/عرض AsciiDoc
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — تسلسل JSON/YAML
- **kotlinx-serialization-json** 1.7.3

يفرض `buildscript` قيمة `org.jetbrains:annotations:26.0.2-1` لتجاوز التثبيت
الصارم لـ Gradle (`{strictly 13.0}`) الذي قد يكسر اعتماديات koog/flexmark
التبعية.

## مصفوفة الاختبار

تعيش المصادر تحت `codex-plugin/src/test/kotlin/`:

| النطاق | الموقع | العدد |
|------|----------|-------|
| وحدوي (JUnit5) | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 فئة spec |
| BDD (Cucumber) | `codex/bdd/PipelineSteps.kt` + 5 ملفات `.feature` | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| تكامل | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

تستخدم جميع الاختبارات `useJUnitPlatform()`؛ يُصدر تسجيل الاختبار أحداث
`FAILED` + `SKIPPED`. يوفّر Testcontainers حاويات بأسلوب
`pgvector/pgvector:pg17` عبر `testcontainers-postgresql` 1.21.4 و`docker-java`
3.7.0 (نقل httpclient5).

لا توجد مهمة تقسيم `testFast`/`testAll`/`testEpics` ولا بوابة `koverVerify`
معرفة هنا — مهمة `:codex-plugin:check` الوحيدة تشغّل مجموعة JUnit5 + Cucumber
الكاملة. يُصدر Kover تقارير XML + HTML لكنه غير موصول بـ `check`
(`onCheck = false`).

### قيد التغطية المعروف

لا يستطيع وكيل on-fly في Kover 0.9.8 تجهيز الفئات التي يحمّلها Gradle
TestKit (`ProjectBuilder` يشغّل الإضافة في محمّل فئات منفصل). اختبارات
`CodexPlugin`, `CodexExtension` إلخ تنجح لكن تغطيتها تُREPORT على أنها 0 %.
مسار الترقية: تجهيز offline لـ Kover 1.x (لم يُصدر بعد).

## ضبط JVM

- **Java**: 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin**: `jvmTarget = JVM_24`, `-Xjsr305=strict`
- الكومة: اضبط عبر `GRADLE_OPTS="-Xmx2g"` للتشغيلات الثقيلة الاقتحام

## أوامر البناء

```bash
./gradlew build -x test                  # ترجمة فقط
./gradlew :codex-plugin:check           # اختبارات كاملة (JUnit5 + Cucumber)
./gradlew :codex-plugin:test            # اختبارات وحدوية JUnit5 فقط
./gradlew koverHtmlReport               # تقرير HTML للتغطية (يدوي)
./gradlew publishToMavenLocal           # نشر محلي
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## خط أنابيب CI

يُعرّف `.github/workflows/test.yml` وظيفة **Build & Test** واحدة
(`ubuntu-latest`, مهلة 15 دقيقة):

1. Checkout (`actions/checkout@v4`)
2. إعداد JDK 24 Temurin (`actions/setup-java@v4`)
3. إعداد Gradle (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

لا توجد وظيفة نشر-عند-الوسم في هذا المسار؛ نشر Maven Central مدفوع
بواسطة مهمة `publishAggregationToCentralPortal` المستدعاة يدويًا / في
مسار نشر منفصل (راجع قسم NMCP في `AGENTS.adoc`).

## النشر (NMCP)

مُعدّ في `codex-plugin/build.gradle.kts`:

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- مستودع `mavenCentral()` (وليس staging القديم من Sonatype)
- `signing { useGpgCmd() }` — يُتخطّى عندما `CI == "true"` أو ينتهي الإصدار بـ `-SNAPSHOT`
- POM مُعلَن على **جميع** `withType<MavenPublication>` (وليس فقط `pluginMaven`):
  رخصة Apache 2.0، مطوّر `cccp-education`، SCM → `github.com/cccp-education/codex`
- JARs للمصادر + javadoc (`withSourcesJar()`, `withJavadocJar()`)
- كتلة `<relocation>` اختيارية مُفعّلة بـ `-Prem relocationGroup=<namespace>`
  (مُعدّة لترحيل `groupId` مستقبلاً؛ حاليًا خاملة)

ملاحظة: لا يتضمّن `settings.gradle.kts` هنا إضافة إعدادات `nmcp`؛ هذا
مستودع codex-gradle ينشر `codex-plugin` مباشرة عبر `maven-publish` +
مسار مهمة `publishAggregationToCentralPortal` الموثّق في `AGENTS.adoc`.

## مثيلات Ollama (قيد شامل)

المنافذ `11434–11436` **محرّمة**. التدوير على `11437–11465` (29 منفذًا).
النماذج المصرّح بها: `gpt-oss:120b-cloud`, `gemma4:31b-cloud`.

## المساهمة

1. يترجم البناء: `./gradlew build -x test`
2. الاختبارات خضراء: `./gradlew :codex-plugin:check`
3. لا تراجع في CVE: أبقِ تثبيت `org.jetbrains:annotations` على `26.0.2-1`
4. اتبع اصطلاحات DDD (value objects, ports/adapters, لا تسريبات)
5. احترم الحد: codex = **READ + RAG**؛ لا تضف منطق WRITE/PUBLISH
   (ذلك يعود لـ `document-gradle`)

## وثائق البنية

- [AGENT.adoc](../AGENT.adoc) — قواعد مطلقة (الالتزامات، الأسرار، التصنيف)
- [BACKLOG.adoc](../BACKLOG.adoc) — backlog نشر EPIC PUB
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — حد Codex↔Document
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — تصنيف موحّد للمهام
- `gradle/libs.versions.toml` (كتالوج الوحدة) — إصدارات الاعتماديات المرجعية

## الترخيص

Apache License 2.0 — راجع [LICENSE](../LICENSE).

---

_جزء من منظومة CCCP Education — `groupId: education.cccp`._