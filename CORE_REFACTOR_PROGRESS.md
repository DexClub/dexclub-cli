# Core 重构进度

## 说明

本文件用于跟踪 `core` 模块重构的实际推进状态。

它不是方案文档的重复版本，而是执行过程中的进度面板。方案、边界与设计以 `CORE_REFACTOR_PLAN.md` 为准；本文件只负责记录当前做到哪里、卡在哪里、下一步做什么。

## 事实来源优先级

恢复工作时，按以下顺序确认真实状态：

1. 当前代码与工作区状态
2. 本进度文档
3. 本轮对话中的最新决定
4. 旧对话记忆

约束：

- 如果代码与文档冲突，先以代码现状为准
- 校对后必须回写本进度文档
- 不能只根据旧对话记忆继续工作
- 本地未提交改动默认视为有效现场，不得因文档未记录而忽略

## 会话启动清单

每次恢复工作时，至少按以下顺序执行：

1. 读取本进度文档
2. 查看当前工作区状态，例如 `git status --short`
3. 确认当前主任务相关文件的实际代码状态与 diff
4. 如果文档与代码不一致，先更新文档，再继续工作
5. 再决定继续原事项、解除阻塞，还是新增事项

## 当前工作区快照

记录时间：`2026-04-06`

- `CORE_REFACTOR_PROGRESS.md`
  - 已存在未提交改动，已回写 `P-01` 完成状态与当前工作区快照
- `core/src/commonMain/kotlin/io/github/dexclub/core/DexEngine.kt`
  - 当前与已提交状态一致
- `core/src/jvmMain/kotlin/io/github/dexclub/core/DexEngine.jvm.kt`
  - 已存在未提交改动，已把 `inspect` 与搜索职责下沉到内部服务
- `core/src/jvmMain/kotlin/io/github/dexclub/core/input/DexArchiveInspector.kt`
  - 已新增，来自本轮 `T-04` 第一轮 facade 收缩
- `core/src/jvmMain/kotlin/io/github/dexclub/core/search/DexSearchService.kt`
  - 已新增，来自已提交的 `T-04`
- `core/src/jvmMain/kotlin/io/github/dexclub/core/export/DexBinaryExportService.kt`
  - 已新增，来自本轮 `P-01`
- `core/src/jvmMain/kotlin/io/github/dexclub/core/export/SmaliRenderService.kt`
  - 已新增，来自本轮 `P-01`
- `core/src/jvmMain/kotlin/io/github/dexclub/core/export/JadxDecompilerService.kt`
  - 已新增，来自本轮 `P-01`
- `core/src/jvmMain/kotlin/io/github/dexclub/core/export/DexExportService.kt`
  - 已存在未提交改动，已收缩为导出流程编排与结果包装
- `dexkit/vendor/DexKit`
  - 当前为 dirty 状态，非本轮 `core` 重构变更，不应擅自处理

约束：

- 恢复工作时，应先确认以上快照是否仍然成立
- 如果工作区状态已经变化，应先更新本节内容

## 状态定义

### `已计划`

表示事项已经进入重构范围，但还没有拆成可直接执行的任务。

进入条件：

- 已确认这件事需要做
- 但完成定义、依赖或切入点还未收敛

### `待开始`

表示事项已经足够明确，可以直接开工，但尚未实际执行。

进入条件：

- 目标明确
- 依赖明确
- 至少知道第一步怎么做

### `进行中`

表示事项已经开始实际执行。

进入条件：

- 已经开始修改代码、补测试、验证或整理实现

约束：

- 同时只保留 1 个主 `进行中` 事项
- `进行中` 必须写明当前停点与下一步

### `已阻塞`

表示事项已开始，但因缺信息、缺环境、缺决策或被外部改动打断而无法继续。

进入条件：

- 事项曾经进入 `进行中`
- 当前无法继续推进

约束：

- 必须写明阻塞原因
- 必须写明解除条件
- 阻塞解除后，应回到 `待开始` 或 `进行中`

### `已完成`

表示事项已经完成，且满足该事项的最低验证要求。

进入条件：

- 实际目标已经完成
- 已记录结果摘要
- 已记录验证方式

约束：

- 没有验证结果，不进入 `已完成`

## 状态流转

默认流转：

```text
已计划 -> 待开始 -> 进行中 -> 已完成
```

阻塞流转：

```text
进行中 -> 已阻塞 -> 待开始
进行中 -> 已阻塞 -> 进行中
```

约束：

- 不允许从 `已计划` 直接跳到 `已完成`
- 不建议跳过 `待开始` 直接进入 `进行中`
- 事项进入 `进行中` 后，不应随意改语义
- 如果范围明显变化，应新建事项或子事项，而不是直接改写原事项

## 中断与续接规则

### 中断时

- 被打断时，不能把 `进行中` 直接改成 `已完成`
- 如果只是暂时停下但还能继续，保持 `进行中`
- 如果因为缺信息、缺环境、缺决策而停下，改成 `已阻塞`
- 中断时必须补：
  - 当前停点
  - 下一步
  - 阻塞原因（如果有）

### 对话结束时

- 对话结束不等于任务完成
- 结束前应刷新一次进度状态
- 未做完的事项只能留在 `待开始`、`进行中` 或 `已阻塞`
- `已完成` 必须已经满足事项定义里的验证要求

### 下次对话时

- 先读本进度文档
- 再检查代码现场与本地未提交改动
- 如果文档与代码不一致，以代码现状为准，再更新文档
- 默认优先处理顺序：
  1. `进行中`
  2. `已阻塞`
  3. `待开始`
  4. `已计划`

## 未提交改动处理规则

恢复工作时，需要先区分：

- 当前任务改动
- 用户手动改动
- 与当前任务无关的旁路改动

约束：

- 不明归属的改动，默认先视为外部改动
- 未确认前，不覆盖、不回退、不重写
- 如果某事项依赖当前脏工作区，应在事项备注中标明

## 事项记录格式

每个事项至少应包含：

- `标题`
- `状态`
- `更新时间`
- `说明`
- `影响范围`
- `完成定义`
- `下一步`
- `验证`

如有阻塞，还应补：

- `阻塞原因`
- `解除条件`

## 当前主任务

- `core` 模块重构收敛与分阶段落地

## 最近一次变更记录

### `2026-04-06`

- 已完成 `core` 重构方案文档整理，形成 `CORE_REFACTOR_PLAN.md`
- 已建立本进度文档，并补充状态、续接、未提交改动与会话恢复规则
- 已在 `AGENTS.md` 增加 `core` 重构文档续接入口
- 已完成 `T-01` 第一轮落地：新增 `CoreRuntimeConfig`、`DexFormatConfig`、`SmaliRenderConfig`、`JavaDecompileConfig`、`DexKitRuntimeConfig`
- `DexEngine`、`DexSessionLoader`、`DexExportService`、`DexKitRuntime` 已开始从配置模型读取默认值
- 已提交 `T-01`，提交号：`bbf20f9`
- 已完成 `T-02`：新增 `core/model`、`core/request`，并为 `DexEngine` 补充基于自有模型的新接口
- 已完成 `T-03`：新增 `core` JVM 测试，测试时通过 `javac + d8` 动态生成最小 dex 夹具
- 已提交 `T-02`、`T-03`，提交号：`7ff645d`
- 已开始 `T-04`：把 `inspect` 与搜索逻辑从 `DexEngine` 下沉到 `DexArchiveInspector`、`DexSearchService`
- 已完成 `T-04`：`DexEngine` 当前主要负责组合、兼容入口与生命周期管理，输入分析、搜索、导出能力均已下沉到内部服务
- 已提交 `T-04`，提交号：`3286ccc`
- 已完成 `P-01`：把 `DexExportService` 进一步拆分为 dex 导出、smali 渲染、jadx 反编译三个内部服务
- 已确认 `P-02` 结论：`core` 必须继续保留 KMP 结构，不再评估 JVM-only 回收方案
- 已开始 CLI 迁移：`cli` 已切到 `core` 自有 model/request 新接口，停止依赖 `DexEngine` 的旧透传搜索与导出方法
- 已进一步收敛搜索边界：新增 `DexKitSearchBackend`，由其承接 `DexKit` 查询 DSL，`DexSearchService` 仅负责搜索语义与结果映射
- 已开始收敛旧兼容接口：`DexEngine` 中旧搜索/导出透传方法已标记为废弃，仓库内测试主体已切到新 API
- 已删除 `DexEngine` 中已不再被仓库内使用的旧透传公开接口，`core` 对外公共边界不再暴露 `DexKitBridge`、`ClassData`、`MethodData`
- 已完成 CLI 多输入能力第一轮：`inspect`、`search-class`、`search-string` 现支持重复传入 `--input`
- 已明确 CLI 多输入边界：单输入支持 `apk|dex`，多输入当前仅支持多个 `dex`；`export-*` 仍保持单个 `dex` 输入
- 已补齐多输入结果来源映射：搜索结果在多输入模式下可返回对应 `sourceDexPath`
- 已同步更新 CLI 帮助文案与 README 中的输入约束说明
- 已执行 `./gradlew :core:compileKotlinJvm :cli:compileKotlin`，验证通过
- 已执行 `./gradlew :core:jvmTest`，验证通过
- 已执行 `./gradlew :core:compileKotlinJvm :cli:compileKotlin :cli:fatJar`，验证通过
- 已额外执行多输入命令级冒烟：使用临时生成的两个最小 dex 验证 `inspect`、`search-class`、`search-string`，结果符合预期

## 已计划

### P-01 拆分导出实现

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：已将当前集中在 `DexExportService` 内的 dex 导出、smali 渲染、jadx 反编译拆分为 `DexBinaryExportService`、`SmaliRenderService`、`JadxDecompilerService`，`DexExportService` 只负责流程编排与结果包装。
- 影响范围：`core/src/jvmMain/kotlin/io/github/dexclub/core/export/` 及 `DexEngine.jvm.kt`
- 完成定义：导出相关职责已拆分为更清晰的内部服务，`DexEngine` 不再直接承载导出细节，且 CLI 行为保持不变。
- 下一步：后续如继续收敛，可再评估是否需要把导出流程编排从 `DexExportService` 进一步细拆，但当前边界已足够清晰。
- 验证：已执行 `./gradlew :core:compileKotlinJvm :core:jvmTest :cli:compileKotlin`，通过。

### P-02 评估 `core` 是否继续保留 KMP

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：已确认 `core` 必须继续保留当前 KMP 结构，这一项不再继续评估 JVM-only 回收方案。
- 影响范围：`core/src/commonMain/`、`core/src/jvmMain/`、`core/build.gradle.kts`
- 完成定义：形成明确结论，且文档与代码结构一致。
- 下一步：后续重构默认以保留 KMP 为前提推进，不再新增“是否回收 JVM-only”的旁路线。
- 验证：方案文档已同步更新为“必须保留 KMP”。

## 待开始

## 进行中

- 当前无。

## 已阻塞

- 当前无。

## 已完成

### T-01 建立 `core` 配置模型

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：已新增 `CoreRuntimeConfig`、`JavaDecompileConfig`、`DexFormatConfig`、`SmaliRenderConfig`、`DexKitRuntimeConfig`，并让 `DexEngine`、`DexSessionLoader`、`DexExportService`、`DexKitRuntime` 开始从这些配置读取默认值。
- 影响范围：`core/src/commonMain/kotlin/io/github/dexclub/core/config/`、`core/src/commonMain/kotlin/io/github/dexclub/core/DexEngine.kt`、`core/src/commonMain/kotlin/io/github/dexclub/core/runtime/DexKitRuntime.kt`、`core/src/jvmMain/`
- 完成定义：配置类已落地到 `commonMain`，JVM 实现开始从这些配置读取默认值，现有 CLI 行为不变。
- 下一步：已完成，后续继续配合 `T-04` 收缩 `DexEngine` 职责。
- 验证：已执行 `./gradlew :core:compileKotlinJvm :cli:compileKotlin`，通过。

### T-02 建立 `core` 结果模型与请求模型

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：已新增 `DexArchiveInfo`、`DexInputRef`、`DexInputKind`、`DexClassHit`、`DexMethodHit`、`DexExportFormat`、`DexExportResult` 以及 `DexExportRequest`、`SmaliExportRequest`、`JavaExportRequest`，并为 `DexEngine` 增加新接口，保留旧接口不删。
- 影响范围：`core/src/commonMain/kotlin/io/github/dexclub/core/model/`、`core/src/commonMain/kotlin/io/github/dexclub/core/request/`、`core/src/commonMain/kotlin/io/github/dexclub/core/DexEngine.kt`、`core/src/jvmMain/`
- 完成定义：`core` 已拥有最小可用的结果模型与导出请求模型，并存在从旧实现结果到新模型的映射路径。
- 下一步：进入 `T-04`，继续把搜索、导出、输入分析从 `DexEngine` 中下沉为更清晰的内部服务。
- 验证：已执行 `./gradlew :core:compileKotlinJvm :cli:compileKotlin`，通过。

### T-03 为搜索与导出补 JVM 测试

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：已新增 `core` 的 JVM 测试，覆盖 `isDex`、`inspect`、旧/新搜索接口，以及 dex、smali、java 导出关键路径。测试输入通过 `javac + d8` 在测试期动态生成，避免提交二进制夹具。
- 影响范围：`core/build.gradle.kts`、`core/src/jvmTest/`
- 完成定义：至少覆盖 `isDex`、搜索、dex 导出、smali 导出、java 导出关键路径，且测试可执行。
- 下一步：后续进入 `T-04` 前，可直接依赖这批测试作为回归保护。
- 验证：已执行 `./gradlew :core:jvmTest`，通过。

### T-04 迁移 `DexEngine` 到 facade 方向

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：已把 `inspect` 与搜索逻辑从 `DexEngine` 下沉到 `DexArchiveInspector`、`DexSearchService`，导出能力继续由 `DexExportService` 承载，当前 `DexEngine` 主要保留组合、兼容入口与生命周期管理职责。
- 影响范围：`core/src/jvmMain/kotlin/io/github/dexclub/core/DexEngine.jvm.kt`、`core/src/jvmMain/kotlin/io/github/dexclub/core/input/`、`core/src/jvmMain/kotlin/io/github/dexclub/core/search/`、`core/src/jvmMain/kotlin/io/github/dexclub/core/export/`
- 完成定义：`DexEngine` 主要承担组合与生命周期管理职责，搜索、导出、输入分析已下沉到更清晰的内部服务。
- 下一步：进入 `P-01`，继续拆分 `DexExportService` 内部职责，把 dex 导出、smali 渲染、jadx 反编译拆得更清晰。
- 验证：已执行 `./gradlew :core:compileKotlinJvm :core:jvmTest :cli:compileKotlin`，通过。

### P-02 评估 `core` 是否继续保留 KMP

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：已确认 `core` 必须继续保留当前 KMP 结构，这一项不再继续评估 JVM-only 回收方案。
- 影响范围：`core/src/commonMain/`、`core/src/jvmMain/`、`core/build.gradle.kts`、项目根目录文档
- 完成定义：形成明确结论，且文档与代码结构一致。
- 下一步：后续重构默认以保留 KMP 为前提推进，不再新增“是否回收 JVM-only”的旁路线。
- 验证：`CORE_REFACTOR_PLAN.md` 与本进度文档已同步更新。

### T-05 迁移 `cli` 到新 API

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：`cli` 已切换为使用 `DexArchiveInfo`、`DexClassHit`、`DexMethodHit`、`DexExportRequest`、`SmaliExportRequest`、`JavaExportRequest` 等 `core` 自有接口，停止依赖 `DexEngine` 的旧透传搜索与导出方法，同时保持现有输出格式不变。
- 影响范围：`cli/src/main/kotlin/io/github/dexclub/cli/Main.kt`
- 完成定义：`cli` 停止直接消费旧透传型搜索/导出接口，并通过 `core` 自有 model/request 完成同等行为。
- 下一步：后续可继续收敛 `DexEngine` 中已不再被仓库内调用使用的旧兼容方法，当前先通过废弃标记缩小使用面。
- 验证：已执行 `./gradlew :core:compileKotlinJvm :cli:compileKotlin :cli:fatJar`，通过。

### T-06 收敛搜索后端与旧兼容接口使用面

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：已新增 `DexKitSearchBackend` 承接 `DexKit` 查询 DSL，`DexSearchService` 不再直接依赖 `DexKitBridge` 查询细节；同时将 `DexEngine` 中旧搜索/导出透传方法标记为废弃，并把仓库内测试主体切换到新 API。
- 影响范围：`core/src/jvmMain/kotlin/io/github/dexclub/core/search/`、`core/src/commonMain/kotlin/io/github/dexclub/core/DexEngine.kt`、`core/src/jvmMain/kotlin/io/github/dexclub/core/DexEngine.jvm.kt`、`core/src/jvmTest/`
- 完成定义：第三方搜索 DSL 已进一步下沉，仓库内不再继续放大旧透传搜索接口的使用面，旧兼容导出/搜索接口已明确进入退场路径。
- 下一步：如果后续决定进入阶段 5，可在确认外部兼容策略后再删除这些旧接口。
- 验证：已执行 `./gradlew :core:compileKotlinJvm :core:jvmTest :cli:compileKotlin`，通过。

### T-07 删除仓库内已完成迁移后的旧公开接口

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：在 `cli` 与仓库内测试均已迁移到 `core` 自有 model/request API 后，已删除 `DexEngine` 中旧的透传公开接口，包括 `getOrCreateBridge`、`readDexNum`、旧搜索接口以及旧单次导出接口，使 `core` 公共边界不再直接暴露 `DexKitBridge`、`ClassData`、`MethodData`。
- 影响范围：`core/src/commonMain/kotlin/io/github/dexclub/core/DexEngine.kt`、`core/src/jvmMain/kotlin/io/github/dexclub/core/DexEngine.jvm.kt`
- 完成定义：旧透传公开接口已从 `core` 公共边界删除，`cli` 与仓库内测试仍能通过当前新 API 正常工作。
- 下一步：后续如需继续收敛，可补充 README 或面向外部使用者的迁移说明，明确当前 `core` 公开 API 以自有 model/request 为准。
- 验证：已执行 `./gradlew :core:compileKotlinJvm :core:jvmTest :cli:compileKotlin :cli:fatJar`，通过。

### D-01 整理 `core` 重构方案文档

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：完成 `core` 重构方案整理，统一了目标边界、目录草案、配置策略、迁移阶段与首批落地项。
- 影响范围：项目根目录文档。
- 完成定义：方案文档已能独立描述重构目标、配置策略、迁移阶段与首批实施项。
- 下一步：按进度依次推进 T-01、T-02、T-03。
- 验证：文档已落地为 `CORE_REFACTOR_PLAN.md`。

### D-02 建立 `core` 重构进度规则

- 状态：`已完成`
- 更新时间：`2026-04-06`
- 说明：补齐了状态定义、状态流转、中断续接、代码与文档冲突处理、未提交改动处理规则。
- 影响范围：项目根目录文档。
- 完成定义：进度文档已能独立描述状态、续接、阻塞与现场恢复规则。
- 下一步：后续每次推进重构前后都应先更新本文件。
- 验证：文档已落地为 `CORE_REFACTOR_PROGRESS.md`。
