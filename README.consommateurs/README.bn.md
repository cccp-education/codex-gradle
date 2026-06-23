<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — ভোক্তা গাইড

> নথি-অধিগ্রহণ Gradle প্লাগইন: PDF/EPUB → নিষ্কর্ষ → খণ্ডায়ন → pgvector RAG।

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **সংস্করণ**: `0.0.1` · **গোষ্ঠী**: `education.cccp` · **প্লাগইন ID**: `education.cccp.codex`
- **বিল্ড**: `./gradlew build` · **পরীক্ষা**: `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **আপস্ট্রিম নির্ভরতা**: [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` এর জন্য একমাত্র সত্য উৎস হিসেবে গৃহীত।

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | **বাংলা** | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## এটি কী করে

`codex-gradle` অসংগঠিত নথি (PDF, EPUB) অধিগ্রহণ করে, সংগঠিত পাঠ্য ও বইয়ের শ্রেণিবিন্যাস নিষ্কর্ষ করে, Markdown/AsciiDoc-এ রূপান্তর করে, অর্থখণ্ডায়ন করে, ONNX (`all-MiniLM-L6-v2`) দিয়ে ভেক্টর বানায়, এম্বেডিংস **PostgreSQL + pgvector**-এ **R2DBC** দ্বারা সঞ্চয় করে, এবং কোসাইন-সাদৃশ্য পুনরুদ্ধার প্রদান করে যাতে CCCP Education **RAG** স্তর ও **জ্ঞান গ্রাফ** খাওয়ানো যায়।

এটি CCCP Education ইকোসিস্টেমে [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle)-এর আপস্ট্রিমে অবস্থিত:

```
document corpus → codex-gradle (READ + RAG) → [codebase-gradle] → koog agents → output
```

সীমানা (`.agents/ARCHITECTURE_BOUNDARY.adoc`-এ নথিভুক্ত): **codex = READ +
RAG**; ভ্রাতৃপ্লাগইন `document-gradle` **WRITE + PUBLISH** পরিচালনা করে। দুটি N2 প্লাগইন সমান্তরাল, স্তূপীয় নয়।

## দ্রুত শুরু

### 1. প্লাগইন প্রয়োগ করুন

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. pgvector সংযোগ কনফিগার করুন (ঐচ্ছিক — ডিফল্ট মান দেখানো)

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. কর্পাস অধিগ্রহণ ও ইনজেস্ট করুন

```bash
./gradlew transformCorpusToPdf          # স্বয়ং-শনাক্ত PDF/EPUB → নিষ্কর্ষ → খণ্ড → JSON
./gradlew collectIngest                 # খণ্ড ভেক্টরাইজ → pgvector (ব্যাচ আকার 32)
./gradlew collectRetrieve --query="..." # কোসাইন সাদৃশ্য top-K
```

## উপলব্ধ কার্যসমূহ

| কার্য | গোষ্ঠী | বিবরণ |
|------|-------|-------------|
| `collectText`              | collect  | PDF থেকে কাঁচা পাঠ্য ও টাইপোগ্রাফিক মেটাডেটা নিষ্কর্ষ |
| `collectBookStructure`     | collect  | PDF কাঠামো নিষ্কর্ষ (শিরোনাম/বিভাগ) → শ্রেণিবদ্ধ `.adoc` |
| `collectEpubStructure`      | collect  | EPUB কাঠামো নিষ্কর্ষ (XHTML → `.adoc` + কোড ব্লক) |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | ONNX ভেক্টরাইজেশন → R2DBC দ্বারা pgvector (ডিফল্ট ব্যাচ 32) |
| `collectRetrieve`          | collect  | pgvector-এ কোসাইন সাদৃশ্য অর্থ-অনুসন্ধান (ডিফল্ট top-K 10) |
| `transformToJsonLdd`       | transform | AsciidoctorJ দ্বারা `.adoc` পার্স → JSON LDD |
| `transformToMarkdown`      | transform | AsciiDoc কাঠামো → Markdown (শ্রেণিবিন্যাস + কোড ব্লক সংরক্ষিত) |
| `transformChunk`           | transform | অর্থ অনুচ্ছেদ খণ্ডায়ন (প্রতি শিরোনামে ১ খণ্ড) |
| `transformCorpusToPdf`     | transform | যৌথ পাইপলাইন (স্বয়ং-শনাক্ত PDF/EPUB): নিষ্কর্ষ → Markdown → খণ্ড → JSON |
| `generateCompositeContext` | generate | `CodexVectorStore` দ্বারা অর্থ-অনুসন্ধান → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | খণ্ড সমষ্টি → বহু-বিন্যাস KB (JSON-L, Markdown, AsciiDoc) |

কার্যগুলি একত্রিত ওয়ার্কস্পেস শ্রেণিবিন্যাস অনুসরণ করে (৪টি ক্রিয়া: GENERATE / COLLECT / TRANSFORM / DEPLOY) — দেখুন `TAXONOMIE_WORKSPACE.adoc`।

## এক্সটেনশন DSL

```gradle
codex {
    // লাইসেন্স জোন (LicenseZoneDetector দ্বারা স্বয়ং-শনাক্ত; OSS/CSS/UNKNOWN)
    zone = codex.LicenseZone.OSS

    // pgvector সংযোগ পরামিতি (সবই দেখানো মানে ডিফল্ট)
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

`generateCompositeContext` কার্যটি এই Gradle বৈশিষ্ট্যগুলিও পড়ে:

| বৈশিষ্ট্য | ডিফল্ট | উদ্দেশ্য |
|----------|---------|---------|
| `query`  | `"architecture du workspace"` | অর্থ-অনুসন্ধান ক্যোয়ারী |
| `topK`   | `"10"`                         | নিকটতম প্রতিবেশীর সংখ্যা |

উদাহরণ:

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json (runner-সামঞ্জস্যপূর্ণ)
```

## পূর্বশর্ত

- **Java** 24 (Kotlin 2.3.20 টুলচেইন, `jvmTarget = 24`)
- **Gradle** 9.5.1
- **PostgreSQL** 15+ `pgvector` এক্সটেনশন সহ (`collectIngest`/`collectRetrieve`-র জন্য)
- **Docker** (Testcontainers-সমর্থিত ইন্টিগ্রেশন পরীক্ষার জন্য)

## বিল্ড ও পরীক্ষা

```bash
./gradlew build                            # সম্পূর্ণ বিল্ড (সংকলন + পরীক্ষা)
./gradlew build -x test                    # শুধু সংকলন
./gradlew :codex-plugin:check              # পরীক্ষা (JUnit5 + Cucumber BDD)
./gradlew publishToMavenLocal              # স্থানীয় প্রকাশনা
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## সমস্যা সমাধান

| লক্ষণ | সমাধান |
|---------|-----|
| `org.jetbrains:annotations` সংস্করণ দ্বন্দ্ব (Gradle পিন 13.0) | `buildscript`-এ ইতিমধ্যে `26.0.2-1`-এ বাধ্য; `--refresh-dependencies` দিয়ে পুনরায় চালান |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Postgres Testcontainer আটকে আছে | `docker rm -f $(docker ps -aq --filter name=postgres)` তারপর পুনরায় চেষ্টা করুন |
| Kover প্লাগইন ক্লাসে 0% রিপোর্ট করে | পরিচিত সীমাবদ্ধতা: Kover 0.9.8 on-fly এজেন্ট Gradle TestKit `ProjectBuilder` ক্লাসলোডার বাধা দিতে পারে না। `build.gradle.kts`-এ নোট দেখুন। |

## লাইসেন্স

Apache License 2.0 — দেখুন [LICENSE](../LICENSE)।

---

_CCCP Education ইকোসিস্টেমের অংশ — `groupId: education.cccp`।_