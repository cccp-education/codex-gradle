<!-- translated from README.md rev 0.0.1 -->
# codex-gradle — 插件内部机制

> `codex-plugin` Gradle 插件的开发者与贡献者指南。

[![Maven Central](https://img.shields.io/maven-central/v/education.cccp/codex-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/education.cccp/codex-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/education.cccp.codex.svg?label=Plugin%20Portal)](https://plugins.gradle.org/plugin/education.cccp.codex)
[![CI](https://img.shields.io/github/actions/workflow/status/cheroliv/codex-gradle/test.yml?branch=main&label=tests)](https://github.com/cheroliv/codex-gradle/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/cheroliv/codex-gradle?label=License)](../LICENSE)

- **版本**: `0.0.1` · **组**: `education.cccp` · **插件 ID**: `education.cccp.codex`
- **工具链**: Java 24 · Kotlin 2.3.20 · Gradle 9.5.1 (wrapper) · plugin-publish 1.3.1
- **构建**: `./gradlew build -x test` · **测试**: `./gradlew :codex-plugin:check` · **覆盖率**: Kover 0.9.8 (on-fly; 见下方已知限制)

🌐 Languages: [English](README.md) | **中文** | [हिन्दी](README.hi.md) | [Español](README.es.md) | [Français](README.fr.md) | [العربية](README.ar.md) | [বাংলা](README.bn.md) | [Português](README.pt.md) | [Русский](README.ru.md) | [اردو](README.ur.md)

---

## 模块布局

```
codex-gradle/
├── build.gradle.kts                 # 根占位符（readme 插件已禁用）
├── settings.gradle.kts              # mavenLocal + gradlePluginPortal + mavenCentral
├── gradle/libs.versions.toml         # 根目录（插件版本）
└── codex-plugin/
    ├── build.gradle.kts              # 插件模块（发布、签名、kover）
    ├── gradle/libs.versions.toml     # 模块目录（所有依赖版本）
    └── src/main/kotlin/codex/
        ├── CodexPlugin.kt            # 插件入口 — 注册 12 个任务
        ├── CodexExtension.kt         # `codex { ... }` 扩展（zone + pgvector）
        ├── Metadata.kt               # 工作区 pivot 元数据格式
        ├── LicenseZoneDetector.kt    # 加载时自动检测 OSS/CSS/UNKNOWN 区域
        ├── ocr/                      # OCR 契约（OcrConfig, OcrRequest, OcrResult, engines）
        ├── store/                    # CodexVectorStore — R2DBC pgvector 客户端
        └── tasks/                    # 12 个任务实现（+ DocNode, FontStyle）
```

## 已注册任务

跨 4 个分类组的 12 个任务（见 `CodexPlugin.kt`）：

| 组 | 任务 |
|-------|-------|
| collect   | `collectText`, `collectBookStructure`, `collectEpubStructure`, `collectBookSql`, `collectIngest`, `collectRetrieve` |
| transform | `transformToJsonLdd`, `transformToMarkdown`, `transformChunk`, `transformCorpusToPdf` |
| generate  | `generateCompositeContext` |
| deploy    | `deployKnowledgeBase` |

## N0 契约（来自 workspace-bom MEMPHIS）

Codex 导入 `education.cccp:workspace-bom:0.0.1` 作为平台 BOM 并直接消费：

| 契约 | 制品 | 提供 |
|----------|----------|----------|
| `codebase-contracts` | `education.cccp:codebase-contracts:0.0.1` | `ContextChannel`, `ChannelBudget`, `CompositeContext`, `CompositeContextConfig` |

通过 BOM 可用的其他 N0 契约（并非所有都被 codex 直接导入）：
`agent-contracts`, `llm-pool-contracts`, `opencode-session-contracts`,
`i18n-contracts`, `pipeline-contracts`。

## 关键依赖

- **koog-agents** 1.0.0 — 智能体编排器（DSL Kotlin StateGraph）
- **langchain4j** 1.14.1 — LLM 提供者、RAG、嵌入
- **langchain4j-embeddings-all-minilm-l6-v2** 1.14.1-beta24 — ONNX 句嵌入
- **R2DBC** (postgresql 1.0.7.RELEASE, pool 1.0.2.RELEASE, spi 1.0.0.RELEASE) — 响应式 pgvector
- **kotlinx-coroutines** 1.9.0 (core + reactive) — R2DBC 桥接
- **Apache PDFBox** 3.0.4 — PDF 文本/结构提取
- **Apache Tika** 3.1.0 (core + parsers-standard-package) — 格式检测
- **Flexmark** 0.64.8 (`flexmark-all`) — Markdown 处理
- **AsciidoctorJ** 3.0.0 — AsciiDoc 解析/渲染
- **Jackson** 2.18.2 (module-kotlin + dataformat-yaml) — JSON/YAML 序列化
- **kotlinx-serialization-json** 1.7.3

`buildscript` 强制 `org.jetbrains:annotations:26.0.2-1` 以覆盖 Gradle 的
严格固定（`{strictly 13.0}`），否则会破坏 koog/flexmark 的传递依赖。

## 测试矩阵

源码位于 `codex-plugin/src/test/kotlin/` 下：

| 范围 | 位置 | 数量 |
|------|----------|-------|
| 单元（JUnit5） | `codex/`, `codex/ocr/`, `codex/store/`, `codex/tasks/` | 17 个 spec 类 |
| BDD（Cucumber） | `codex/bdd/PipelineSteps.kt` + 5 个 `.feature` 文件 | `extract-chunk-pipeline`, `chunk-ingest-pipeline`, `ingest-retrieve-pipeline`, `license-zone-detection`, `pipeline-auto-detect` |
| 集成 | `codex/tasks/CodexIngestRetrieveIT.kt` | Testcontainers PostgreSQL |

所有测试使用 `useJUnitPlatform()`；测试日志输出 `FAILED` + `SKIPPED` 事件。
Testcontainers 通过 `testcontainers-postgresql` 1.21.4 和 `docker-java`
3.7.0 (httpclient5 transport) 提供 `pgvector/pgvector:pg17` 风格的容器。

此处**没有** `testFast`/`testAll`/`testEpics` 分离任务，也**没有**定义
`koverVerify` 门控——单一的 `:codex-plugin:check` 任务运行完整的
JUnit5 + Cucumber 套件。Kover 输出 XML + HTML 报告，但未接入
`check`（`onCheck = false`）。

### 已知覆盖率限制

Kover 0.9.8 on-fly 代理无法插桩由 Gradle TestKit 加载的类
（`ProjectBuilder` 在单独的类加载器中运行插件）。`CodexPlugin`、
`CodexExtension` 等的测试通过，但其覆盖率报告为 0 %。
升级路径：Kover 1.x 离线插桩（尚未发布）。

## JVM 调优

- **Java**: 24 (`sourceCompatibility`/`targetCompatibility = VERSION_24`, `jvmToolchain(24)`)
- **Kotlin**: `jvmTarget = JVM_24`, `-Xjsr305=strict`
- 堆：通过 `GRADLE_OPTS="-Xmx2g"` 为重度摄取运行调优

## 构建命令

```bash
./gradlew build -x test                  # 仅编译
./gradlew :codex-plugin:check           # 完整测试（JUnit5 + Cucumber）
./gradlew :codex-plugin:test            # 仅 JUnit5 单元测试
./gradlew koverHtmlReport               # 覆盖率 HTML 报告（手动）
./gradlew publishToMavenLocal           # 本地发布
./gradlew publishAggregationToCentralPortal --no-daemon   # Maven Central (CI)
```

## CI 管道

`.github/workflows/test.yml` 定义了单一的 **Build & Test** 作业
（`ubuntu-latest`，超时 15 分钟）：

1. Checkout (`actions/checkout@v4`)
2. 设置 JDK 24 Temurin (`actions/setup-java@v4`)
3. 设置 Gradle (`gradle/actions/setup-gradle@v4`)
4. `./gradlew publishToMavenLocal`
5. `./gradlew :codex-plugin:check`

此工作流中**没有** tag 触发发布作业；Maven Central 发布由
`publishAggregationToCentralPortal` 任务手动/在独立发布流程中驱动
（见 `AGENTS.adoc` 中的 NMCP 章节）。

## 发布（NMCP）

在 `codex-plugin/build.gradle.kts` 中配置：

- `group = "education.cccp"`, `version = libs.versions.codex.plugin` (`0.0.1`)
- `mavenCentral()` 仓库（非遗留 Sonatype staging）
- `signing { useGpgCmd() }` — 当 `CI == "true"` 或版本以 `-SNAPSHOT` 结尾时跳过
- POM 声明于**所有** `withType<MavenPublication>`（不仅 `pluginMaven`）：
  Apache 2.0 许可证、开发者 `cccp-education`、SCM → `github.com/cccp-education/codex`
- Sources + javadoc JARs (`withSourcesJar()`, `withJavadocJar()`)
- 可选 `<relocation>` 块通过 `-Prem relocationGroup=<namespace>` 激活
  （为未来 `groupId` 迁移准备；当前不生效）

注意：此处的 `settings.gradle.kts` **不**包含 `nmcp` 设置插件；
本 codex-gradle 仓库通过 `maven-publish` + `AGENTS.adoc` 中记录的
`publishAggregationToCentralPortal` 任务流直接发布 `codex-plugin`。

## Ollama 实例（全局约束）

端口 `11434–11436` **被禁止**。在 `11437–11465`（29 个端口）上轮换。
授权模型：`gpt-oss:120b-cloud`, `gemma4:31b-cloud`。

## 贡献

1. 构建编译通过：`./gradlew build -x test`
2. 测试通过：`./gradlew :codex-plugin:check`
3. 无 CVE 回归：保持 `org.jetbrains:annotations` 强制固定为 `26.0.2-1`
4. 遵循 DDD 约定（值对象、端口/适配器、无泄漏）
5. 遵守边界：codex = **READ + RAG**；不要添加 WRITE/PUBLISH 逻辑
   （那属于 `document-gradle`）

## 架构文档

- [AGENT.adoc](../AGENT.adoc) — 绝对规则（提交、密钥、分类）
- [BACKLOG.adoc](../BACKLOG.adoc) — EPIC PUB 发布待办
- `.agents/ARCHITECTURE_BOUNDARY.adoc` — Codex↔Document 边界
- [TAXONOMIE_WORKSPACE.adoc](../../../../configuration/TAXONOMIE_WORKSPACE.adoc) — 统一任务分类法
- `gradle/libs.versions.toml` (模块目录) — 规范依赖版本

## 许可证

Apache License 2.0 — 参见 [LICENSE](../LICENSE)。

---

_CCCP Education 生态的一部分 — `groupId: education.cccp`。_