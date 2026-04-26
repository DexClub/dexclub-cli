# DexClub CLI V3 Core API 设计草案

## 目标

本草案只回答 4 件事：

1. `core` 对外暴露哪些稳定能力
2. `cli` 如何围绕这些能力组织命令适配层
3. `core` 如何保持去 CLI 化
4. 默认实现如何装配

对象模型的详细字段定义见：

- `10-domain-model.md`

执行流与命令装配见：

- `60-execution-flow.md`
- `70-pseudocode.md`

实现阶段的红线、顺序与自查清单见：

- `62-implementation-guardrails.md`
- `63-p0-delivery-order.md`
- `64-implementation-checklist.md`

## 总体原则

1. `core` 是能力库，不是 CLI 命令镜像层
2. `cli` 是命令应用层，负责解析、适配、渲染与输出
3. `core` 不被 `cli` 的命令形态反向约束
4. `cli` 应围绕 `core` 的能力接口组织命令适配层

## 去 CLI 化约束清单

后续所有 `core` 设计都应对照以下约束：

1. `core` 不接受 `CliRequest`
2. `core` 不接受 `--query-json / --query-file` 这种 CLI 二选一形态
3. `core` 不产出 text/json 字符串
4. `core` 不感知 stdout/stderr、help、usage、退出码
5. `core` 不直接产出终端用户文案
6. `core` 不使用 `Cli*`、`Command*`、`Help*` 这类 CLI 命名作为公共 API
7. `core` 保留工作区概念，但工作区不属于 CLI 私有能力
8. `core` 对外暴露能力接口，而不是 CLI 命令镜像
9. 磁盘 DTO 不外露给上层
10. 默认实现不等于公共 API，`cli` 不直接依赖 `impl.*`

## 工作区概念

`core` 中仍然应存在工作区概念。

原因：

- `.dexclub` 的结构契约、snapshot、cache、status、gc 都属于核心领域能力
- 若将工作区完全下放到 `cli`，后续 GUI 或其它调用方会被迫重写一遍工作区逻辑

但 `core` 不应只有工作区这一种入口。

建议将 `core` 划分为两层：

1. 目标/能力层
   - 面向 inspect、search、export、resource decode 等能力
2. 工作区层
   - 面向 `.dexclub`、active target、status、gc、snapshot、cache

## 工作区扩展区

`core` 只管理受管结构：

```text
.dexclub/
  workspace.json
  targets/
```

同时为上层调用方预留消费者自管区域：

```text
.dexclub/
  workspace.json
  targets/
  consumers/
    <consumer-id>/
```

约束：

- `core` 不读取 `consumers/` 下的数据
- `core` 不迁移 `consumers/` 下的数据
- `core` 不校验 `consumers/` 下的数据
- `core` 不清理 `consumers/` 下的数据

## 公开对象

当前阶段公开对象收敛为：

- `WorkspaceRef`
- `WorkspaceContext`
- `TargetHandle`
- `WorkspaceStatus`
- `TargetSnapshotSummary`
- `WorkspaceState`
- `WorkspaceIssue`
- `WorkspaceIssueSeverity`
- `CacheState`
- `InventoryCounts`
- `CapabilitySet`
- `PageWindow`
- `SourceLocator`

这些对象的详细字段与边界定义见 `10-domain-model.md`。

## 对外能力分组

建议 `core` 对外只公开 3 个主 service：

- `WorkspaceService`
- `DexAnalysisService`
- `ResourceService`

不建议对外再暴露一个总超级门面。

### WorkspaceService

负责：

- 初始化工作区
- 打开工作区
- 读取工作区状态
- 清理当前 active target 的 cache
- 输出 active target 摘要

建议公开方法：

- `initialize`
- `open`
- `loadStatus`
- `gc`
- `inspect`

### DexAnalysisService

负责：

- dex 查询
- using-strings 查询
- class 导出
- method smali 导出

建议公开方法：

- `findClasses`
- `findMethods`
- `findFields`
- `findClassesUsingStrings`
- `findMethodsUsingStrings`
- `exportClassDex`
- `exportClassSmali`
- `exportClassJava`
- `exportMethodSmali`

### ResourceService

负责：

- manifest 解码
- resource table 读取
- XML 解码
- 资源条目列举
- 资源值解析
- 资源值反查

建议公开方法：

- `decodeManifest`
- `dumpResourceTable`
- `decodeXml`
- `listResourceEntries`
- `resolveResourceValue`
- `findResourceEntries`

## service 形态

建议：

- 对外公开 `interface`
- 内部提供默认实现

例如：

- `WorkspaceService`
- `DefaultWorkspaceService`
- `DexAnalysisService`
- `DefaultDexAnalysisService`
- `ResourceService`
- `DefaultResourceService`

约束：

- 公开接口数量控制在这 3 个主 service
- 不把 inventory、snapshot、smali exporter、resource resolver 等内部依赖都变成公共接口

## 内部层次

建议 `core` 内部实现按以下四层组织：

1. `store`
2. `runtime`
3. `engine-adapter`
4. `service-impl`

### store

职责：

- 读写 `.dexclub` 受管结构
- 读写 `workspace / target / snapshot`
- 清理当前 active target 的 cache

### runtime

职责：

- 打开工作区
- 恢复 active target
- 扫描当前 binding 物料
- 推导 capability / kind / snapshot
- 生成 `WorkspaceContext / WorkspaceStatus`

### engine-adapter

职责：

- 贴近 DexEngine / JADX / resource parser
- 执行 dex 查询、导出、manifest/resource/xml 解析

约束补充：

- `jadx` 保留给 Java 导出等反编译能力
- manifest / binary xml / resource table 不应以 `jadx` 作为主解析路径
- 资源线应使用独立的 resource parser backend

### service-impl

职责：

- 组合 store / runtime / executors
- 提供稳定能力语义
- 返回稳定结果对象

## 关键内部组件

### WorkspaceStore

`WorkspaceStore` 是 `.dexclub` 的唯一正式读写入口。

职责：

- 维护 `workspace / target / snapshot`
- 清理当前 active target cache
- 不负责 inventory 扫描
- 不负责 capability 推导
- 不负责 dex/resource 业务执行

### WorkspaceRuntimeResolver

职责：

- `open(workdir) -> WorkspaceContext`
- `loadStatus(workspaceRef) -> WorkspaceStatus`
- `refreshSnapshot(workspace) -> TargetSnapshotSummary`

关键约束：

- `open()` 返回可执行的 `WorkspaceContext`
- `open()` 允许刷新并回写 `snapshot`
- `loadStatus()` 不依赖 `open()` 成功
- `loadStatus()` 只读，不修复、不刷新
- `refreshSnapshot()` 是显式运行态刷新入口

### executors

建议最小 executor 集合收敛为：

- `DexSearchExecutor`
- `DexExportExecutor`
- `ManifestExecutor`
- `ResourceTableExecutor`
- `XmlExecutor`
- `ResourceValueExecutor`

职责边界：

- executor 负责底层执行
- service 负责 capability gate、稳定排序、分页、稳定语义

## record / dto / API object 分层

建议严格区分三层：

1. 公共 API 对象
   - `WorkspaceContext`
   - `WorkspaceStatus`
   - `TargetHandle`
   - `TargetSnapshotSummary`
2. store record
   - `WorkspaceRecord`
   - `TargetRecord`
   - `SnapshotRecord`
3. 磁盘 DTO
   - `WorkspaceDto`
   - `TargetDto`
   - `SnapshotDto`

原则：

- DTO 只负责序列化
- Record 只负责持久化语义
- API 对象只负责对外能力语义

`CapabilitySet` 与 `InventoryCounts` 可以跨 record / DTO / API 三层复用。

## Capability gate

capability gate 应统一放在 service 层，不散落到 executor。

建议增加一个内部组件：

- `CapabilityChecker`

推荐形态：

```kotlin
enum class Operation {
    Inspect,
    FindClass,
    FindMethod,
    FindField,
    ExportDex,
    ExportSmali,
    ExportJava,
    ManifestDecode,
    ResourceTableDecode,
    XmlDecode,
    ResourceEntryList,
}

class CapabilityChecker {
    fun require(workspace: WorkspaceContext, operation: Operation)
}
```

规则：

- `Operation -> Capability` 映射集中在 checker 内部
- 失败时抛 `CapabilityError(operation, requiredCapability, kind)`
- `cli` 再把领域错误渲染成英文终端提示

## 默认装配入口

`core` 应提供一个很薄的默认装配入口，而不是让 `cli` 自己手动 new 一堆 `Default*`。

建议：

```kotlin
data class Services(
    val workspace: WorkspaceService,
    val dex: DexAnalysisService,
    val resource: ResourceService,
)

fun createDefaultServices(): Services
```

内部装配关系建议为：

```text
WorkspaceStore
+ WorkspaceRuntimeResolver
-> DefaultWorkspaceService

DexSearchExecutor
+ DexExportExecutor
+ ResultSorter
+ WindowApplier
-> DefaultDexAnalysisService

ManifestExecutor
+ ResourceTableExecutor
+ XmlExecutor
+ ResourceValueExecutor
+ ResultSorter
+ WindowApplier
-> DefaultResourceService
```

## cli command adapter 边界

`command adapter` 是 CLI 与 `core` 之间唯一允许同时理解两边语义的薄层。

允许：

1. 接收 `CliRequest`
2. 做 CLI 专属输入归一化
3. 打开工作区
4. 构造 `core` request
5. 调用 capability service
6. 把结果交给 renderer

禁止：

1. 直接操作 `.dexclub` 文件
2. 直接碰 DexEngine / JADX / resource parser
3. 直接做 text/json 渲染
4. 直接决定最终终端错误模板
5. 自己装配默认实现
6. 绕过 `consumers/<consumer-id>/` 写扩展数据
7. 复制 capability / 排序 / 分页等核心规则

## 包骨架建议

模块目录当前收敛为 `core / cli / dexkit`，包名与类名不带版本号。

建议源码结构：

```text
core/
  src/
    commonMain/
    jvmMain/
    commonTest/
    jvmTest/
```

### commonMain

建议包：

- `io.github.dexclub.core.api.workspace`
- `io.github.dexclub.core.api.dex`
- `io.github.dexclub.core.api.resource`
- `io.github.dexclub.core.api.shared`

### jvmMain

建议包：

- `io.github.dexclub.core.impl.workspace`
- `io.github.dexclub.core.impl.dex`
- `io.github.dexclub.core.impl.resource`
- `io.github.dexclub.core.impl.shared`

## cli 默认装配

`cli` 与 `core` 的连接建议收敛为：

```text
main
-> createDefaultServices()
-> create adapters
-> CliParser
-> CommandDispatcher
-> CommandAdapter
-> core Services
-> Renderer
-> OutputWriter
```

约束：

- `main` 保持极薄
- `CommandDispatcher` 负责 `CliRequest -> adapter` 分发
- `cli` 只通过 `Services` 和 `core.api.*` 与 `core` 交互
- `cli` 不直接依赖 `core.impl.*`

