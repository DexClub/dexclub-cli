# DexClub CLI V3 状态文件 Schema 草案

## 目标

本文件专门定义 `.dexclub` 内各状态文件的内容结构。

这里关注的是：

- 每个文件的职责
- 每个字段的含义
- 哪些字段属于稳定契约
- 哪些字段属于可演化 payload

本文件不重复讨论目录结构本身，目录总览见：

- [50-storage-layout.md](./50-storage-layout.md)

## 路径字段约定

`.dexclub` 内所有路径字段统一采用以下规则：

1. 默认使用相对 `workdir` 的路径
2. 分隔符统一为 `/`
3. 不允许出现 `..`
4. 不允许写入 `.dexclub` 内部路径作为业务输入路径

示例：

- 文件初始化
  - `cli init D:/abc/app.apk`
  - `inputPath = "app.apk"`

## target id 约定

### 文件输入

文件输入建议使用：

```text
targetId = sha256("file\0" + inputPath)
```

规则如下：

1. 输入字符串按 UTF-8 编码
2. 哈希算法为 SHA-256
3. 输出为小写十六进制字符串

注意：

- 这不是文件内容 hash
- 这不是绝对路径 hash
- 这只是当前 `workdir` 下的文件 target identity

建议理解为：

- `targetId`
  - 表示路径身份
- `contentFingerprint`
  - 表示当前内容身份

因此：

- 同一路径文件被替换为另一个 dex / apk
  - `targetId` 不变
  - `contentFingerprint` 应变化
  - snapshot 和 cache 应失效并重建

## kind 枚举

`snapshot.json.kind` 建议固定为以下小写字符串枚举：

- `apk`
- `dex`
- `manifest`
- `arsc`
- `axml`

`kind` 的职责是：

- 表达当前扫描摘要类型

`kind` 不是 target 身份字段，因此不进入 `target.json`。

## 通用字段约定

以下字段命名建议在所有 JSON 文件中尽量统一：

- `schemaVersion`
  - 当前文件的 JSON 结构版本
- `generatedAt`
  - 当前文件对应结果的生成时间
- `createdAt`
  - 当前实体首次创建时间
- `updatedAt`
  - 当前实体最后更新时间
- `targetId`
  - 当前文件归属的 target
- `format`
  - 某类 payload 的内部格式名

## 指纹字段约定

### `inventoryFingerprint`

表示“识别出的物料集合是否变化”。

建议基于以下有序路径集合计算：

- `apkFiles`
- `dexFiles`
- `manifestFiles`
- `arscFiles`
- `binaryXmlFiles`

也就是说，它描述的是：

- 当前识别出的物料集合是否变化

### `contentFingerprint`

表示“当前物料内容是否变化”。

建议基于以下信息计算：

- `inventory` 中每个物料的相对路径
- 每个物料的内容摘要

也就是说，它描述的是：

- 当前物料内容是否变化

## 容器路径字段约定

建议统一区分三类路径字段：

### `inputPath`

- 相对 `workdir` 的输入路径
- 用于表示当前 target 绑定到了哪个外部输入

### `sourcePath`

- 相对 `workdir` 的源输入路径
- 用于表示某个 cache 文件来自哪个外部输入文件

`sourcePath` 只用于单源缓存文件。

### `sourceEntry`

- 当源输入是容器文件时，表示容器内条目路径
- 例如 APK 内的 `AndroidManifest.xml`

`sourceEntry` 只描述容器内路径，不描述文件系统路径。

它只在“单源缓存文件来自容器输入”时出现。

## 1. `workspace.json`

### 职责

`workspace.json` 只表达工作区根级别的身份与当前激活 target。

它不表达：

- 完整 binding 详情
- 重型分析结果
- manifest / xml / arsc 正文
- 索引正文

### 建议结构

```json
{
  "schemaVersion": 1,
  "layoutVersion": 1,
  "workspaceId": "3d2f6c4a-7b55-4d22-a6fd-7d8a45f1b321",
  "createdAt": "2026-04-25T12:00:00Z",
  "updatedAt": "2026-04-25T12:00:00Z",
  "toolVersion": "dev",
  "activeTargetId": "e3b0c44298fc1c149afbf4c8996fb924..."
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | `int` | 是 | `workspace.json` 的结构版本 |
| `layoutVersion` | `int` | 是 | `.dexclub` 目录布局版本 |
| `workspaceId` | `string` | 是 | 工作区唯一身份 |
| `createdAt` | `string` | 是 | 初始化时间，UTC ISO-8601 |
| `updatedAt` | `string` | 是 | 工作区根元数据最后更新时间 |
| `toolVersion` | `string` | 是 | 生成该状态的工具版本 |
| `activeTargetId` | `string` | 是 | 当前激活 target |

### 约束

- 不在 `workspace.json` 重复存整份 snapshot
- 不在 `workspace.json` 保存 cache 内容
- 不在 `workspace.json` 保存原始输入副本信息
- 不在 `workspace.json` 重复存完整 binding 详情

## 2. `targets/<target-id>/target.json`

### 职责

`target.json` 表达 target 身份。

它回答的问题是：

- 当前 target 是谁
- 它最初绑定到哪个 input
- 它绑定到哪个单文件输入

### 建议结构

文件输入示例：

```json
{
  "schemaVersion": 1,
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "createdAt": "2026-04-25T12:10:00Z",
  "updatedAt": "2026-04-25T12:10:00Z",
  "inputType": "file",
  "inputPath": "app.apk"
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | `int` | 是 | `target.json` 的结构版本 |
| `targetId` | `string` | 是 | 当前 target 的稳定 id |
| `createdAt` | `string` | 是 | target 首次绑定时间 |
| `updatedAt` | `string` | 是 | target 身份元数据最后更新时间 |
| `inputType` | `string` | 是 | 首版固定为 `file` |
| `inputPath` | `string` | 是 | 相对 `workdir` 的输入路径 |

### 约束

- `target.json` 不保存 `kind=apk|dex|manifest...` 这类动态扫描结论
- `kind` 属于 `snapshot.json` 的职责

## 3. `targets/<target-id>/snapshot.json`

### 职责

`snapshot.json` 表达“当前 target 当前时刻”的稳定摘要。

它回答的问题是：

- 现在扫出来的 kind 是什么
- 当前 inventory 是什么
- 当前内容指纹是什么
- 当前 capabilities 是什么

### 建议结构

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-04-25T12:15:00Z",
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "inputPath": "app.apk",
  "kind": "apk",
  "inventory": {
    "apkFiles": [
      "app.apk"
    ],
    "dexFiles": [],
    "manifestFiles": [],
    "arscFiles": [],
    "binaryXmlFiles": []
  },
  "inventoryFingerprint": "3d8c6c...",
  "contentFingerprint": "97af4d...",
  "capabilities": {
    "inspect": true,
    "findClass": true,
    "findMethod": true,
    "findField": true,
    "exportDex": true,
    "exportSmali": true,
    "exportJava": true,
    "manifestDecode": true,
    "resourceTableDecode": true,
    "xmlDecode": true,
    "resourceEntryList": true
  }
}
```

APK 中 `resources.arsc` 示例：

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-04-25T12:21:00Z",
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "toolVersion": "dev",
  "sourcePath": "app.apk",
  "sourceEntry": "resources.arsc",
  "sourceFingerprint": "a81d33...",
  "format": "resource-table-v1",
  "payload": {
    "packages": [],
    "entries": []
  }
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | `int` | 是 | `snapshot.json` 的结构版本 |
| `generatedAt` | `string` | 是 | 当前 snapshot 生成时间 |
| `targetId` | `string` | 是 | 对应的 target |
| `inputPath` | `string` | 是 | 相对 `workdir` 的输入路径 |
| `kind` | `string` | 是 | 当前解析得到的摘要类型 |
| `inventory` | `object` | 是 | 当前识别出的物料清单 |
| `inventoryFingerprint` | `string` | 是 | 物料集合指纹 |
| `contentFingerprint` | `string` | 是 | 内容指纹 |
| `capabilities` | `object` | 是 | 当前能力矩阵 |

### `inventory` 说明

`inventory` 建议固定为：

```json
{
  "apkFiles": [],
  "dexFiles": [],
  "manifestFiles": [],
  "arscFiles": [],
  "binaryXmlFiles": []
}
```

这些数组中的路径都应是：

- 相对 `workdir`
- 已排序
- 不重复

### 约束

- `snapshot.json` 是当前扫描摘要，不是历史记录
- `snapshot.json` 不保存 manifest / arsc / xml 正文
- 正文类内容进入 `cache/decoded`

## 4. `cache/decoded/manifest.json`

### 职责

保存 manifest 的解码结果缓存。

### 建议结构

普通 manifest 输入示例：

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-04-25T12:20:00Z",
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "toolVersion": "dev",
  "sourcePath": "AndroidManifest.xml",
  "sourceFingerprint": "97af4d...",
  "format": "xml-text",
  "text": "<manifest ... />"
}
```

APK 中 manifest 示例：

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-04-25T12:20:00Z",
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "toolVersion": "dev",
  "sourcePath": "app.apk",
  "sourceEntry": "AndroidManifest.xml",
  "sourceFingerprint": "97af4d...",
  "format": "xml-text",
  "text": "<manifest ... />"
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | `int` | 是 | 文件结构版本 |
| `generatedAt` | `string` | 是 | 缓存生成时间 |
| `targetId` | `string` | 是 | 所属 target |
| `toolVersion` | `string` | 是 | 生成该缓存的工具版本 |
| `sourcePath` | `string` | 是 | 源输入路径，workdir 相对路径 |
| `sourceEntry` | `string` | 否 | 若来自容器输入，表示容器内 entry |
| `sourceFingerprint` | `string` | 是 | 源输入指纹 |
| `format` | `string` | 是 | 当前 payload 格式名 |
| `text` | `string` | 是 | 解码后的 manifest 文本 |

### `sourcePath` / `sourceEntry` 语义

- `sourcePath`
  - 文件系统中的源输入路径
  - 相对 `workdir`
- `sourceEntry`
  - 若源输入是容器文件，则表示容器内条目路径
  - 例如 APK 内的 `AndroidManifest.xml`

两者不应混用。

### 适用边界

以下文件适合出现顶层 `sourcePath` / `sourceEntry`：

- `cache/decoded/manifest.json`
- `cache/decoded/resource-table.json`
- `cache/decoded/xml/<xml-id>.json`

以下聚合索引文件不建议出现顶层 `sourcePath`：

- `cache/indexes/class-source-map.json`
- `cache/indexes/resource-entry-index.json`

因为这些文件表达的是“当前 target 整体索引结果”，而不是某一个单源输入的正文缓存。

## 5. `cache/decoded/resource-table.json`

### 职责

保存 `resources.arsc` 的解码结果缓存。

### 建议结构

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-04-25T12:21:00Z",
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "toolVersion": "dev",
  "sourcePath": "resources.arsc",
  "sourceFingerprint": "a81d33...",
  "format": "resource-table-v1",
  "payload": {
    "packages": [],
    "entries": []
  }
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | `int` | 是 | 文件结构版本 |
| `generatedAt` | `string` | 是 | 缓存生成时间 |
| `targetId` | `string` | 是 | 所属 target |
| `toolVersion` | `string` | 是 | 生成该缓存的工具版本 |
| `sourcePath` | `string` | 是 | 源输入路径 |
| `sourceEntry` | `string` | 否 | 若来自容器输入，表示容器内 entry |
| `sourceFingerprint` | `string` | 是 | 源输入指纹 |
| `format` | `string` | 是 | payload 格式名 |
| `payload` | `object` | 是 | 资源表解码结果 |

### 约束

- `payload` 可以内部演化
- 但外层包装字段名建议保持稳定

## 6. `cache/decoded/xml/<xml-id>.json`

### 职责

保存单个 XML 的解码结果缓存。

### 文件命名建议

建议：

```text
xml-id = sha256(sourcePath + "\0" + sourceEntry)
```

这样文件名稳定，不受路径分隔符影响，也不会在 APK 场景下因同一 `sourcePath` 下存在多个 XML entry 而冲突。

### 建议结构

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-04-25T12:22:00Z",
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "toolVersion": "dev",
  "sourcePath": "res/layout/activity_main.xml",
  "sourceFingerprint": "0f2aa1...",
  "format": "xml-text",
  "text": "<LinearLayout ... />"
}
```

APK 中 XML 示例：

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-04-25T12:22:00Z",
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "toolVersion": "dev",
  "sourcePath": "app.apk",
  "sourceEntry": "res/layout/activity_main.xml",
  "sourceFingerprint": "0f2aa1...",
  "format": "xml-text",
  "text": "<LinearLayout ... />"
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | `int` | 是 | 文件结构版本 |
| `generatedAt` | `string` | 是 | 缓存生成时间 |
| `targetId` | `string` | 是 | 所属 target |
| `toolVersion` | `string` | 是 | 生成该缓存的工具版本 |
| `sourcePath` | `string` | 是 | 源输入路径 |
| `sourceEntry` | `string` | 否 | 若来自容器输入，表示容器内 entry |
| `sourceFingerprint` | `string` | 是 | 源输入指纹 |
| `format` | `string` | 是 | 当前 payload 格式名 |
| `text` | `string` | 是 | 解码后的 XML 文本 |

## 7. `cache/indexes/class-source-map.json`

### 职责

为 `export-class-dex / export-class-smali / export-class-java` 提供类到 source dex 的快速定位映射。

### 建议结构

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-04-25T12:26:00Z",
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "toolVersion": "dev",
  "contentFingerprint": "97af4d...",
  "format": "class-source-map-v1",
  "mappings": {
    "Lcom/example/Foo;": "1.dex",
    "Lcom/example/Bar;": "2.dex"
  }
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | `int` | 是 | 文件结构版本 |
| `generatedAt` | `string` | 是 | 索引生成时间 |
| `targetId` | `string` | 是 | 所属 target |
| `toolVersion` | `string` | 是 | 生成该索引的工具版本 |
| `contentFingerprint` | `string` | 是 | 内容指纹 |
| `format` | `string` | 是 | 索引格式名 |
| `mappings` | `object` | 是 | `classSignature -> sourceDexPath` 映射 |

### 约束

- key 建议统一使用 type signature，例如 `Lcom/example/Foo;`
- value 建议使用相对 `workdir` 的 dex 路径

## 8. `cache/indexes/resource-entry-index.json`

### 职责

为 APK 工作区下的 `list-res` 提供资源条目索引。

### 建议结构

```json
{
  "schemaVersion": 1,
  "generatedAt": "2026-04-25T12:27:00Z",
  "targetId": "e3b0c44298fc1c149afbf4c8996fb924...",
  "toolVersion": "dev",
  "contentFingerprint": "97af4d...",
  "format": "resource-entry-index-v1",
  "entries": [
    {
      "resourceId": "0x7f020001",
      "type": "layout",
      "name": "activity_main",
      "filePath": "res/a.xml",
      "sourcePath": "app.apk",
      "sourceEntry": "res/a.xml",
      "resolution": "table-backed"
    },
    {
      "type": "layout",
      "name": "activity_main",
      "filePath": "res/layout/activity_main.xml",
      "resolution": "path-inferred"
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | `int` | 是 | 文件结构版本 |
| `generatedAt` | `string` | 是 | 索引生成时间 |
| `targetId` | `string` | 是 | 所属 target |
| `toolVersion` | `string` | 是 | 生成该索引的工具版本 |
| `contentFingerprint` | `string` | 是 | 内容指纹 |
| `format` | `string` | 是 | 索引格式名 |
| `entries` | `array` | 是 | 资源条目列表 |

`entries[*]` 建议字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `resourceId` | `string` | 否 | 逻辑资源 id，例如 `0x7f020001` |
| `type` | `string` | 否 | 资源类型，例如 `layout` |
| `name` | `string` | 否 | 资源逻辑名 |
| `filePath` | `string` | 否 | 物理资源文件路径 |
| `sourcePath` | `string` | 否 | 外部源路径 |
| `sourceEntry` | `string` | 否 | 容器内条目路径 |
| `resolution` | `string` | 是 | 条目解析模式 |

### `entries[*].resolution` 的语义

建议固定为以下枚举之一：

- `table-backed`
  - 表示条目来自 `resources.arsc`，且与物理文件存在可信关联
- `path-inferred`
  - 表示条目只根据路径推断，不代表资源表真相
- `unresolved`
  - 表示条目无法安全推断逻辑资源身份

### 约束

- `list-res` 的主语是资源条目，不是资源文件
- `list-res` 不默认展开字符串、数字、数组等值
- 首版该索引仅为 APK 工作区生成，不要求 standalone `resources.arsc` 工作区生成
- 物理文件路径相同，不足以证明该文件就是资源表中的对应条目

## 稳定契约与可演化部分

建议把 `.dexclub` 内文件分成两类：

### 稳定契约

- `workspace.json`
- `target.json`
- `snapshot.json`
- cache 文件的外层包裹字段

这些字段应该尽量稳定。

### 可演化部分

- `cache/decoded/*` 中的具体 payload 结构
- `cache/indexes/*` 中的具体索引正文

这些允许随着实现演化，但应通过：

- `schemaVersion`
- `format`

控制兼容性。

## 哪些文件是权威真相

建议明确分层：

- `workspace.json`
  - 工作区身份真相
- `target.json`
  - target 身份真相
- `snapshot.json`
  - 当前扫描状态真相
- `cache/*`
  - 非真相，只是可重建缓存

也就是说：

- `cache` 可以删
- `snapshot` 可以重算
- 但 `workspace.json` 和 `target.json` 不应被视为普通缓存

