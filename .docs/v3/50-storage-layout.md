# DexClub CLI V3 存储布局

## 目标

V3 需要一个能承载以下职责的工作区存储布局：

- workspace 身份信息
- 当前激活 target
- target 级状态隔离
- snapshot
- derived state

同时要求：

- `.dexclub` 只落在 workdir 下
- metadata 与重型派生文件分离
- 能按 target 与 snapshot 判断缓存是否可复用

本文件只负责目录结构与职责边界。

各状态文件的字段结构与示例，见：

- [51-state-file-schemas.md](./51-state-file-schemas.md)

## 根目录

状态根固定为：

```text
<workdir>/.dexclub/
```

其中：

- `workdir = input.parent`

## 建议布局

```text
<workdir>/
  .dexclub/
    workspace.json
    targets/
      <target-id>/
        target.json
        snapshot.json
        cache/
          decoded/
          indexes/
          exports/
            tmp/
```

## 审阅边界

建议将 `.dexclub` 的设计拆成两层审阅：

1. 本文件
   - 看目录结构是否合理
   - 看职责边界是否清晰
2. `51-state-file-schemas.md`
   - 看每个文件的字段结构是否稳定
   - 看路径字段、指纹字段和 payload 包装是否合理

## 文件职责

### `workspace.json`

工作区根 metadata。

建议保存：

- schema version
- layout version
- workspace id
- created / updated at
- tool version
- active target id

工作区根不重复保存完整 binding 详情。

### `targets/<target-id>/target.json`

当前 target 的身份信息。

建议保存：

- input path
- input type
- target id
- created / updated at

### `targets/<target-id>/snapshot.json`

当前 target 的稳定快照。

建议保存：

- input path
- target id
- inventory fingerprint
- content fingerprint
- capabilities
- kind

### `targets/<target-id>/cache/decoded/`

解码类派生产物，例如：

- manifest 解码结果
- arsc 解码结果
- xml 解码结果

这类内容偏“可直接理解的解码结果”，通常更接近最终可展示内容。

### `targets/<target-id>/cache/indexes/`

索引类派生产物，例如：

- source dex 定位辅助索引
- 资源条目索引

其中 class source map 主要服务于：

- `export-class-dex`
- `export-class-smali`
- `export-class-java`

这类内容偏“内部加速结构”，通常不直接面向最终用户展示。

### `targets/<target-id>/cache/exports/tmp/`

导出链内部使用的一次性中间产物目录，例如：

- `export-class-java` / `export-method-java` 生成的临时单类 dex
- `jadx` 反编译过程中写出的临时输出目录

约束：

- 仅承载命令执行期的中间产物
- 不作为稳定状态文件目录
- 命令完成后应尽量清空，不向用户输出目录泄露临时文件

## target id

V3 首版建议：

- `target-id = sha256("file\0" + inputPath)`

其中：

- 输入字符串按 UTF-8 编码
- 哈希算法使用 SHA-256
- 输出统一为小写十六进制字符串

这里的文件 target id：

- 不是文件内容 hash
- 不是绝对路径 hash
- 只用于在当前 workdir 下标识该文件 target

建议理解为：

- `target-id` 表示“当前 workdir 下这个输入路径是谁”
- `contentFingerprint` 表示“这个输入路径当前的内容是什么”

因此：

- 同名文件内容变化，不应改变 `target-id`
- 但应改变 `contentFingerprint`

这套规则建立在：

- 一个工作区只有一个当前 binding
- `init` 只接受单个文件输入

的前提下。

## key 设计

derived state 不应只按 workspace id 组织，而应按：

- `target-id`
- `inventory fingerprint`
- `content fingerprint`

组织。

建议模型：

```kotlin
data class DerivedStateKey(
    val schemaVersion: Int,
    val layoutVersion: Int,
    val toolVersion: String,
    val targetId: String,
    val inventoryFingerprint: String,
    val contentFingerprint: String,
)
```

建议口径：

- `inventoryFingerprint`
  - 基于已识别物料路径集合计算
- `contentFingerprint`
  - 基于这些物料的路径 + 内容摘要计算

## 重建策略

以下任一变化都应导致 derived state 失效或重建：

- schema version 变化
- layout version 变化
- tool version 变化
- active target 变化
- inventory fingerprint 变化
- content fingerprint 变化

cache 命中失败时，只应影响性能与复用效果，不应改变命令语义。

也就是说：

- cache 可以删
- 删掉后工作区仍应可恢复
- 恢复方式是重新扫描、重新解码、重新建索引

## 外部物料策略

V3 首版建议先把外部物料视为原始绑定源，而不是默认复制进 `.dexclub`。

也就是说：

- metadata 记录外部绑定路径
- 外部物料被删除后，工作区可进入不可用状态
- `status` 负责显式报告

后续如需控制磁盘占用与可用性，可以再设计复制或门机制。

## Cache 准入规则

适合进入 cache 的内容：

- manifest / arsc / binary xml 的解码结果
- dex 导出辅助索引
- 资源入口索引

不适合默认进入 cache 的内容：

- `workspace.json`
- `target.json`
- `snapshot.json`
- 原始输入文件副本
- 一次性命令的最终 stdout 文本
- 无明确复用价值的临时文件

换句话说：

- 身份信息进 metadata
- 当前摘要进 snapshot
- 可重建且值得复用的派生产物进 cache

## 原始输入副本策略

V3 首版明确不做：

- 把 `apk` 复制进 `.dexclub`
- 把 `dex` 复制进 `.dexclub`
- 把 `manifest` / `arsc` / xml 原始输入复制进 `.dexclub`

也就是说：

- `cache` 不保存原始输入副本
- 工作区当前 binding 仍然依赖外部原始输入
- 外部输入被删除后，工作区可以进入不可用状态

如果未来需要支持“原始输入被删除后仍可继续工作”，那应引入独立的：

```text
materials/
```

设计，而不是把原始输入副本塞进 `cache/`。

