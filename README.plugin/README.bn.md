<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — প্লাগইনের অভ্যন্তরীণ বিষয়

> `codex-plugin` Gradle প্লাগইনের জন্য ডেভেলপার ও অবদানকারী গাইড।

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **সংস্করণ**: `0.0.1` · **গোষ্ঠী**: `education.cccp` · **প্লাগইন ID**: `education.cccp.codex`
- **টুলচেইন**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **বিল্ড**: `./gradlew build -x test` · **পরীক্ষা**: `./gradlew :codex-plugin:check` · **কভারেজ**: Kover 0.9.8 (on-fly; নিচে পরিচিত সীমাবদ্ধতা দেখুন)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | **বাংলা** | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## মডিউল বিন্যাস

```
codex-gradle/
├── build.gradle.kts                 # রুট প্লেসহোল্ডার (readme প্লাগইন নিষ্ক্রিয়)
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # রুট ক্যাটালগ (প্লাগইন সংস্করণ)
└── codex-plugin/
    ├── build.gradle.kts              # প্লাগইন মডিউল (প্রকাশনা, সাইনিং, kover)
    ├── gradle/libs.versions.toml     # মডিউল ক্যাটালগ (সব নির্ভরতা সংস্করণ)
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # প্লাগইন প্রবেশ বিন্দু — ১২টি কার্য নিবন্ধন
        ├── CodexExtension.kt         # `codex { ... }` এক্সটেনশন (zone + pgvector)
        ├── Metadata.kt               # ওয়ার্কস্পেস পিভট মেটাডেটা বিন্যাস
        ├── LicenseZoneDetector.kt    # লোডে স্বয়ং-শনাক্ত OSS/CSS/UNKNOWN জোন
        ├── ocr/                      # OCR চুক্তি (OcrConfig, OcrRequest, OcrResult, engines)
        ├── store/                    # CodexVectorStore — R2DBC pgvector ক্লায়েন্ট
        └── tasks/                    # ১২টি কার্য বাস্তবায়ন (+ DocNode, FontStyle)
```

## নিবন্ধিত কার্যসমূহ

৪টি শ্রেণিবিন্যাস গোষ্ঠীতে ১২টি কার্য (`CodexPlugin.kt` দেখুন):

| গোষ্ঠী | কার্য |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## N0 চুক্তি (workspace-bom MEMPHIS থেকে)

Codex `education.cccp:workspace-bom:0.0.1` কে প্ল্যাটফর্ম BOM হিসেবে আমদানি করে ও সরাসরি গ্রহণ করে:

| চুক্তি | আর্টিফ্যাক্ট | প্রদান করে |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

BOM-এর মাধ্যমে অন্যান্য N0 চুক্তি উপলব্ধ (সবই codex দ্বারা সরাসরি আমদানিকৃত নয়):
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`।

## মূল নির্ভরতাসমূহ

- **koog-agents** 1.0.0 — এজেন্টিক অর্কেস্ট্রেটর (DSL Kotlin StateGraph)
- **langchain4j** 1.14.1 — LLM প্রদানকারী, RAG, এম্বেডিং
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — ONNX বাক্য এম্বেডিং
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — রিয়েক্টিভ pgvector
- **kotlinx-coroutines** 1.9.0 (core + reactive) — R2DBC সেতু
- **Apache PDFBox** 3.0.4 — PDF পাঠ্য/কাঠামো নিষ্কর্ষ
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — বিন্যাস শনাক্তকরণ
- **Flexmark** 0.64.8 (`flexmark-all`) — Markdown প্রক্রিয়াকরণ
- **AsciidoctorJ** 3.0.0 — AsciiDoc পার্স/রেন্ডার
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — JSON/YAML ধারাবাহিকীকরণ
- **kotlinx-serialization-json** 1.7.3

`buildscript` `org.jetbrains:annotations:26.0.2-1` কে Gradle-এর কঠোর পিন
(`{strictly 13.0}`) অগ্রাহ্য করতে বাধ্য করে, অন্যথায় koog/flexmark ট্রানজিটিভ ভেঙে যেত।

## পরীক্ষা ম্যাট্রিক্স

উৎস `codex-plugin/src/test/kotlin/`-এর অধীনে:

| পরিসর | অবস্থান | গণনা |
|------|----------|-------|
| ইউনিট (JUnit5) | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 spec ক্লাস |
| BDD (Cucumber) | `codex/bdd/PipelineSteps.kt` + 5টি `.feature` ফাইল | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| ইন্টিগ্রেশন | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

সব পরীক্ষা `useJUnitPlatform()` ব্যবহার করে; পরীক্ষা লগ `FAILED` + `SKIPPED` ঘটনা নির্গমন করে।
Testcontainers `testcontainers-postgresql` 1.21.4 ও `docker-java` 3.7.0
(httpclient5 ট্রান্সপোর্ট) দ্বারা `pgvector/pgvector:pg17`-শৈলীর কন্টেইনার সরবরাহ করে।

এখানে কোনো `testFast`/`testAll`/`testEpics` বিভাজন কার্য নেই এবং কোনো
`koverVerify` গেটও সংজ্ঞায়িত নয় — একমাত্র `:codex-plugin:check` কার্য সম্পূর্ণ
JUnit5 + Cucumber স্যুট চালায়। Kover XML + HTML রিপোর্ট নির্গমন করে কিন্তু
`check`-এ যুক্ত নয় (`onCheck = false`)।

### পরিচিত কভারেজ সীমাবদ্ধতা

Kover 0.9.8 on-fly এজেন্ট Gradle TestKit দ্বারা লোডকৃত ক্লাস ইনস্ট্রুমেন্ট করতে
পারে না (`ProjectBuilder` প্লাগইনকে আলাদা ক্লাসলোডারে চালায়)।
`CodexPlugin`, `CodexExtension` ইত্যাদির পরীক্ষা পাস করে কিন্তু তাদের কভারেজ 0%
রিপোর্ট হয়। আপগ্রেড পথ: Kover 1.x অফলাইন ইনস্ট্রুমেন্টেশন (এখনো প্রকাশিত নয়)।

## JVM টিউনিং

- **Java**: 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin**: `jvmTarget = JVM_24`, `-Xjsr305=strict`
- হিপ: ইনজেস্ট-ভারী রানের জন্য `GRADLE_OPTS="-Xmx2g"` দ্বারা টিউন করুন

## বিল্ড কমান্ড

```bash
./gradlew build -x test                  # শুধু সংকলন
./gradlew :codex-plugin:check           # সম্পূর্ণ পরীক্ষা (JUnit5 + Cucumber)
./gradlew :codex-plugin:test            # শুধু JUnit5 ইউনিট পরীক্ষা
./gradlew koverHtmlReport               # কভারেজ HTML রিপোর্ট (ম্যানুয়াল)
./gradlew publishToMavenLocal           # স্থানীয় প্রকাশনা
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## CI পাইপলাইন

`.github/workflows/test.yml` একটি একক **Build & Test** কাজ নির্ধারণ করে
(`ubuntu-latest`, টাইমআউট ১৫ মিনিট):

1. Checkout (`actions/checkout@v4`)
2. JDK 24 Temurin সেটআপ (`actions/setup-java@v4`)
3. Gradle সেটআপ (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

এই ওয়ার্কফ্লোতে কোনো প্রকাশনা-ট্যাগে কাজ নেই; Maven Central প্রকাশনা
`publishAggregationToCentralPortal` কার্য দ্বারা ম্যানুয়াল/আলাদা প্রকাশনা প্রবাহে চালিত
(`AGENTS.adoc`-এ NMCP অংশ দেখুন)।

## প্রকাশনা (NMCP)

`codex-plugin/build.gradle.kts`-এ কনফিগার:

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- `mavenCentral()` রিপোজিটরি (ওয়ারিশ Sonatype staging নয়)
- `signing { useGpgCmd() }` — যখন `CI == "true"` বা সংস্করণ `-SNAPSHOT`-এ শেষ তখন বাদ দেওয়া হয়
- POM **সব** `withType<MavenPublication>`-এ ঘোষিত (শুধু `pluginMaven` নয়):
  Apache 2.0 লাইসেন্স, ডেভেলপার `cccp-education`, SCM → `github.com/cccp-education/codex`
- Sources + javadoc JARs (`withSourcesJar()`, `withJavadocJar()`)
- ঐচ্ছিক `<relocation>` ব্লক `-Prem relocationGroup=<namespace>` দ্বারা সক্রিয়
  (ভবিষ্যৎ `groupId` মাইগ্রেশনের জন্য প্রস্তুত; বর্তমানে নিষ্ক্রিয়)

নোট: এখানকার `settings.gradle.kts` `nmcp` সেটিংস প্লাগইন অন্তর্ভুক্ত করে না;
এই codex-gradle রিপোজিটরি `codex-plugin` কে সরাসরি `maven-publish` +
`AGENTS.adoc`-এ নথিভুক্ত `publishAggregationToCentralPortal` কার্য প্রবাহের মাধ্যমে প্রকাশ করে।

## Ollama ইনস্ট্যন্স (বৈশ্বিক বাধ্যবাধকতা)

পোর্ট `11434–11436` **নিষিদ্ধ**। `11437–11465` (২৯টি পোর্ট) উপর রোটেশন করুন।
অনুমোদিত মডেল: `gpt-oss:120b-cloud`, `gemma4:31b-cloud`।

## অবদান

1. বিল্ড সংকলন: `./gradlew build -x test`
2. পরীক্ষা সবুজ: `./gradlew :codex-plugin:check`
3. কোনো CVE প্রতিগমন নয়: `org.jetbrains:annotations` ফোর্স-পিন `26.0.2-1` রাখুন
4. DDD রীতিনীতি অনুসরণ করুন (value objects, ports/adapters, কোনো লিক নয়)
5. সীমানা সম্মান করুন: codex = **READ + RAG**; WRITE/PUBLISH যুক্তি যোগ করবেন না
   (সেটি `document-gradle`-এর দায়িত্ব)

## আর্কিটেকচার দলিল

- [AGENT.adoc](../AGENT.adoc) — নিত্যন্ত নিয়ম (কমিট, রহস্য, শ্রেণিবিন্যাস)
- [BACKLOG.adoc](../BACKLOG.adoc) — EPIC PUB প্রকাশনা ব্যাকলগ
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — Codex↔Document সীমানা
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — একত্রিত কার্য শ্রেণিবিন্যাস
- `gradle/libs.versions.toml` (মডিউল ক্যাটালগ) — ক্যানোনিক্যাল নির্ভরতা সংস্করণ

## লাইসেন্স

Apache License 2.0 — দেখুন [LICENSE](../LICENSE)।

---

_CCCP Education ইকোসিস্টেমের অংশ — `groupId: education.cccp`।_