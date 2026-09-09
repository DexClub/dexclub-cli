# DexClub V4 构建与交付

> 本稿是当前构建与交付边界说明。
> 它回答“现在这套结构应如何理解和维护”，不是继续拆供应链的施工清单。
> 如果需要看具体 native 维护步骤，优先配合 [../native-maintenance.md](../native-maintenance.md) 一起读。

## 目标

本稿只回答下面几个问题：

- `v4` 下构建链应该收口到什么程度
- `dexkit / native / vendor` 各自处于什么位置
- 哪些前提应成为主仓默认要求
- 哪些前提不应继续泄漏到所有开发路径

这是一份当前状态说明，不是迁移草案；当前稳定边界见 [stabilization.md](./stabilization.md)。

## 当前判断

现在更准确的说法是：

- 普通开发路径已经可以和 native 维护路径分开看
- 入口交付路径已经可以和供应链维护路径分开看
- 还需要继续做的是文档收口与边界诚实表达，而不是再造一套新的构建结构

同时要带着一个当前前提来读：

- 根工程当前主要承接 `app-service / domain-core / cli-app / mcp-app`
- `dexkit-binding` 作为独立组合构建承接绑定层与 native 维护复杂度

## 基本原则

按下面几条原则收口：

- 普通共享层开发路径应尽量轻
- native 维护路径可以复杂，但必须显式
- vendor 依赖可以保留，但职责边界必须清楚
- 主仓 README 只保留高频入口
- 环境特例不能伪装成通用前提

## 三类构建路径

构建和交付路径应明确区分成三类：

### 1. 共享层开发路径

面向：

- `domain-core`
- `app-service`
- 一部分 `cli`
- 一部分 `mcp`

这条路径应尽量满足：

- 不需要理解完整 native 供应链
- 不需要维护 vendor 仓细节
- 不需要默认处理多套 NDK / cmake 组合

### 2. 入口层交付路径

面向：

- CLI 打包
- MCP 打包

这条路径需要：

- 能消费共享层产物
- 能带上运行所需 native library
- 能产出可分发物

但不一定要求每次都从头重建全部 native 依赖。

### 3. native / vendor 维护路径

面向：

- DexKit 绑定维护
- `libcxx` prefab 维护
- vendor 同步与兼容调整

这条路径允许复杂。

但它必须被明确标记为：

- 维护链
- 特殊路径
- 非所有开发者的日常路径

## `dexkit-binding` 的位置

`dexkit-binding` 应被理解成绑定层，而不是产品主结构中心。

这意味着：

- `dexkit-binding` 负责消费或桥接底层分析引擎能力
- `dexkit-binding` 的构建复杂度不应无条件外溢到所有上层模块
- `dexkit-binding` 的平台和 native 细节不应泄漏到应用层和入口层接口

但当前真实状态还要补一条：

- `cli-app` 的打包脚本仍直接消费 `dexkit-binding/vendor/DexKit` 下的 native 产物
- 这属于当前交付链的现实约束，不能写成“已经完全隔离”

## vendor 的位置

vendor 可以保留，但要明确它的角色：

- vendor 是供应链输入的一部分
- vendor 不是普通业务开发者的主要工作面
- vendor 目录中的复杂度不应成为所有模块日常开发的默认心智负担

这意味着文档和结构上都应能让人一眼区分：

- 哪些改动是在改产品
- 哪些改动是在维护上游绑定链

## `mavenLocal()` 的问题

当前仓库的 native 维护链仍保留 `mavenLocal()` 相关步骤。

但要把事实说准确：

- `dexkit-binding/vendor/libcxx-prefab` 当前发布的是 `dev.rikka.ndk.thirdparty:libcxx:1.3.0`
- vendored DexKit Android 当前实际编译依赖声明是 `dev.rikka.ndk.thirdparty:cxx:1.2.0`
- 两者不是同一个坐标，所以当前看不到直接的版本冲突
- 现阶段更像是“维护链保留了本地发布与分发步骤”，而不是“正常 Android 编译类路径已经直接依赖 `libcxx:1.3.0`”

这本身不是原罪，问题在于它仍然容易让构建链边界变得模糊：

- 哪些产物应该由主仓自行提供
- 哪些产物只是本机状态
- 某次构建成功到底依赖了仓库内容，还是依赖了机器遗留状态

`v4` 当前更适合把这件事文档化和显式化，而不是先假装彻底去掉。

先要做到的是：

- 明确哪些路径只是执行了 `mavenLocal()` 发布步骤
- 明确哪些路径真的解析了这些本地产物
- 明确为什么需要
- 明确缺失时应该怎么准备
- 不把这类前提扩散成所有改动的默认要求

## 收口方向

按下面方向收口：

### 1. 共享层改动应尽量避开 native 前提

如果一个改动只涉及：

- 领域模型
- 应用层用例
- CLI parser / renderer
- MCP schema / result mapping

那就不应默认要求开发者先准备完整 native 维护环境。

### 2. 入口打包要区分“消费产物”和“重建产物”

CLI 和 MCP 的交付路径应优先表达：

- 我需要哪些运行时文件
- 这些文件从哪里来
- 哪些情况下只消费已有产物
- 哪些情况下才需要重建 native 产物

当前仓库里，这件事的真实落点是：

- `cli-app` 已经把“消费已有 native 产物”和“从 vendored DexKit 拷贝 native 产物”分成两条路径
- 但这条路径仍然写在 `cli-app` 打包脚本里，而不是完全下沉到独立供应链模块

### 3. native 维护要有独立说明

涉及：

- NDK
- cmake
- ninja
- `libcxx`
- vendor DexKit

这类内容，应更明确地归入维护说明，而不是散在所有高层入口文档里。

## 验证基线

对当前仓库来说，普通改动优先验证：

- `./gradlew verifyFast`
- `./gradlew :app-service:testStructured`
- `./gradlew :cli-app:testStructured`
- `./gradlew :mcp-app:testStructured`
- `./gradlew :domain-core:testWorkspace`
- `./gradlew :domain-core:compileKotlinJvm`

需要 native 维护路径时，再额外进入对应专项命令。

## 交付形态

继续区分两类正式交付：

### CLI 交付

关注点是：

- 可执行 jar 或分发目录
- native library 随包方式
- 启动脚本
- 最小运行前提

### MCP 交付

关注点是：

- 可执行分发目录
- native library 随包方式
- 运行时日志与诊断目录
- 面向宿主进程的启动前提

这两类交付都应尽量做到：

- 运行前提清楚
- 打包产物边界清楚
- 与 native 维护链区分清楚

## 统一构建元数据

当前实现已经将 CLI、MCP 和 `domain-core` 的版本来源收口到根工程解析的统一构建元数据。
各模块仍使用各自的生成 `BuildInfo` 类，发行目录则携带同一格式的 `VERSION` 文件；这表示
它们共享同一组构建输入，不要求新增一个运行时公共模块。

构建元数据至少包含：

- `version`
- `mcpContractVersion`
- `commit`
- `dirty`

根工程的解析优先级为：显式 `-PreleaseVersion`、本地 Git 开发版本、`dev-unknown`；commit
优先使用显式 `-PreleaseCommit`，再回退到 `git rev-parse HEAD` 和 `unknown`；dirty 优先使用
显式 `-PreleaseDirty`，再读取 `git status --porcelain`，无 Git 时默认为 `false`。正式版本会
去除单个前导 `v`，非法版本、非法完整 SHA、非法 dirty 值和正式版本 dirty 状态都会使构建失败。
正式版本与开发版本均由同一份 `DexClubBuildMetadata` 提供给 CLI、MCP、domain-core、发行包
和版本化 skill；`MCP_CONTRACT_VERSION` 是根工程维护的固定合同版本，不从 tag 或 commit 推导。

本地没有显式发行版本时，Gradle 根据当前 Git 状态生成开发版本，例如
`dev-<shortCommit>` 或 `dev-<shortCommit>-dirty`。因此本地编译、测试和直接启动 MCP 不依赖
GitHub Actions。正式构建由 CI 显式传入版本、完整 commit 和 clean 状态，并在构建前校验
checkout 与这些输入一致。

MCP 发行目录中的 skill 不是直接复制仓库模板，而是由同一次构建生成并写入对应版本。业务
tool 调用要求调用方声明该版本；`get_server_info` 用于启动后的版本诊断和本地联调。若本地
使用 Agent skill，需要先生成并同步当前构建对应的 skill 副本，否则旧 skill 与当前 MCP
不匹配时会被拒绝。

CLI/MCP 的发行 workflow 接收统一的版本、完整 commit 和 clean 状态输入，并在聚合 job 中解包
最终 zip，校验每个包恰好包含一份 `VERSION`，且 CLI 与 MCP 的完整元数据逐字段一致。MCP 包还
必须包含已经替换占位符的 `dexclub-analysis` skill；CI 不会在 Gradle 构建后重新推导或改写版本。

构建元数据测试位于 `buildSrc/src/test`，覆盖解析校验、Git/dirty/unknown 回退、生成内容和
字节稳定性；Gradle TestKit 进一步验证版本或模板输入变化时无需 `clean` 即重新生成，验证入口
为 `./gradlew :buildSrc:test`，并已接入 CI。当前本地 skill 同步仍使用生成任务加手动复制命令，
自动写入 `$CODEX_HOME/skills` 暂不实现，以避免跨平台路径和覆盖策略扩大构建任务职责。

本节记录当前已落地的边界和开发入口。未来扩展 GUI、发布协调或兼容策略时，继续复用根工程
metadata provider，并按平台生成对应的 BuildInfo；不应恢复模块各自解析版本的实现。

## CI 的位置

CI 应至少承担两类职责：

### 1. 普通验证

面向：

- 共享层编译
- `cli` 测试
- `mcp` 测试

它应该尽量覆盖高频改动路径。

### 2. native / Android 维护验证

面向：

- `dexkit-binding` 绑定
- Android 构建链
- vendor 相关维护

它允许更重，但应作为明确的专项验证路径存在。

## 文档收口

说明应拆成三层：

### README

只保留：

- 项目概览
- 高层模块说明
- 最常用构建命令
- 常用运行方式

### `.docs/v4/`

承接：

- 结构设计
- 迁移路径
- 构建边界

### 维护说明

单独承接：

- native 维护
- vendor 更新
- 本地发布 `mavenLocal()` 的准备流程

当前仓库已经补出的落点：

- [../native-maintenance.md](../native-maintenance.md)

## 仍待讨论

下面这些点还可以继续讨论，但不影响当前普通开发路径：

- `mavenLocal()` 是长期保留，还是逐步收缩
- `libcxx` 产物未来更适合怎样消费
- `dexkit-binding` 绑定层是否应进一步独立维护
- CI 是否应进一步拆成共享层与 native 维护两条主线
- 哪些环境前提可以从 README 下沉到专项维护文档

## 当前结论

`v4` 在构建和交付上的目标不是“把复杂度删除”，而是：

- 把复杂度放在正确层级
- 让普通开发路径和维护路径分离
- 让入口交付路径和 native 供应链分离
- 让文档表达和真实职责一致
