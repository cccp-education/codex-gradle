<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — 消费者指南

> 文档获取 Gradle 插件：PDF/EPUB → 提取 → 分块 → pgvector RAG。

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **版本**: `0.0.1` · **组**: `education.cccp` · **插件 ID**: `education.cccp.codex`
- **构建**: `./gradlew build` · **测试**: `./gradlew :codex-plugin:check` (JUnit5 + Cucumber BDD)
- **上游依赖**: [`education.cccp:codebase-contracts`](https://central.sonatype.com/artifact/education.cccp/codebase-contracts) — 作为 `ContextChannel`、`ChannelBudget`、`CompositeContext`、`CompositeContextConfig` 的唯一可信源被消费。

🌐 Languages: [English](README.md) | **中文** | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## 它做什么

`codex-gradle` 获取非结构化文档（PDF、EPUB），提取结构化文本与书籍层级，转换为 Markdown/AsciiDoc，执行语义分块，使用 ONNX（`all-MiniLM-L6-v2`）向量化，将嵌入存储于 **PostgreSQL + pgvector**（基于 **R2DBC**），并暴露余弦相似度检索以供 CCCP Education **RAG** 层和 **知识图谱** 使用。

它在 CCCP Education 生态中位于 [`codebase-gradle`](https://github.com/cheroliv/codebase-gradle) 的上游：

```
document corpus → codex-gradle (READ + RAG) → [codebase-gradle] → koog agents → output
```

边界（记录于 `.agents/ARCHITECTURE_BOUNDARY.adoc`）：**codex = READ + RAG**；兄弟插件 `document-gradle` 处理 **WRITE + PUBLISH**。两个 N2 插件是平行的，而非堆叠的。

## 快速开始

### 1. 应用插件

```gradle
plugins {
    id("education.cccp.codex") version "0.0.1"
}
```

### 2. 配置 pgvector 连接（可选 — 显示默认值）

```gradle
codex {
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

### 3. 获取并摄取语料库

```bash
./gradlew transformCorpusToPdf          # 自动检测 PDF/EPUB → 提取 → 分块 → JSON
./gradlew collectIngest                 # 向量化分块 → pgvector（批大小 32）
./gradlew collectRetrieve --query="..." # 余弦相似度 top-K
```

## 可用任务

| 任务 | 组 | 描述 |
|------|-------|-------------|
| `collectText`              | collect  | 从 PDF 提取原始文本及排版元数据 |
| `collectBookStructure`     | collect  | PDF 结构提取（标题/章节）→ 层级 `.adoc` |
| `collectEpubStructure`      | collect  | EPUB 结构提取（XHTML → `.adoc` + 代码块） |
| `collectBookSql`           | collect  | JSON LDD → DDL + INSERT PostgreSQL |
| `collectIngest`            | collect  | ONNX 向量化 → 通过 R2DBC 写入 pgvector（默认批 32） |
| `collectRetrieve`          | collect  | pgvector 中余弦相似度语义搜索（默认 top-K 10） |
| `transformToJsonLdd`       | transform | 通过 AsciidoctorJ 解析 `.adoc` → JSON LDD |
| `transformToMarkdown`      | transform | AsciiDoc 结构 → Markdown（保留层级与代码块） |
| `transformChunk`           | transform | 语义段落分块（每个标题一个分块） |
| `transformCorpusToPdf`     | transform | 组合管道（自动检测 PDF/EPUB）：提取 → Markdown → 分块 → JSON |
| `generateCompositeContext` | generate | 通过 `CodexVectorStore` 语义搜索 → `composite-context.json` |
| `deployKnowledgeBase`      | deploy   | 聚合分块 → 多格式知识库（JSON-L、Markdown、AsciiDoc） |

任务遵循统一的工作区分类法（4 个动词：GENERATE / COLLECT / TRANSFORM / DEPLOY）——见 `TAXONOMIE_WORKSPACE.adoc`。

## 扩展 DSL

```gradle
codex {
    // 许可区域（通过 LicenseZoneDetector 自动检测；OSS/CSS/UNKNOWN）
    zone = codex.LicenseZone.OSS

    // pgvector 连接参数（均默认为所示值）
    pgvectorHost     = "localhost"
    pgvectorPort     = "5432"
    pgvectorDatabase = "codex"
    pgvectorUser     = "codex"
    pgvectorPassword = "codex"
}
```

`generateCompositeContext` 任务还读取以下 Gradle 属性：

| 属性 | 默认值 | 用途 |
|----------|---------|---------|
| `query`  | `"architecture du workspace"` | 语义搜索查询 |
| `topK`   | `"10"`                         | 最近邻数量 |

示例：

```bash
./gradlew generateCompositeContext --query="pipeline dag runner" --topK=5
# → build/codex/composite-context.json（与 runner 兼容）
```

## 前提条件

- **Java** 24（Kotlin 2.3.20 工具链，`jvmTarget = 24`）
- **Gradle** 9.5.1
- **PostgreSQL** 15+ 及 `pgvector` 扩展（用于 `collectIngest`/`collectRetrieve`）
- **Docker**（用于基于 Testcontainers 的集成测试）

## 构建与测试

```bash
./gradlew build                            # 完整构建（编译 + 测试）
./gradlew build -x test                    # 仅编译
./gradlew :codex-plugin:check              # 测试（JUnit5 + Cucumber BDD）
./gradlew publishToMavenLocal              # 本地发布
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central（CI）
```

## 故障排除

| 症状 | 修复 |
|---------|-----|
| `org.jetbrains:annotations` 版本冲突（Gradle 固定为 13.0） | 已在 `buildscript` 中强制为 `26.0.2-1`；使用 `--refresh-dependencies` 重跑 |
| `Java heap space`            | `export GRADLE_OPTS="-Xmx2g"` |
| Postgres Testcontainer 卡住 | `docker rm -f $(docker ps -aq --filter name=postgres)` 后重试 |
| Kover 报告插件类覆盖率为 0% | 已知限制：Kover 0.9.8 on-fly 代理无法拦截 Gradle TestKit `ProjectBuilder` 类加载器。见 `build.gradle.kts` 中说明。 |

## 许可证

Apache License 2.0 — 参见 [LICENSE](../LICENSE)。

---

_CCCP Education 生态的一部分 — `groupId: education.cccp`。_