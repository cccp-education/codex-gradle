<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — صارف کی رہنمائی

> دستاویز حاصل کرنے کا Gradle پلگ ان: PDF/EPUB → استخراج → ٹکڑے → pgvector RAG۔

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **ورژن**: `0.0.1` · **گروپ**: `education.cccp` · **پلگ ان ID**: `education.cccp.codex`
- **بلڈ**: `./gradlew build` · **ٹیسٹ**: `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **اپ اسٹریم انحصار**: [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — `ContextChannel`، `ChannelBudget`، `CompositeContext`، `CompositeContextConfig` کے لیے واحد حقائق کا منبع کے طور پر استعمال ہوتا ہے۔

🌐 زبانیں: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | **اردو**

---

## یہ کیا کرتا ہے

`codex-gradle` غیر منظم دستاویزات (PDF، EPUB) حاصل کرتا ہے، منظم متن اور کتاب کی درجہ بندی استخراج کرتا ہے، Markdown/AsciiDoc میں تبدیل کرتا ہے، معنوی ٹکڑائو کرتا ہے، ONNX (`all-MiniLM-L6-v2`) سے ویکٹرائز کرتا ہے، ایمبیڈنگز کو **R2DBC** پر **PostgreSQL + pgvector** میں محفوظ کرتا ہے، اور کوسائن مشابہت بازیافت فراہم کرتا ہے تاکہ CCCP Education کی **RAG** پرت اور **نقشہ معرفت** کو کھلایا جا سکے۔

یہ CCCP Education کے ماحولیاتی نظام میں [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle) کے اپ اسٹریم میں واقع ہے:

```
document corpus → codex-gradle (READ + RAG) → [codebase-gradle] → koog agents → output
```

حدود (`.agents/ARCHITECTURE_BOUNDARY.adoc` میں دستاویزی): **codex = READ +
RAG**؛ ہم پلگ ان `document-gradle` **WRITE + PUBLISH** سنبھالتا ہے۔ دونوں N2 پلگ ان متوازی ہیں، اسٹیک نہیں۔

## فوری آغاز

### 1. پلگ ان لگائیں

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. pgvector کنکشن تشکیل دیں (اختیاری — طے شدہ قدرات دکھائے گئے)

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. کورپس حاصل کریں اور انجسٹ کریں

```bash
./gradlew transformCorpusToPdf          # خودکار شناخت PDF/EPUB → استخراج → ٹکڑے → JSON
./gradlew collectIngest                 # ٹکڑوں کو ویکٹرائز → pgvector (بیچ سائز 32)
./gradlew collectRetrieve --query="..." # کوسائن مشابہت top-K
```

## دستیاب کام

| کام | گروپ | تفصیل |
|------|-------|-------------|
| `collectText`              | collect  | PDF سے کچا متن اور ٹائپوگرافک میٹاڈیٹا استخراج |
| `collectBookStructure`     | collect  | PDF ساخت استخراج (عنوانات/اقسام) → درجہ بند `.adoc` |
| `collectEpubStructure`      | collect  | EPUB ساخت استخراج (XHTML → `.adoc` + کوڈ بلاکس) |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | ONNX ویکٹرائزیشن → R2DBC کے ذریعے pgvector (طے شدہ بیچ 32) |
| `collectRetrieve`          | collect  | pgvector میں کوسائن مشابہت معنوی تلاش (طے شدہ top-K 10) |
| `transformToJsonLdd`       | transform | AsciidoctorJ کے ذریعے `.adoc` پارس → JSON LDD |
| `transformToMarkdown`      | transform | AsciiDoc ساخت → Markdown (درجہ بندی + کوڈ بلاکس محفوظ) |
| `transformChunk`           | transform | معنوی ٹکڑائو فی حصہ (فی عنوان 1 ٹکڑا) |
| `transformCorpusToPdf`     | transform | مرکب پائپ لائن (خودکار شناخت PDF/EPUB): استخراج → Markdown → ٹکڑے → JSON |
| `generateCompositeContext` | generate | `CodexVectorStore` کے ذریعے معنوی تلاش → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | ٹکڑوں کا اجتماع → کثیر شکلب KB (JSON-L، Markdown، AsciiDoc) |

کام متحدہ ورک اسپیس ٹیکسونومی (4 افعال: GENERATE / COLLECT / TRANSFORM / DEPLOY) پر عمل کرتے ہیں — دیکھیں `TAXONOMIE_WORKSPACE.adoc`۔

## توسیع DSL

```gradle
codex {
    // لائسنس زون (LicenseZoneDetector کے ذریعے خودکار شناخت؛ OSS/CSS/UNKNOWN)
    zone = codex.LicenseZone.OSS

    // pgvector کنکشن پیرامیٹرز (تمام طے شدہ قدرات دکھائے گئے)
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

`generateCompositeContext` کام یہ Gradle خصوصیات بھی پڑھتا ہے:

| خصوصیت | طے شدہ | مقصد |
|----------|---------|---------|
| `query`  | `"architecture du workspace"` | معنوی تلاش استفسار |
| `topK`   | `"10"`                         | قریب ترین پڑوسیوں کی تعداد |

مثال:

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json (runner-موافق)
```

## پیشگی ضروریات

- **Java** 24 (Kotlin 2.3.20 ٹول چین، `jvmTarget = 24`)
- **Gradle** 9.5.1
- **PostgreSQL** 15+ `pgvector` ایکسٹینشن کے ساتھ (`collectIngest`/`collectRetrieve` کے لیے)
- **Docker** (Testcontainers کے سہولت یافتہ انضمام ٹیسٹوں کے لیے)

## بلڈ اور ٹیسٹ

```bash
./gradlew build                            # مکمل بلڈ (کمپائل + ٹیسٹ)
./gradlew build -x test                    # صرف کمپائل
./gradlew :codex-plugin:check              # ٹیسٹ (JUnit5 + Cucumber BDD)
./gradlew publishToMavenLocal              # مقامی اشاعت
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## مسائل کا حل

| علامت | حل |
|---------|-----|
| `org.jetbrains:annotations` ورژن تنازع (Gradle پن 13.0) | `buildscript` میں پہلے ہی `26.0.2-1` پر مجبور ہے؛ `--refresh-dependencies` کے ساتھ دوبارہ چلائیں |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Postgres Testcontainer پھنسا | `docker rm -f $(docker ps -aq --filter name=postgres)` پھر دوبارہ کوشش کریں |
| Kover پلگ ان کلاسز پر 0% رپورٹ | معروف حد: Kover 0.9.8 on-fly ایجنٹ Gradle TestKit `ProjectBuilder` کلاس لوڈر کو روک نہیں سکتا۔ `build.gradle.kts` میں نوٹ دیکھیں۔ |

## لائسنس

Apache License 2.0 — دیکھیں [LICENSE](../LICENSE)۔

---

_CCCP Education ماحولیاتی نظام کا حصہ — `groupId: education.cccp`۔_