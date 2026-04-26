# DexClub CLI V3 领域模型

## 目标

V3 的领域模型需要同时解决三件事：

1. 如何从 `input` 初始化一个工作区
2. 如何从 `workdir` 打开一个已初始化工作区
3. 如何在一个已打开工作区上执行稳定能力

因此 V3 的领域模型围绕以下对象展开：

- `Workdir`
- `WorkspaceRef`
- `WorkspaceContext`
- `TargetHandle`
- `WorkspaceStatus`
- `TargetSnapshotSummary`
- `MaterialInventory`
- `CapabilitySet`

## 一等概念

### Workdir

`Workdir` 是用户可见的工作目录。

职责：

- 承载 `.dexclub`
- 作为非 `init` 命令的定位参数

建议模型：

```kotlin
data class Workdir(
    val path: String,
)
```

### WorkspaceRef

`WorkspaceRef` 表示“如何找到一个工作区”的引用。

它只是输入引用，不代表工作区已经打开。

建议模型：

```kotlin
data class WorkspaceRef(
    val workdir: String,
)
```

### WorkspaceContext

`WorkspaceContext` 表示：

- 一个已打开、可执行能力的工作区上下文
- service / executor 的统一输入
- 工作区的稳定身份与当前 active target 的运行期摘要

建议模型：

```kotlin
data class WorkspaceContext(
    val workdir: String,
    val dexclubDir: String,
    val workspaceId: String,
    val activeTargetId: String,
    val activeTarget: TargetHandle,
    val snapshot: TargetSnapshotSummary,
)
```

约束：

- `WorkspaceContext` 只承载执行所需的最小上下文
- `WorkspaceContext` 不直接承载 `state`
- `WorkspaceContext` 不直接承载 `issues`
- `WorkspaceContext` 不直接承载 `cacheState`

### TargetHandle

`TargetHandle` 表示当前 active target 的身份对象。

建议模型：

```kotlin
enum class InputType {
    File,
}

data class TargetHandle(
    val targetId: String,
    val inputType: InputType,
    val inputPath: String,
)
```

约束：

- `kind` 不属于 target 身份
- `snapshot` 不直接挂在 `TargetHandle` 上

### WorkspaceStatus

`WorkspaceStatus` 表示一次状态检查得到的动态状态视图。

建议模型：

```kotlin
data class WorkspaceStatus(
    val workspaceId: String,
    val activeTargetId: String,
    val state: WorkspaceState,
    val issues: List<WorkspaceIssue>,
    val activeTarget: TargetHandle,
    val snapshot: TargetSnapshotSummary?,
    val cacheState: CacheState,
)
```

约束：

- `healthy`
  - 无 issue
- `degraded`
  - 允许存在 `warning`
  - 不允许存在 `error`
- `broken`
  - 至少存在一个 `error`

`WorkspaceIssue.severity` 首版只收敛为两级：

- `warning`
  - 表示工作区仍可恢复为可执行状态
  - 常见于 `snapshot` 缺失、cache 缺失、需要重建派生状态
- `error`
  - 表示当前工作区已不能正常作为执行上下文使用
  - 常见于 metadata 缺失或损坏、binding 指向的外部输入不存在

`error` 不等于“必须重建工作区”。

更准确地说：

- 需要先修复核心前提
- 若无法修复，再重新 `init`

### TargetSnapshotSummary

`TargetSnapshotSummary` 表示当前 active target 的摘要快照。

建议模型：

```kotlin
data class TargetSnapshotSummary(
    val kind: WorkspaceKind,
    val inventoryFingerprint: String,
    val contentFingerprint: String,
    val capabilities: CapabilitySet,
    val inventoryCounts: InventoryCounts,
)
```

它属于运行期摘要，不属于 target 身份。

## 工作区与绑定

V3 首版只允许一个工作区拥有一个当前绑定目标。

当前绑定目标由以下两部分恢复：

- `.dexclub/workspace.json` 中的 `activeTargetId`
- `.dexclub/targets/<target-id>/target.json` 中的 `inputPath`

这意味着：

- 打开工作区后不再要求用户额外选择 target
- 所有业务命令都围绕当前 active target 运行

## 物料与 kind

### MaterialInventory

`MaterialInventory` 表示当前绑定目标扫描后的物料集合。

建议模型：

```kotlin
data class MaterialInventory(
    val apkFiles: List<String> = emptyList(),
    val dexFiles: List<String> = emptyList(),
    val manifestFiles: List<String> = emptyList(),
    val arscFiles: List<String> = emptyList(),
    val binaryXmlFiles: List<String> = emptyList(),
)
```

### WorkspaceKind

`WorkspaceKind` 是当前物料集合的摘要标签。

建议模型：

```kotlin
enum class WorkspaceKind {
    Apk,
    Dex,
    Manifest,
    Arsc,
    Axml,
}
```

序列化到状态文件时统一使用小写字符串：

- `apk`
- `dex`
- `manifest`
- `arsc`
- `axml`

原则：

- `kind` 由 `MaterialInventory` 推导
- 若 `kind` 与 `inventory` 语义冲突，以 `inventory` 为准

## 能力集合

### CapabilitySet

`CapabilitySet` 表示当前工作区可执行的能力集合。

建议模型：

```kotlin
data class CapabilitySet(
    val inspect: Boolean = true,
    val findClass: Boolean = false,
    val findMethod: Boolean = false,
    val findField: Boolean = false,
    val exportDex: Boolean = false,
    val exportSmali: Boolean = false,
    val exportJava: Boolean = false,
    val manifestDecode: Boolean = false,
    val resourceTableDecode: Boolean = false,
    val xmlDecode: Boolean = false,
    val resourceEntryList: Boolean = false,
)
```

`CapabilitySet` 是：

- 可序列化的
- 可测试的
- 可跨 API / record / DTO 复用的值对象

### InventoryCounts

建议模型：

```kotlin
data class InventoryCounts(
    val apkCount: Int,
    val dexCount: Int,
    val manifestCount: Int,
    val arscCount: Int,
    val binaryXmlCount: Int,
)
```

## 指纹语义

V3 中有两类指纹：

### inventoryFingerprint

表示“识别出的物料集合是否变化”。

建议基于以下有序路径集合计算：

- `apkFiles`
- `dexFiles`
- `manifestFiles`
- `arscFiles`
- `binaryXmlFiles`

### contentFingerprint

表示“当前物料内容是否变化”。

建议基于以下信息计算：

- 物料的相对路径
- 物料内容摘要

## 路径语义

建议严格区分三类路径字段：

### inputPath

- 相对 `workdir` 的输入路径
- 用于标识当前 target 绑定到了哪个外部输入

### sourcePath

- 相对 `workdir` 的外部源路径
- 适用于单源缓存文件
- 不适用于聚合索引文件

### sourceEntry

- 当源输入是容器文件时，表示容器内条目路径
- 例如 APK 内的 `AndroidManifest.xml`

## targetId 语义

`targetId` 表示路径身份，不表示内容身份。

建议规则：

- `sha256("file\0" + inputPath)`

这意味着：

- 同一路径上的内容变化不应导致 `targetId` 变化
- 内容变化应通过 `contentFingerprint` 反映
- snapshot 与 cache 失效由内容指纹驱动，而不是由 targetId 驱动

## 持久化与派生状态

### WorkspaceMetadata

`.dexclub/workspace.json` 只记录工作区根身份与当前 active target。

建议模型：

```kotlin
data class WorkspaceMetadata(
    val schemaVersion: Int,
    val layoutVersion: Int,
    val workspaceId: String,
    val createdAt: String,
    val updatedAt: String,
    val toolVersion: String,
    val activeTargetId: String,
)
```

### DerivedState

`DerivedState` 指从当前 binding 物料派生出来、值得缓存的中间产物。

例如：

- manifest 解码结果
- 资源表解析结果
- class source 映射
- 资源条目索引

约束：

- 可从原始输入重建
- 删除后不影响工作区身份
- 不应被视为不可替代的真相

## 领域边界

当前阶段建议将领域边界收敛为：

- `WorkspaceStore`
  - 持久化 `.dexclub` 受管结构
- `WorkspaceRuntimeResolver`
  - 恢复运行态上下文与状态视图
- `InventoryScanner`
  - 扫描当前绑定物料
- `CapabilityResolver`
  - 从 `MaterialInventory` 推导 `CapabilitySet`
- `SnapshotBuilder`
  - 生成 `TargetSnapshotSummary`
- capability services
  - 在 `WorkspaceContext` 上执行稳定能力

