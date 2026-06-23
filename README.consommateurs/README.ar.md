<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — دليل المستهلك

> إضافة Gradle لاقتناء المستندات: PDF/EPUB → استخراج → تقسيم → pgvector RAG.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **الإصدار**: `0.0.1` · **المجموعة**: `education.cccp` · **معرف الإضافة**: `education.cccp.codex`
- **البناء**: `./gradlew build` · **الاختبارات**: `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **الاعتمادية المنبعية**: [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — تُستهلك كمصدر وحيد للحقيقة لـ `ContextChannel`، `ChannelBudget`، `CompositeContext`، `CompositeContextConfig`.

🌐 اللغات: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | **العربية** | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## ما الذي يقوم به

يقوم `codex-gradle` باقتناء المستندات غير المهيكلة (PDF، EPUB)، واستخراج النص
المهيكل والتسلسل الهرمي للكتاب، والتحويل إلى Markdown/AsciiDoc، وإجراء التقسيم
الدلالي، والتوجيه المتجهي باستخدام ONNX (`all-MiniLM-L6-v2`)، وتخزين
التضمينات في **PostgreSQL + pgvector** عبر **R2DBC**، ويعرض استرجاعًا عبر
تشابه جيب التمام لتغذية طبقة **RAG** و**رسم المعرفة** في CCCP Education.

يقع هذا upstream من [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle)
في منظومة CCCP Education:

```
document corpus → codex-gradle (READ + RAG) → [codebase-gradle] → koog agents → output
```

الحدود (موثقة في `.agents/ARCHITECTURE_BOUNDARY.adoc`): **codex = READ +
RAG**؛ أما الإضافة الشقيقة `document-gradle` فتتعامل مع **WRITE + PUBLISH**.
الإضافتان N2 متوازيتان وليستا متراكبتين.

## البداية السريعة

### 1. تطبيق الإضافة

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. تهيئة اتصال pgvector (اختياري — القيم الافتراضية معروضة)

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. اقتناء واقتحان مجموعة المستندات

```bash
./gradlew transformCorpusToPdf          # كشف تلقائي PDF/EPUB → استخراج → تقسيم → JSON
./gradlew collectIngest                 # توجيه متجهي للأجزاء → pgvector (حجم الدفعة 32)
./gradlew collectRetrieve --query="..." # تشابه جيب التمام top-K
```

## المهام المتاحة

| المهمة | المجموعة | الوصف |
|------|-------|-------------|
| `collectText`              | collect  | استخراج النص الخام من PDF مع البيانات الوصفية الطباعية |
| `collectBookStructure`     | collect  | استخراج بنية PDF (عناوين/أقسام) → `.adoc` هرمي |
| `collectEpubStructure`      | collect  | استخراج بنية EPUB (XHTML → `.adoc` + كتل التعليمات البرمجية) |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | توجيه متجهي ONNX → pgvector عبر R2DBC (دفعة افتراضية 32) |
| `collectRetrieve`          | collect  | بحث دلالي بتشابه جيب التمام في pgvector (top-K افتراضي 10) |
| `transformToJsonLdd`       | transform | تحليل `.adoc` عبر AsciidoctorJ → JSON LDD |
| `transformToMarkdown`      | transform | بنية AsciiDoc → Markdown (الحفاظ على التسلسل الهرمي وكتل التعليمات البرمجية) |
| `transformChunk`           | transform | تقسيم دلالي حسب الأقسام (جزء واحد لكل عنوان) |
| `transformCorpusToPdf`     | transform | خط معالجة مركب (كشف تلقائي PDF/EPUB): استخراج → Markdown → تقسيم → JSON |
| `generateCompositeContext` | generate | بحث دلالي عبر `CodexVectorStore` → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | تجميع الأجزاء → قاعدة معرفة متعددة الصيغ (JSON-L، Markdown، AsciiDoc) |

تتبع المهام التصنيف الموحد لمساحة العمل (4 أفعال: GENERATE / COLLECT /
TRANSFORM / DEPLOY) — راجع `TAXONOMIE_WORKSPACE.adoc`.

## DSL التوسعة

```gradle
codex {
    // منطقة الترخيص (كشف تلقائي عبر LicenseZoneDetector؛ OSS/CSS/UNKNOWN)
    zone = codex.LicenseZone.OSS

    // معاملات اتصال pgvector (جميعها افتراضية للقيم المعروضة)
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

تقرأ مهمة `generateCompositeContext` أيضًا خصائص Gradle التالية:

| الخاصية | الافتراضي | الغرض |
|----------|---------|---------|
| `query`  | `"architecture du workspace"` | استعلام البحث الدلالي |
| `topK`   | `"10"`                         | عدد أقرب الجيران |

مثال:

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json (متوافق مع runner)
```

## المتطلبات المسبقة

- **Java** 24 (سلسلة أدوات Kotlin 2.3.20، `jvmTarget = 24`)
- **Gradle** 9.5.1
- **PostgreSQL** 15+ مع امتداد `pgvector` (لـ `collectIngest`/`collectRetrieve`)
- **Docker** (لاختبارات التكامل المدعومة بـ Testcontainers)

## البناء والاختبار

```bash
./gradlew build                            # بناء كامل (ترجمة + اختبارات)
./gradlew build -x test                    # ترجمة فقط
./gradlew :codex-plugin:check              # الاختبارات (JUnit5 + Cucumber BDD)
./gradlew publishToMavenLocal              # نشر محلي
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## استكشاف الأخطاء وإصلاحها

| العَرَض | الإصلاح |
|---------|-----|
| تعارض إصدار `org.jetbrains:annotations` (Gradle يثبّت 13.0) | تم فرضه على `26.0.2-1` في `buildscript`؛ أعد التشغيل مع `--refresh-dependencies` |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Postgres Testcontainer عالق | `docker rm -f $(docker ps -aq --filter name=postgres)` ثم أعد المحاولة |
| Kover يُبلغ عن 0% على فئات الإضافة | قيد معروف: لا يستطيع وكيل on-fly في Kover 0.9.8 اعتراض مُحمّل فئات `ProjectBuilder` في Gradle TestKit. راجع الملاحظة في `build.gradle.kts`. |

## الترخيص

Apache License 2.0 — راجع [LICENSE](../LICENSE).

---

_جزء من منظومة CCCP Education — `groupId: education.cccp`._