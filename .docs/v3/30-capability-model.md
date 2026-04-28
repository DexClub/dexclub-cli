# DexClub CLI V3 能力模型

## 目标

V3 的能力模型用于回答一个统一问题：

`当前工作区上下文能执行哪些能力`

能力模型必须：

- 来源单一
- 规则稳定
- 可序列化
- 可测试
- 不散落在命令适配层

## 能力集合

V3 首版建议公开以下能力字段：

- `inspect`
- `findClass`
- `findMethod`
- `findField`
- `exportDex`
- `exportSmali`
- `exportJava`
- `manifestDecode`
- `resourceTableDecode`
- `xmlDecode`
- `resourceEntryList`

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

## 基本原则

### 1. 能力来源于物料

能力必须由 `MaterialInventory` 推导，不允许散落在命令层临时判断。

### 2. 能力字段服务于运行期 gate

每个业务能力都必须映射到一个清晰的 capability。

### 3. 能力集合允许比 kind 更细

例如：

- `apk` 会同时支持 dex、manifest、resource 相关能力
- `manifest` 只支持 manifest/xml 相关能力
- `arsc` 只支持资源表和值解析相关能力

### 4. 不支持必须显式失败

任何能力在 capability 关闭时都必须明确失败。

## 推导规则

### dex 侧能力

若 inventory 中存在以下任一物料：

- `apk`
- `dex`

则开启：

- `findClass`
- `findMethod`
- `findField`
- `exportDex`
- `exportSmali`
- `exportJava`

### manifest 解码

若 inventory 中存在以下任一物料：

- `apk`
- `AndroidManifest.xml`

则开启：

- `manifestDecode`

### 资源表解码

若 inventory 中存在以下任一物料：

- `apk`
- `resources.arsc`

则开启：

- `resourceTableDecode`

### XML 解码

若 inventory 中存在以下任一物料：

- `apk`
- `AndroidManifest.xml`
- 二进制 xml

则开启：

- `xmlDecode`

### 资源入口列举

若 inventory 中存在以下任一物料：

- `apk`

则开启：

- `resourceEntryList`

说明：

- 首版 `resourceEntryList` 仅对 APK 工作区成立
- standalone `resources.arsc` 工作区只开启 `resourceTableDecode`，不开启 `resourceEntryList`

### inspect

`inspect` 始终开启。

## 示例矩阵

| kind | inspect | dex 查询/导出 | manifest | arsc | xml | list res |
| --- | --- | --- | --- | --- | --- | --- |
| `apk` | yes | yes | yes | yes | yes | yes |
| `dex` | yes | yes | no | no | no | no |
| `manifest` | yes | no | yes | no | yes | no |
| `arsc` | yes | no | no | yes | no | no |
| `axml` | yes | no | no | no | yes | no |

## 公开操作

建议用 `Operation` 表示 service 层的运行期操作，而不是直接用 CLI 命令字符串。

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
```

命令到 operation 的映射：

- `inspect` -> `Inspect`
- `find-class` -> `FindClass`
- `find-method` -> `FindMethod`
- `find-field` -> `FindField`
- `find-class-using-strings` -> `FindClass`
- `find-method-using-strings` -> `FindMethod`
- `export-class-dex` -> `ExportDex`
- `export-method-dex` -> `ExportDex`
- `export-class-smali` -> `ExportSmali`
- `export-class-java` -> `ExportJava`
- `export-method-smali` -> `ExportSmali`
- `manifest` -> `ManifestDecode`
- `res-table` -> `ResourceTableDecode`
- `decode-xml` -> `XmlDecode`
- `list-res` -> `ResourceEntryList`
- `resolve-res` -> `ResourceTableDecode`
- `find-res` -> `ResourceTableDecode`

## 统一 gate

capability gate 应统一放在 service 层。

建议内部增加：

- `CapabilityChecker`

推荐形态：

```kotlin
class CapabilityChecker {
    fun require(workspace: WorkspaceContext, operation: Operation)
}
```

规则：

- `Operation -> Capability` 映射集中在 checker 内部
- `CapabilityChecker` 只依赖 `WorkspaceContext.snapshot.capabilities`
- 失败时抛结构化 `CapabilityError`

例如：

```kotlin
data class CapabilityError(
    val operation: Operation,
    val requiredCapability: String,
    val kind: String,
) : RuntimeException()
```

CLI 层再把领域错误渲染成最终英文终端提示。

