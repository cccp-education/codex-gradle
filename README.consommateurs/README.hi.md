<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — उपभोक्ता गाइड

> दस्तावेज़-अधिग्रहण Gradle प्लगइन: PDF/EPUB → निष्कर्ष → खंडन → pgvector RAG।

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **संस्करण**: `0.0.1` · **समूह**: `education.cccp` · **प्लगइन ID**: `education.cccp.codex`
- **बिल्ड**: `./gradlew build` · **परीक्षण**: `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **अपस्ट्रीम निर्भरता**: [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` के लिए एकमात्र सत्य स्रोत के रूप में उपभुक्त।

🌐 Languages: [English](README.md) | [中文](README.zh.md) | **हिन्दी** | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## यह क्या करता है

`codex-gradle` असंरचित दस्तावेज़ों (PDF, EPUB) को अधिग्रहित करता है, संरचित पाठ और पुस्तक पदानुक्रम निकालता है, Markdown/AsciiDoc में रूपांतरित करता है, अर्थखंडन करता है, ONNX (`all-MiniLM-L6-v2`) से वेक्टर बनाता है, एम्बेडिंग्स को **PostgreSQL + pgvector** में **R2DBC** द्वारा भंडारित करता है, और कोसाइन-समानता पुनःप्राप्ति प्रदान करता है ताकि CCCP Education **RAG** परत और **ज्ञान ग्राफ** को खिलाया जा सके।

यह CCCP Education पारिस्थितिकी में [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle) के अपस्ट्रीम स्थित है:

```
document corpus → codex-gradle (READ + RAG) → [codebase-gradle] → koog agents → output
```

सीमा (`.agents/ARCHITECTURE_BOUNDARY.adoc` में दस्तावेज़ित): **codex = READ + RAG**; सहपाठी `document-gradle` प्लगइन **WRITE + PUBLISH** को संभालता है। दोनों N2 प्लगइन समानांतर हैं, स्टैक नहीं।

## त्वरित प्रारंभ

### 1. प्लगइन लागू करें

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. pgvector कनेक्शन कॉन्फ़िगर करें (वैकल्पिक — डिफ़ॉल्ट दिखाए गए)

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. कॉर्पस अधिग्रहित करें और इंजेस्ट करें

```bash
./gradlew transformCorpusToPdf          # पीडीएफ/EPUB स्वतः-पहचान → निष्कर्ष → खंड → JSON
./gradlew collectIngest                 # खंड वेक्टर → pgvector (बैच आकार 32)
./gradlew collectRetrieve --query="..." # कोसाइन समानता top-K
```

## उपलब्ध कार्य

| कार्य | समूह | विवरण |
|------|-------|-------------|
| `collectText`              | collect  | PDF से कच्चा पाठ और टाइपोग्राफिक मेटाडेटा निष्कर्ष |
| `collectBookStructure`     | collect  | PDF संरचना निष्कर्ष (शीर्षक/अनुभाग) → पदानुक्रमित `.adoc` |
| `collectEpubStructure`      | collect  | EPUB संरचना निष्कर्ष (XHTML → `.adoc` + कोड ब्लॉक) |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | ONNX वेक्टर → R2DBC द्वारा pgvector (डिफ़ॉल्ट बैच 32) |
| `collectRetrieve`          | collect  | pgvector में कोसाइन समानता अर्थखोज (डिफ़ॉल्ट top-K 10) |
| `transformToJsonLdd`       | transform | AsciidoctorJ द्वारा `.adoc` पार्स → JSON LDD |
| `transformToMarkdown`      | transform | AsciiDoc संरचना → Markdown (पदानुक्रम + कोड ब्लॉक संरक्षित) |
| `transformChunk`           | transform | अर्थखंड खंडन (प्रति शीर्षक 1 खंड) |
| `transformCorpusToPdf`     | transform | समग्र पाइपलाइन (स्वतः-पहचान PDF/EPUB): निष्कर्ष → Markdown → खंड → JSON |
| `generateCompositeContext` | generate | `CodexVectorStore` द्वारा अर्थखोज → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | खंड समग्रीकरण → बहु-प्रारूप KB (JSON-L, Markdown, AsciiDoc) |

कार्य एकीकृत कार्यक्षेत्र वर्गीकरण (4 क्रिया: GENERATE / COLLECT / TRANSFORM / DEPLOY) का अनुसरण करते हैं — देखें `TAXONOMIE_WORKSPACE.adoc`।

## एक्सटेंशन DSL

```gradle
codex {
    // लाइसेंस ज़ोन (LicenseZoneDetector द्वारा स्वतः-पहचान; OSS/CSS/UNKNOWN)
    zone = codex.LicenseZone.OSS

    // pgvector कनेक्शन पैरामीटर (सभी डिफ़ॉल्ट दिखाए गए मान)
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

`generateCompositeContext` कार्य ये Gradle गुण भी पढ़ता है:

| गुण | डिफ़ॉल्ट | उद्देश्य |
|----------|---------|---------|
| `query`  | `"architecture du workspace"` | अर्थखोज प्रश्न |
| `topK`   | `"10"`                         | निकटतम पड़ोसियों की संख्या |

उदाहरण:

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json (runner-संगत)
```

## पूर्वापेक्षाएँ

- **Java** 24 (Kotlin 2.3.20 टूलचेन, `jvmTarget = 24`)
- **Gradle** 9.5.1
- **PostgreSQL** 15+ `pgvector` एक्सटेंशन सहित (`collectIngest`/`collectRetrieve` हेतु)
- **Docker** (Testcontainers-समर्थित एकीकरण परीक्षणों हेतु)

## बिल्ड व परीक्षण

```bash
./gradlew build                            # पूर्ण बिल्ड (संकलन + परीक्षण)
./gradlew build -x test                    # केवल संकलन
./gradlew :codex-plugin:check              # परीक्षण (JUnit5 + Cucumber BDD)
./gradlew publishToMavenLocal              # स्थानीय प्रकाशन
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## समस्या-निवारण

| लक्षण | समाधान |
|---------|-----|
| `org.jetbrains:annotations` संस्करण विरोध (Gradle पिन 13.0) | `buildscript` में पहले से `26.0.2-1` पर बाध्य; `--refresh-dependencies` से पुनः चलाएँ |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Postgres Testcontainer अटका | `docker rm -f $(docker ps -aq --filter name=postgres)` फिर पुनः प्रयास |
| Kover प्लगइन वर्गों पर 0% रिपोर्ट | ज्ञात सीमा: Kover 0.9.8 on-fly एजेंट Gradle TestKit `ProjectBuilder` क्लासलोडर को नहीं पकड़ पाता। `build.gradle.kts` में नोट देखें। |

## लाइसेंस

Apache License 2.0 — देखें [LICENSE](../LICENSE)।

---

_CCCP Education पारिस्थितिकी का भाग — `groupId: education.cccp`।_