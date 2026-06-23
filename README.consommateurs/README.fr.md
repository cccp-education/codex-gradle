<!-- translated from README.md rev 1 -->
# codex-gradle — Guide Consommateur

> Plugin Gradle d'acquisition de documents : PDF/EPUB → extraction → chunking → RAG pgvector.

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![Licence](https://img.shields.io/github/license/cheroliv/codex-gradle?label=Licence)](../LICENSE)

- **Version** : `0.0.1` · **Groupe** : `education.cccp` · **ID Plugin** : `education.cccp.codex`
- **Build** : `./gradlew build` · **Tests** : `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **Dépendance amont** : [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — consommée comme source unique de vérité pour `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig`.

🌐 Langues : [English](README.md) | **Français**

---

## Ce qu'il fait

`codex-gradle` acquiert des documents non structurés (PDF, EPUB), extrait le
texte structuré et la hiérarchie de l'ouvrage, convertit en Markdown/AsciiDoc,
réalise le chunking sémantique, vectorise avec ONNX (`all-MiniLM-L6-v2`),
stocke les embeddings dans **PostgreSQL + pgvector** via **R2DBC**, et expose la
recherche par similarité cosinus pour alimenter la couche **RAG** et le
**Knowledge Graph** de CCCP Education.

Il se situe en amont de [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle)
dans l'écosystème CCCP Education :

```
corpus documentaire → codex-gradle (READ + RAG) → [codebase-gradle] → agents koog → sortie
```

Frontière (documentée dans `.agents/ARCHITECTURE_BOUNDARY.adoc`) : **codex = READ +
RAG** ; le plugin frère `document-gradle` gère **WRITE + PUBLISH**. Les deux
plugins N2 sont parallèles, pas empilés.

## Démarrage rapide

### 1. Appliquer le plugin

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. Configurer la connexion pgvector (optionnel — valeurs par défaut affichées)

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. Acquérir et ingérer un corpus

```bash
./gradlew transformCorpusToPdf          # auto-détection PDF/EPUB → extraction → chunk → JSON
./gradlew collectIngest                 # vectorisation des chunks → pgvector (lot 32)
./gradlew collectRetrieve --query="..." # similarité cosinus top-K
```

## Tâches disponibles

| Tâche | Groupe | Description |
|-------|--------|-------------|
| `collectText`              | collect  | Extraction du texte brut d'un PDF avec métadonnées typographiques |
| `collectBookStructure`     | collect  | Extraction de structure d'un PDF (titres/sections) → `.adoc` hiérarchique |
| `collectEpubStructure`      | collect  | Extraction de structure d'un EPUB (XHTML → `.adoc` + blocs de code) |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | Vectorisation ONNX → pgvector via R2DBC (lot par défaut 32) |
| `collectRetrieve`          | collect  | Recherche sémantique similarité cosinus dans pgvector (top-K par défaut 10) |
| `transformToJsonLdd`       | transform | Parser un `.adoc` via AsciidoctorJ → JSON LDD |
| `transformToMarkdown`      | transform | Structure AsciiDoc → Markdown (hiérarchie + blocs de code préservés) |
| `transformChunk`           | transform | Chunking sémantique par section (1 chunk par titre) |
| `transformCorpusToPdf`     | transform | Pipeline composite (auto-détection PDF/EPUB) : extraction → Markdown → chunk → JSON |
| `generateCompositeContext` | generate | Recherche sémantique via `CodexVectorStore` → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | Agréger les chunks → base de connaissance multi-format (JSON-L, Markdown, AsciiDoc) |

Les tâches suivent la taxonomie unifiée du workspace (4 verbes : GENERATE /
COLLECT / TRANSFORM / DEPLOY) — voir `TAXONOMIE_WORKSPACE.adoc`.

## DSL d'extension

```gradle
codex {
    // Zone de licence (auto-détectée via LicenseZoneDetector ; OSS/CSS/UNKNOWN)
    zone = codex.LicenseZone.OSS

    // Paramètres de connexion pgvector (tous par défaut aux valeurs affichées)
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

La tâche `generateCompositeContext` lit aussi ces propriétés Gradle :

| Propriété | Défaut | Rôle |
|-----------|--------|------|
| `query`  | `"architecture du workspace"` | Requête de recherche sémantique |
| `topK`   | `"10"`                         | Nombre de plus proches voisins |

Exemple :

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json (compatible runner)
```

## Prérequis

- **Java** 24 (toolchain Kotlin 2.3.20, `jvmTarget = 24`)
- **Gradle** 9.5.1
- **PostgreSQL** 15+ avec l'extension `pgvector` (pour `collectIngest`/`collectRetrieve`)
- **Docker** (pour les tests d'intégration Testcontainers)

## Build & test

```bash
./gradlew build                            # build complet (compile + tests)
./gradlew build -x test                    # compilation seule
./gradlew :codex-plugin:check              # tests (JUnit5 + Cucumber BDD)
./gradlew publishToMavenLocal              # publication locale
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## Dépannage

| Symptôme | Solution |
|----------|----------|
| Conflit de version `org.jetbrains:annotations` (Gradle pigne 13.0) | Déjà forcée à `26.0.2-1` dans `buildscript` ; relancer avec `--refresh-dependencies` |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Conteneur Postgres Testcontainers bloqué | `docker rm -f $(docker ps -aq --filter name=postgres)` puis réessayer |
| Kover signale 0 % sur les classes du plugin | Limitation connue : l'agent à la volée Kover 0.9.8 ne peut intercepter le classloader Gradle TestKit `ProjectBuilder`. Voir la note dans `build.gradle.kts`. |

## Licence

Licence Apache 2.0 — voir [LICENSE](../LICENSE).

---

_Fait partie de l'écosystème CCCP Education — `groupId : education.cccp`._