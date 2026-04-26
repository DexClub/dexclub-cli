# DexClub CLI V3 命令面

## 目标

V3 的命令面必须体现以下事实：

1. 工作区是正式概念
2. `init` 是工作区前置步骤
3. 其余命令都在已初始化工作区上执行
4. 命令优先，`workdir` 作为可选位置参数
5. CLI 所有用户可见输出统一使用英文

## 顶层命令

建议首版定义：

```text
init
status
gc
inspect
find-class
find-method
find-field
find-class-using-strings
find-method-using-strings
export-class-dex
export-class-smali
export-class-java
export-method-smali
manifest
res-table
decode-xml
list-res
resolve-res
find-res
```

## 总体语法

### 初始化命令

```text
cli init <input>
```

### 其余命令

```text
cli <command> [workdir] [options]
```

其中：

- `[workdir]` 可省略
- 省略时隐式使用 `cwd`
- `cwd` 必须直接存在 `.dexclub`
- 不向上查找

## init

### 签名

```text
cli init <input>
```

### 语义

- `input` 必须是单个文件
- 在文件所在目录下创建 `.dexclub`
- 建立当前 binding
- 写入工作区 metadata
- 准备必要的状态目录

### 约束

- `init` 是唯一允许创建 `.dexclub` 的命令
- `init` 必须显式传入 `input`
- `init` 不接受多个输入
- `init` 不支持省略参数后默认使用 `cwd`
- `init` 首版不接受目录输入
- 目录输入必须直接失败

若要初始化当前目录，应显式写为：

```text
cli init ./app.apk
```

## 状态命令

### status

```text
cli status [workdir] [--json]
```

输出内容应偏：

- workspace 身份
- 当前 binding 摘要
- 当前 kind
- 当前 capabilities
- 状态摘要

`status` 是只读命令。

它不应：

- 自动修复状态
- 自动重建 snapshot
- 自动重建 cache
- 修改 `.dexclub` 中任何文件

`status` 建议输出以下三种状态之一：

- `healthy`
- `degraded`
- `broken`

其中 issue severity 首版只定义两级：

- `warning`
  - 表示工作区仍可恢复为可执行状态
  - 典型场景如 cache 缺失、snapshot 缺失或过期
- `error`
  - 表示当前工作区不能正常作为执行上下文使用
  - 典型场景如 metadata 损坏、binding 指向的输入不存在

状态与 issue 的对应关系应保持：

- `healthy`
  - 无 issue
- `degraded`
  - 允许 `warning`
  - 不允许 `error`
- `broken`
  - 至少存在一个 `error`

推荐 text 输出示例：

```text
workdir=D:/abc
state=healthy
workspaceId=3d2f6c4a-7b55-4d22-a6fd-7d8a45f1b321
activeTargetId=e3b0c44298fc1c149afbf4c8996fb924...
inputType=file
inputPath=app.apk
kind=apk
inventoryFingerprint=3d8c6c...
contentFingerprint=97af4d...
capabilities=inspect,findClass,findMethod,findField,exportDex,exportSmali,exportJava,manifestDecode,resourceTableDecode,xmlDecode,resourceEntryList
cacheState=present
issueCount=0
```

若状态非健康，则仍应输出状态摘要，并附带 issue 列表。

### gc

```text
cli gc [workdir] [--json]
```

职责：

- 清理当前 active target 下可安全删除、且可重建的 derived state
- 不修改当前 binding

`gc` 首版只处理：

- `targets/<target-id>/cache/decoded/`
- `targets/<target-id>/cache/indexes/`

不处理：

- `workspace.json`
- `target.json`
- `snapshot.json`
- 非 active target 的其它状态目录

推荐 text 输出示例：

```text
workdir=D:/abc
targetId=e3b0c44298fc1c149afbf4c8996fb924...
deletedFiles=12
deletedBytes=483920
```

## 业务命令

### inspect

```text
cli inspect [workdir] [--json]
```

`inspect` 输出当前 active target 的分析摘要。

它应偏向：

- 当前 target 身份
- 当前 kind
- inventory 统计
- class 数量等高价值摘要
- 当前可用 capabilities

它不应重复输出：

- `workspaceId`
- `activeTargetId`
- `state=healthy|degraded|broken`

这些内容属于 `status` 的职责。

`classCount` 在当前 target 可安全计算时输出；若当前输入不包含可安全计数的 dex，或当前实现未提供该统计，则可省略。

推荐 text 输出示例：

```text
kind=apk
inputType=file
inputPath=app.apk
apkCount=1
dexCount=0
manifestCount=0
arscCount=0
binaryXmlCount=0
classCount=1234
capabilities=inspect,findClass,findMethod,findField,exportDex,exportSmali,exportJava,manifestDecode,resourceTableDecode,xmlDecode,resourceEntryList
```

### find-class / find-method / find-field

```text
cli find-class [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]
cli find-method [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]
cli find-field [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]
```

约束：

- `--query-json` 与 `--query-file` 必须且只能传一个
- `--query-file` 按 UTF-8 读取
- `--offset` 可选，默认 `0`
- `--limit` 可选，默认 `all`
- `--json` 控制输出 JSON

查询结果在应用 `offset / limit` 之前必须先做稳定排序。

建议排序键：

- `find-class`
  - `className`, `sourcePath`, `sourceEntry`
- `find-method`
  - `className`, `methodName`, `descriptor`, `sourcePath`, `sourceEntry`
- `find-field`
  - `className`, `fieldName`, `descriptor`, `sourcePath`, `sourceEntry`

结果必须包含来源信息：

- `sourcePath`
  - 外部源路径，相对 `workdir`
- `sourceEntry`
  - 容器内条目路径，可选

text 输出规则：

- 固定表头
- tab 分隔
- 一行一个结果
- 空结果时只保留表头

`--json` 输出规则：

- 直接输出结果数组
- 空结果输出 `[]`
- 不附带额外元信息

```text
find-class:
className	sourcePath	sourceEntry
Lcom/example/Foo;	app.apk	classes2.dex
```

```text
find-method:
className	methodName	descriptor	sourcePath	sourceEntry
Lcom/example/Foo;	bar	(I)Ljava/lang/String;	app.apk	classes2.dex
```

```text
find-field:
className	fieldName	descriptor	sourcePath	sourceEntry
Lcom/example/Foo;	DEBUG	Z	1.dex
```

`descriptor` 的语义统一为底层 DexKit 返回的完整 descriptor。

也就是：

- `find-method`
  - 完整方法 smali 签名
- `find-field`
  - 完整字段类型 descriptor

CLI / core 不应自行再发明另一套 `descriptor` 语义。

### find-class-using-strings / find-method-using-strings

```text
cli find-class-using-strings [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]
cli find-method-using-strings [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]
```

这两个命令对外不暴露 `batch-` 前缀，但语义上对应底层的 batch using strings 能力。

参数与窗口规则对齐其它 `find-*`：

- `--query-json` 与 `--query-file` 必须且只能传一个
- `--query-file` 按 UTF-8 读取
- `--offset` 默认 `0`
- `--limit` 默认 `all`
- `--json` 可选
- 先稳定排序，再应用 `offset / limit`

查询载荷沿用底层 batch using strings 模型：

```json
{
  "groups": {
    "needle-a": [
      { "value": "dexclub-needle-string", "matchType": "Equals" }
    ],
    "needle-b": [
      { "value": "Sample", "matchType": "Contains", "ignoreCase": true }
    ]
  }
}
```

其中：

- `groups` 是 `Map<String, List<StringMatcher>>`
- key 是组名
- value 是该组使用的字符串匹配器列表

结果字段建议：

`find-class-using-strings`

- `className`
- `sourcePath`
- `sourceEntry`

`find-method-using-strings`

- `className`
- `methodName`
- `descriptor`
- `sourcePath`
- `sourceEntry`

结果收口规则应保持：

- 先合并所有 group 的命中
- 再按最终输出字段去重
- 再做稳定排序与分页
- 结果中不暴露 group 维度
- 结果中不机械回显整组查询字符串

### export-class-dex / export-class-smali / export-class-java

```text
cli export-class-dex [workdir] --class <class-name> [--source-path <path>] [--source-entry <entry>] --output <file>
cli export-class-smali [workdir] --class <class-name> [--source-path <path>] [--source-entry <entry>] --output <file> [--auto-unicode-decode true|false]
cli export-class-java [workdir] --class <class-name> [--source-path <path>] [--source-entry <entry>] --output <file>
```

约束：

- `--class` 必填
- `--output` 必填
- `--source-entry` 不允许单独出现，必须与 `--source-path` 一起传
- 若类无法仅凭 `--class` 唯一定位，则必须显式补充来源参数

成功输出固定为：

```text
output=<absolute-path>
```

`export-class-smali` 可额外支持：

```text
--auto-unicode-decode true|false
```

若未显式传入，默认值为：

```text
true
```

### export-method-smali

```text
cli export-method-smali [workdir] --method <signature> [--source-path <path>] [--source-entry <entry>] --output <file> [--auto-unicode-decode true|false] [--mode snippet|class]
```

约束：

- `--method` 必填
- `--output` 必填
- `--source-entry` 不允许单独出现，必须与 `--source-path` 一起传
- 若方法无法仅凭 `--method` 唯一定位，则必须显式补充来源参数
- `--auto-unicode-decode` 可选，默认 `true`
- `--mode` 可选，默认 `snippet`

`--method` 的值统一使用完整 smali 方法签名，例如：

```text
Lcom/example/Foo;->bar(I)Ljava/lang/String;
```

模式语义：

- `snippet`
  - 导出方法级 smali 片段
- `class`
  - 导出最小 class-level smali 壳，仅保留目标方法

`class` 模式的约束：

- 只保留最小 class 头与目标方法
- 不保留其它方法
- 不保留 fields
- 不保证运行时完整性

成功输出固定为：

```text
output=<absolute-path>
```

### manifest

```text
cli manifest [workdir] [--json]
```

职责：

- 读取并解码当前 active target 的 `AndroidManifest.xml`

输出约定：

- text
  - 直接输出 manifest 文本
- `--json`
  - 输出带来源信息的结构化对象

```json
{
  "sourcePath": "app.apk",
  "sourceEntry": "AndroidManifest.xml",
  "text": "<manifest ... />"
}
```

### res-table

```text
cli res-table [workdir] [--json]
```

职责：

- 读取并解析当前 active target 的资源表

输出约定：

- text
  - 输出资源表摘要
- `--json`
  - 输出资源表的完整结构化结果

推荐 text 输出示例：

```text
sourcePath=app.apk
sourceEntry=resources.arsc
packageCount=1
typeCount=12
entryCount=1347
```

### decode-xml

```text
cli decode-xml [workdir] --path <xml-path> [--json]
```

职责：

- 解码当前 active target 中指定的一个物理 XML

约束：

- `--path` 必填
- `--path` 表示逻辑路径或容器内条目路径，例如：
  - `AndroidManifest.xml`
  - `res/layout/activity_main.xml`

`decode-xml` 只按物理 XML 处理，不自动把该 XML 提升为可信的逻辑资源条目。

输出约定：

- text
  - 直接输出解码后的 XML 文本
- `--json`
  - 输出带来源信息的结构化对象

### list-res

```text
cli list-res [workdir] [--json]
```

职责：

- 列出当前 active target 的资源条目索引
- 首版仅支持 APK 工作区

`list-res` 的主语是资源条目，不是资源文件，也不默认展开资源值。

结果字段建议：

- `resourceId`
- `type`
- `name`
- `filePath`
- `sourcePath`
- `sourceEntry`
- `resolution`

其中：

- `resolution=table-backed`
  - 表示该条目来自 `resources.arsc`，且与物理文件存在可信关联
- `resolution=path-inferred`
  - 表示该条目只根据路径推断，不是资源表真相
- `resolution=unresolved`
  - 表示无法安全推断逻辑资源身份

text 输出规则：

- 固定表头
- tab 分隔
- 空结果时只保留表头

### resolve-res

```text
cli resolve-res [workdir] (--id <res-id> | --type <type> --name <name>) [--json]
```

职责：

- 解析单个资源条目的值视图
- 支持 `apk` 与 `resources.arsc` 工作区

约束：

- `--id` 与 `--type + --name` 必须二选一
- 首版默认不递归解引用，只返回当前条目的直接值或引用表示

text 输出示例：

```text
resourceId=0x7f010001
type=string
name=app_name
value=DexClub
```

### find-res

```text
cli find-res [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]
```

职责：

- 按资源值反查资源条目
- 支持 `apk` 与 `resources.arsc` 工作区

参数规则：

- `--query-json` 与 `--query-file` 必须且只能传一个
- `--query-file` 按 UTF-8 读取
- `--offset` 默认 `0`
- `--limit` 默认 `all`
- 先稳定排序，再应用 `offset / limit`

建议首版支持：

- `string`
- `integer`
- `bool`
- `color`

查询 JSON 示例：

```json
{
  "type": "string",
  "value": "login",
  "contains": true,
  "ignoreCase": true
}
```

text 输出规则：

- 固定表头
- tab 分隔
- 空结果时只保留表头

## workdir 规则

### 显式传入

若命令显式传入 `[workdir]`，则：

- 该目录必须直接存在 `.dexclub`
- 否则直接失败

### 省略

若命令省略 `[workdir]`，则：

- 隐式使用当前进程的 `cwd`
- `cwd` 必须直接存在 `.dexclub`
- 否则直接失败

### 不做的行为

- 不向上查找
- 不自动初始化
- 不自动切换到其它目录

## 错误语义

CLI 错误信息一律使用英文。

推荐直接明确：

- `Current directory is not an initialized workspace: <path>`
- `Target workspace is not initialized: <path>`
- `Please run: cli init <input>`

## help 原则

help 文案需要体现以下事实：

1. `init` 是前置步骤
2. 其余命令只消费已初始化工作区
3. `workdir` 是可选位置参数
4. 省略 `workdir` 时使用 `cwd`
5. 不向上查找工作区
6. 所有 help 文案统一使用英文

## help 触发规则

建议支持以下形式：

```text
cli
cli --version
cli --help
cli -h
cli help
cli help <command>
cli <command> --help
cli <command> -h
```

不建议额外扩展更多变体。

## 总 help 模板

总 help 建议固定包含以下部分：

- 产品名与版本
- 总体用法
- 生命周期命令分组
- dex 分析命令分组
- 资源命令分组
- 全局规则
- `cli help <command>` 提示

建议风格：

- 每个命令只给一句话摘要
- 不展开实现细节
- 直接写出最重要的全局规则

## 单命令 help 模板

建议每个命令固定使用以下结构：

```text
Command:
  <command>

Usage:
  cli <command> ...

Description:
  <single-paragraph description>

Arguments:
  <positional arguments when applicable>

Options:
  <supported options>

Output:
  <text/json behavior>

Notes:
  <important constraints>
```

其中：

- `Usage` 只写最终可执行签名
- `Description` 只解释命令意图
- `Notes` 用于放稳定排序、唯一定位、只读语义等高价值约束

## 参数错误提示

参数使用错误时，建议固定输出：

```text
Error: <message>

Usage:
  cli <command> ...
```

适用场景：

- 缺少必填参数
- 参数互斥冲突
- 数值参数非法
- 未知参数
- 重复参数

示例：

```text
Error: missing required option: --query-json or --query-file

Usage:
  cli find-method [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]
```

## 工作区 / 能力 / 状态错误提示

当错误不属于命令用法，而属于工作区状态、能力 gate 或运行前提不满足时，建议输出：

```text
Error: <message>
<next hint when useful>
```

这类错误不必再附整段 usage。

示例：

```text
Error: target workspace is not initialized: D:/abc
Run 'cli init <input>' to create a workspace.
```

```text
Error: current directory is not an initialized workspace: D:/abc
Run 'cli init <input>' to create a workspace.
```

```text
Error: command 'res-table' is not supported by the current workspace (kind=dex)
```

```text
Error: active target input is missing: D:/abc/app.apk
Run 'cli status' for workspace details.
```

## 未知命令提示

未知命令时建议输出：

```text
Error: unknown command: <token>
Run 'cli help' to see available commands.
```

若后续实现成本可接受，可增加近似命令建议，但首版不是必须。

## 文案风格

建议统一遵循：

- 全英文
- 句首大写
- 短句
- 不使用感叹号
- 不使用模糊或营销式表述
- 错误信息尽量直接给出下一步提示

## 参数解析规则

建议统一遵循以下总规则：

1. `init` 必须且只能有一个位置参数 `<input>`
2. 非 `init` 命令最多允许一个位置参数 `[workdir]`
3. 位置参数若存在，必须出现在所有 option 之前
4. 有值参数只支持 `--key value`
5. flag 参数不带值，出现即为 `true`
6. 所有参数默认不允许重复
7. 未知参数直接失败
8. 参数错误统一输出 `Error + Usage`

## 互斥组规则

### 查询类命令

适用命令：

- `find-class`
- `find-method`
- `find-field`
- `find-class-using-strings`
- `find-method-using-strings`
- `find-res`

规则：

- `--query-json` 与 `--query-file` 必须且只能传一个

### `resolve-res`

规则：

- `--id` 与 `--type + --name` 必须二选一
- 不允许同时传 `--id` 和 `--type`
- 不允许同时传 `--id` 和 `--name`

## 依赖组规则

### `resolve-res`

规则：

- `--type` 与 `--name` 必须同时出现

### `export-class-*`

规则：

- `--source-entry` 必须依赖 `--source-path`
- `--source-path` 可以单独出现

## 数值参数校验

### `--offset`

适用：

- 所有 `find-*`
- `find-res`

规则：

- 必须是整数
- 必须大于等于 `0`

### `--limit`

适用：

- 所有 `find-*`
- `find-res`

规则：

- 必须是整数
- 必须大于 `0`

## 文件参数校验

### `--query-file`

规则：

- 文件必须存在
- 必须是普通文件
- 按 UTF-8 读取
- 内容必须是合法 JSON

## 空字符串规则

建议所有字符串参数先做 trim。

trim 后为空时，按“缺失值”处理。

适用：

- `--class`
- `--path`
- `--type`
- `--name`
- `--source-path`
- `--source-entry`
- `--query-json`

## 典型参数错误示例

```text
Error: --query-json and --query-file are mutually exclusive

Usage:
  cli find-method [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]
```

```text
Error: --id and --type/--name are mutually exclusive

Usage:
  cli resolve-res [workdir] (--id <res-id> | --type <type> --name <name>) [--json]
```

```text
Error: --type and --name must be specified together
```

```text
Error: --source-entry requires --source-path
```

```text
Error: invalid value for --offset: expected a non-negative integer
```

```text
Error: invalid value for --limit: expected a positive integer
```

```text
Error: option may only be specified once: --limit
```

## 输出契约

### stdout / stderr

建议固定为：

- `stdout`
  - 只放命令结果
- `stderr`
  - 只放错误、提示与诊断信息

成功时不应向 `stderr` 追加额外提示。

### text 输出稳定性

text 输出建议也作为稳定契约，而不只是“可读文本”。

规则：

- key-value 输出
  - 字段顺序固定
- 表格输出
  - 表头固定
  - 列顺序固定
  - tab 分隔固定
- 空结果行为固定

### JSON 输出稳定性

JSON 输出建议直接返回业务对象或业务数组。

不建议默认套统一 envelope。

规则：

- 字段名稳定
- 数组元素结构稳定
- 仅在某个命令未来确实需要明显不同的 JSON 结构时，再单独引入 `format`

### 导出类命令输出

`export-class-dex` / `export-class-smali` / `export-class-java` 成功时固定输出：

```text
output=<absolute-path>
```

约束：

- 不支持 `--json`
- 失败时不写 `stdout`

## 退出码

建议首版固定为：

- `0`
  - 成功
  - `status=healthy`
- `1`
  - CLI 用法错误
  - 参数错误
  - 未知命令
- `2`
  - 工作区错误
  - 能力不支持
  - 底层执行失败
  - `status=degraded`
  - `status=broken`

