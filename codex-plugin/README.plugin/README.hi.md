<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — प्लगइन आंतरिक तंत्र

> `codex-plugin` Gradle प्लगइन के लिए डेवलपर व योगदानकर्ता गाइड।

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **संस्करण**: `0.0.1` · **समूह**: `education.cccp` · **प्लगइन ID**: `education.cccp.codex`
- **टूलचेन**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **बिल्ड**: `./gradlew build -x test` · **परीक्षण**: `./gradlew :codex-plugin:check` · **कवरेज**: Kover 0.9.8 (on-fly; नीचे ज्ञात सीमा देखें)

🌐 Languages: [English](README.md) | [中文](README.zh.md) | **हिन्दी** | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## मॉड्यूल अभिन्यास

```
codex-gradle/
├── build.gradle.kts                 # रूट प्लेसहोल्डर (readme प्लगइन अक्षम)
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # रूट कैटलॉग (प्लगइन संस्करण)
└── codex-plugin/
    ├── build.gradle.kts              # प्लगइन मॉड्यूल (प्रकाशन, साइनिंग, kover)
    ├── gradle/libs.versions.toml     # मॉड्यूल कैटलॉग (सभी निर्भरता संस्करण)
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # प्लगइन प्रवेश बिंदु — 12 कार्य पंजीकृत करता है
        ├── CodexExtension.kt         # `codex { ... }` एक्सटेंशन (zone + pgvector)
        ├── Metadata.kt               # वर्कस्पेस पिवट मेटाडेटा प्रारूप
        ├── LicenseZoneDetector.kt    # लोड पर स्वतः-पहचान OSS/CSS/UNKNOWN ज़ोन
        ├── ocr/                      # OCR अनुबंध (OcrConfig, OcrRequest, OcrResult, engines)
        ├── store/                    # CodexVectorStore — R2DBC pgvector क्लाइंट
        └── tasks/                    # 12 कार्य कार्यान्वयन (+ DocNode, FontStyle)
```

## पंजीकृत कार्य

4 वर्गीकरण समूहों में 12 कार्य (`CodexPlugin.kt` देखें):

| समूह | कार्य |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## N0 अनुबंध (workspace-bom MEMPHIS से)

Codex `education.cccp:workspace-bom:0.0.1` को प्लेटफ़ॉर्म BOM के रूप में आयात करता है और सीधे उपभोग करता है:

| अनुबंध | आर्टिफ़ैक्ट | प्रदान करता है |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

BOM के माध्यम से अन्य N0 अनुबंध उपलब्ध (सभी codex द्वारा सीधे आयातित नहीं):
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`।

## प्रमुख निर्भरताएँ

- **koog-agents** 1.0.0 — अभिकर्ता ऑर्केस्ट्रेटर (DSL Kotlin StateGraph)
- **langchain4j** 1.14.1 — LLM प्रदाता, RAG, एम्बेडिंग्स
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — ONNX वाक्य एम्बेडिंग्स
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — रिएक्टिव pgvector
- **kotlinx-coroutines** 1.9.0 (core + reactive) — R2DBC ब्रिज
- **Apache PDFBox** 3.0.4 — PDF पाठ्य/संरचना निष्कर्ष
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — प्रारूप पहचान
- **Flexmark** 0.64.8 (`flexmark-all`) — Markdown प्रसंस्करण
- **AsciidoctorJ** 3.0.0 — AsciiDoc पार्स/रेंडर
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — JSON/YAML क्रमानुकूलन
- **kotlinx-serialization-json** 1.7.3

`buildscript` `org.jetbrains:annotations:26.0.2-1` को Gradle की सख्त पिन
(`{strictly 13.0}`) पर बाध्य करता है, अन्यथा koog/flexmark ट्रांज़िटिव्स टूट जाते।

## परीक्षण मैट्रिक्स

स्रोत `codex-plugin/src/test/kotlin/` के अंतर्गत:

| क्षेत्र | स्थान | गणना |
|------|----------|-------|
| यूनिट (JUnit5) | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 spec वर्ग |
| BDD (Cucumber) | `codex/bdd/PipelineSteps.kt` + 5 `.feature` फाइलें | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| एकीकरण | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

सभी परीक्षण `useJUnitPlatform()` का उपयोग करते हैं; परीक्षण लॉग `FAILED` + `SKIPPED` घटनाएँ उत्सर्जित करते हैं।
Testcontainers `testcontainers-postgresql` 1.21.4 और `docker-java` 3.7.0
(httpclient5 ट्रांसपोर्ट) के माध्यम से `pgvector/pgvector:pg17`-शैली कंटेनर प्रदान करता है।

यहाँ कोई `testFast`/`testAll`/`testEpics` विभाजन कार्य नहीं है और न ही कोई
`koverVerify` गेट परिभाषित है — एकमात्र `:codex-plugin:check` कार्य पूर्ण
JUnit5 + Cucumber सूट चलाता है। Kover XML + HTML रिपोर्ट उत्सर्जित करता है परंतु
`check` में नहीं जुड़ा (`onCheck = false`)।

### ज्ञात कवरेज सीमा

Kover 0.9.8 on-fly एजेंट Gradle TestKit द्वारा लोड किए गए वर्गों को
इंस्ट्रूमेंट नहीं कर सकता (`ProjectBuilder` प्लगइन को अलग क्लासलोडर में चलाता है)।
`CodexPlugin`, `CodexExtension` आदि के परीक्षण पास होते हैं किंतु उनकी कवरेज 0% रिपोर्ट होती है।
उन्नयन पथ: Kover 1.x ऑफ़लाइन इंस्ट्रूमेंटेशन (अभी जारी नहीं)।

## JVM ट्यूनिंग

- **Java**: 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin**: `jvmTarget = JVM_24`, `-Xjsr305=strict`
- हीप: `GRADLE_OPTS="-Xmx2g"` द्वारा इंजेस्ट-भारी रन के लिए ट्यून करें

## बिल्ड कमांड

```bash
./gradlew build -x test                  # केवल संकलन
./gradlew :codex-plugin:check           # पूर्ण परीक्षण (JUnit5 + Cucumber)
./gradlew :codex-plugin:test            # केवल JUnit5 यूनिट परीक्षण
./gradlew koverHtmlReport               # कवरेज HTML रिपोर्ट (मैन्युअल)
./gradlew publishToMavenLocal           # स्थानीय प्रकाशन
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## CI पाइपलाइन

`.github/workflows/test.yml` एकमात्र **Build & Test** जॉब परिभाषित करता है
(`ubuntu-latest`, टाइमआउट 15 मिनट):

1. Checkout (`actions/checkout@v4`)
2. JDK 24 Temurin सेटअप (`actions/setup-java@v4`)
3. Gradle सेटअप (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

इस वर्कफ़्लो में कोई tag-on-publish जॉब नहीं है; Maven Central प्रकाशन
`publishAggregationToCentralPortal` कार्य द्वारा मैन्युअल/अलग प्रकाशन प्रवाह में चालित होता है (`AGENTS.adoc` में NMCP खंड देखें)।

## प्रकाशन (NMCP)

`codex-plugin/build.gradle.kts` में कॉन्फ़िगर:

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- `mavenCentral()` रिपॉजिटरी (विरासत Sonatype staging नहीं)
- `signing { useGpgCmd() }` — जब `CI == "true"` या संस्करण `-SNAPSHOT` पर समाप्त हो तो छोड़ा जाता है
- POM **सभी** `withType<MavenPublication>` पर घोषित (केवल `pluginMaven` नहीं):
  Apache 2.0 लाइसेंस, डेवलपर `cccp-education`, SCM → `github.com/cccp-education/codex`
- Sources + javadoc JARs (`withSourcesJar()`, `withJavadocJar()`)
- वैकल्पिक `<relocation>` ब्लॉक `-Prem relocationGroup=<namespace>` द्वारा सक्रिय
  (भविष्य `groupId` माइग्रेशन हेतु तैयार; वर्तमान निष्क्रिय)

ध्यान दें: यहाँ `settings.gradle.kts` में `nmcp` सेटिंग्स प्लगइन शामिल नहीं है; यह codex-gradle रिपॉजिटरी `codex-plugin` को `maven-publish` + `AGENTS.adoc` में प्रलेखित `publishAggregationToCentralPortal` कार्य प्रवाह द्वारा सीधे प्रकाशित करती है।

## Ollama इंस्टेंस (वैश्विक बाध्यता)

पोर्ट `11434–11436` **निषिद्ध** हैं। `11437–11465` (29 पोर्ट) पर रोटेट करें।
अधिकृत मॉडल: `gpt-oss:120b-cloud`, `gemma4:31b-cloud`।

## योगदान

1. बिल्ड संकलित हो: `./gradlew build -x test`
2. परीक्षण हरित हों: `./gradlew :codex-plugin:check`
3. कोई CVE प्रतिगमन नहीं: `org.jetbrains:annotations` बलपिन `26.0.2-1` रखें
4. DDD सम्मेलनों का पालन करें (मान ऑब्जेक्ट, पोर्ट/एडॉप्टर, कोई रिसाव नहीं)
5. सीमा का सम्मान करें: codex = **READ + RAG**; WRITE/PUBLISH तर्क न जोड़ें
   (वह `document-gradle` का है)

## आर्किटेक्चर दस्तावेज़

- [AGENT.adoc](../AGENT.adoc) — निरपेक्ष नियम (कमिट, रहस्य, वर्गीकरण)
- [BACKLOG.adoc](../BACKLOG.adoc) — EPIC PUB प्रकाशन बैकलॉग
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — Codex↔Document सीमा
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — एकीकृत कार्य वर्गीकरण
- `gradle/libs.versions.toml` (मॉड्यूल कैटलॉग) — विहित निर्भरता संस्करण

## लाइसेंस

Apache License 2.0 — देखें [LICENSE](../LICENSE)।

---

_CCCP Education पारिस्थितिकी का भाग — `groupId: education.cccp`।_