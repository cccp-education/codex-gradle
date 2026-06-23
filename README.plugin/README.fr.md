<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — Internes du Plugin

> Guide développeur et contributeur pour le plugin Gradle `codex-plugin`.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=Licence)](../LICENSE)

- **Version** : `0.0.1` · **Groupe** : `education.cccp` · **ID plugin** : `education.cccp.codex`
- **Toolchain** : Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **Build** : `./gradlew build -x test` · **Tests** : `./gradlew :codex-plugin:check` · **Couverture** : Kover 0.9.8 (on-fly ; voir limitation ci-dessous)

🌐 Langues : [English](README.md) | **Français**

---

## Organisation des modules

```
codex-gradle/
├── build.gradle.kts                 # placeholder racine (plugin readme désactivé)
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # catalogue racine (versions des plugins)
└── codex-plugin/
    ├── build.gradle.kts              # module plugin (publishing, signing, kover)
    ├── gradle/libs.versions.toml     # catalogue module (toutes versions de dépendances)
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # Point d'entrée — enregistre 12 tâches
        ├── CodexExtension.kt         # Extension `codex { ... }` (zone + pgvector)
        ├── Metadata.kt               # Format pivot metadata workspace
        ├── LicenseZoneDetector.kt    # Auto-détection zone OSS/CSS/UNKNOWN au chargement
        ├── ocr/                       # Contrats OCR (OcrConfig, OcrRequest, OcrResult, engines)
        ├── store/                     # CodexVectorStore — client R2DBC pgvector
        └── tasks/                     # 12 implémentations de tâches (+ DocNode, FontStyle)
```

## Tâches enregistrées

12 tâches réparties en 4 groupes taxonomiques (voir `CodexPlugin.kt`) :

| Groupe | Tâches |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## Contrats N0 (depuis workspace-bom MEMPHIS)

Codex importe `education.cccp:workspace-bom:0.0.1` comme BOM plateforme et consomme directement :

| Contrat | Artefact | Fournit |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

Autres contrats N0 disponibles via la BOM (non tous directement importés par codex) :
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`.

## Dépendances clés

- **koog-agents** 1.0.0 — orchestrateur agentic (DSL Kotlin StateGraph)
- **langchain4j** 1.14.1 — fournisseurs LLM, RAG, embeddings
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — embeddings ONNX de phrases
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — pgvector réactif
- **kotlinx-coroutines** 1.9.0 (core + reactive) — pont R2DBC
- **Apache PDFBox** 3.0.4 — extraction texte/structure PDF
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — détection de format
- **Flexmark** 0.64.8 (`flexmark-all`) — traitement Markdown
- **AsciidoctorJ** 3.0.0 — parsing/rendu AsciiDoc
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — sérialisation JSON/YAML
- **kotlinx-serialization-json** 1.7.3

Le `buildscript` force `org.jetbrains:annotations:26.0.2-1` pour surcharger le pin
strict de Gradle (`{strictly 13.0}`) qui casserait sinon les transitifs de koog/flexmark.

## Matrice de tests

Les sources de test sont sous `codex-plugin/src/test/kotlin/` :

| Périmètre | Emplacement | Nombre |
|------|----------|-------|
| Unitaires (JUnit5) | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 classes de spec |
| BDD (Cucumber) | `codex/bdd/PipelineSteps.kt` + 5 fichiers `.feature` | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| Intégration | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

Tous les tests utilisent `useJUnitPlatform()` ; le logging émet les événements `FAILED` + `SKIPPED`.
Testcontainers fournit des conteneurs `pgvector/pgvector:pg17` via
`testcontainers-postgresql` 1.21.4 et `docker-java` 3.7.0 (transport httpclient5).

Il n'y a **pas** de split `testFast`/`testAll`/`testEpics` ni de gate `koverVerify`
défini ici — la tâche unique `:codex-plugin:check` exécute la suite complète
JUnit5 + Cucumber. Kover émet des rapports XML + HTML mais n'est pas câblé dans
`check` (`onCheck = false`).

### Limitation de couverture connue

L'agent on-fly Kover 0.9.8 ne peut pas instrumenter les classes chargées par Gradle
TestKit (`ProjectBuilder` exécute le plugin dans un classloader séparé). Les tests
de `CodexPlugin`, `CodexExtension`, etc. passent mais leur couverture est reportée
à 0 %. Voie de mise à niveau : Kover 1.x instrumentation offline (non encore released).

## Réglage JVM

- **Java** : 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin** : `jvmTarget = JVM_24`, `-Xjsr305=strict`
- Heap : régler via `GRADLE_OPTS="-Xmx2g"` pour les runs lourds en ingestion

## Commandes de build

```bash
./gradlew build -x test                  # compilation seule
./gradlew :codex-plugin:check           # tests complets (JUnit5 + Cucumber)
./gradlew :codex-plugin:test            # tests unitaires JUnit5 seulement
./gradlew koverHtmlReport               # rapport de couverture HTML (manuel)
./gradlew publishToMavenLocal           # publication locale
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## Pipeline CI

`.github/workflows/test.yml` définit un job unique **Build & Test**
(`ubuntu-latest`, timeout 15 min) :

1. Checkout (`actions/checkout@v4`)
2. JDK 24 Temurin (`actions/setup-java@v4`)
3. Gradle (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

Il n'y a **pas** de job publish-on-tag dans ce workflow ; la publication Maven Central
est pilotée par la tâche `publishAggregationToCentralPortal` invoquée manuellement /
dans un flux de publication séparé (voir section NMCP dans `AGENTS.adoc`).

## Publication (NMCP)

Configurée dans `codex-plugin/build.gradle.kts` :

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- Dépôt `mavenCentral()` (pas l'ancien staging Sonatype)
- `signing { useGpgCmd() }` — ignoré quand `CI == "true"` ou version en `-SNAPSHOT`
- POM déclaré sur **toutes** les `withType<MavenPublication>` (pas seulement `pluginMaven`) :
  licence Apache 2.0, développeur `cccp-education`, SCM → `github.com/cccp-education/codex`
- JARs sources + javadoc (`withSourcesJar()`, `withJavadocJar()`)
- Bloc `<relocation>` optionnel activé par `-Pre locationGroup=<namespace>`
  (préparé pour migration future de `groupId` ; actuellement inerte)

Note : `settings.gradle.kts` n'inclut **pas** le plugin settings `nmcp` ;
ce dépôt codex-gradle publie `codex-plugin` directement via `maven-publish`
+ le flux de tâche `publishAggregationToCentralPortal` documenté dans `AGENTS.adoc`.

## Instances Ollama (contrainte globale)

Les ports `11434–11436` sont **interdits**. Rotation sur `11437–11465` (29 ports).
Modèles autorisés : `gpt-oss:120b-cloud`, `gemma4:31b-cloud`.

## Contribuer

1. Le build compile : `./gradlew build -x test`
2. Tests verts : `./gradlew :codex-plugin:check`
3. Pas de régression CVE : garder le forçage `org.jetbrains:annotations` à `26.0.2-1`
4. Suivre les conventions DDD (value objects, ports/adaptateurs, sans fuites)
5. Respecter le boundary : codex = **READ + RAG** ; ne pas ajouter de logique WRITE/PUBLISH
   (cela appartient à `document-gradle`)

## Docs d'architecture

- [AGENT.adoc](../AGENT.adoc) — Règles absolues (commits, secrets, classification)
- [BACKLOG.adoc](../BACKLOG.adoc) — Backlog EPIC PUB publication
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — Boundary Codex↔Document
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — Taxonomie unifiée des tâches
- `gradle/libs.versions.toml` (catalogue module) — versions canoniques des dépendances

## Licence

Apache License 2.0 — voir [LICENSE](../LICENSE).

---

_Partie de l'écosystème CCCP Education — `groupId: education.cccp`._