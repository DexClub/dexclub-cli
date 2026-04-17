# DexClub CLI Workspace V2 设计稿

## 背景

`workspace` 已经验证了“工程化静态分析 + 缓存复用”方向成立，但当前 v1 仍有两个结构性问题：

1. `workspace` 子命令统一依赖 `--workspace <dir>`，语义重复，CLI 形态不够自然。
2. `manifest/res` 能力当前被 `apk` 输入强绑定，不利于把 dexclub-cli 做成真正的静态分析工具。

因此 v2 直接做破坏性调整，不保留 v1 兼容语法。

## v2 目标

1. 统一 `workspace` 子命令语法，使用 `<dir>` 作为第一个位置参数。
2. 把 `workspace` 从“容器类型模型”升级为“输入类型 + 能力矩阵模型”。
3. 保持 `workspace` 为单输入工作区，不做多输入工程聚合。
4. 明确机器可读输出契约，禁止日志污染 JSON 输出。

## 非目标

- 不做 APK 重打包、签名、安装、运行。
- 不做 Git、分支、提交、分析笔记托管。
- 不做后台服务、数据库系统、守护进程。
- 不在 v2 首轮引入 `workspace cache/*` 或 `workspace runs/*` 公共命令面。

## 破坏性调整

v2 不再接受 `--workspace <dir>`。

所有 `workspace` 子命令都改为：

```bash
dexclub-cli workspace <subcommand> <dir> [options]
```

示例：

```bash
dexclub-cli workspace init .ws/demo --input app.apk
dexclub-cli workspace status .ws/demo
dexclub-cli workspace inspect .ws/demo
dexclub-cli workspace find-class .ws/demo --query-json '{...}'
```

## 输入类型模型

v2 的 `workspace` 输入类型建议扩展为：

- `apk`
- `dex`
- `dexs`
- `bundle`
- `manifest`
- `arsc`
- `axml`
- `resdir`

定义说明：

- `apk`
  - 单个 `.apk`
- `dex`
  - 单个 `.dex`
- `dexs`
  - 一个 dex 目录，或一组显式 dex 文件
- `bundle`
  - 一个混合静态分析物料目录
  - 目录内允许出现 `classes*.dex`、`AndroidManifest.xml`、`resources.arsc`、二进制 XML、`res/`、`assets/`
- `manifest`
  - 单独的 `AndroidManifest.xml`
  - 允许文本 XML 或二进制 AXML
- `arsc`
  - 单独的 `resources.arsc`
- `axml`
  - 单独的二进制 XML，例如 `res/layout/*.xml`
- `resdir`
  - 已解包的 `res/` 目录

## 能力矩阵

v2 不再把“资源能力”压缩成一个 `res` 开关，而是拆成更细的能力字段。

建议能力字段：

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

建议矩阵如下：

| input type | inspect | find/export dex | manifest decode | arsc decode | xml decode | list res |
| --- | --- | --- | --- | --- | --- | --- |
| `apk` | yes | yes | yes | yes | yes | yes |
| `dex` | yes | yes | no | no | no | no |
| `dexs` | yes | yes | no | no | no | no |
| `bundle` | yes | conditional | conditional | conditional | conditional | conditional |
| `manifest` | yes | no | yes | no | yes | no |
| `arsc` | yes | no | no | yes | no | no |
| `axml` | yes | no | no | no | yes | no |
| `resdir` | yes | no | no | no | optional | yes |

约束：

- 不支持的能力必须显式失败。
- 失败信息必须包含当前输入类型。
- 不伪造资源层，不自动把 `dex|dexs` 转换成 `apk` 语义。
- `bundle` 的能力不按类型名写死，而按目录内实际发现的物料动态开启。

## 命令树

### 顶层无状态命令

保留现有 dex 向命令：

```bash
dexclub-cli inspect --input <...>
dexclub-cli find-class --input <...>
dexclub-cli find-method --input <...>
dexclub-cli find-field --input <...>
dexclub-cli export-dex --input <...>
dexclub-cli export-smali --input <...>
dexclub-cli export-java --input <...>
```

新增资源向命令：

```bash
dexclub-cli manifest --input <apk|AndroidManifest.xml>
dexclub-cli res-table --input <apk|resources.arsc>
dexclub-cli decode-xml --input <apk|AndroidManifest.xml|binary-xml>
dexclub-cli list-res --input <apk|resdir>
```

### workspace 有状态命令

```bash
dexclub-cli workspace init <dir> --input <...> [--type <...>]
dexclub-cli workspace status <dir>
dexclub-cli workspace inspect <dir>
dexclub-cli workspace capabilities <dir>

dexclub-cli workspace find-class <dir> ...
dexclub-cli workspace find-method <dir> ...
dexclub-cli workspace find-field <dir> ...
dexclub-cli workspace export-dex <dir> ...
dexclub-cli workspace export-smali <dir> ...
dexclub-cli workspace export-java <dir> ...

dexclub-cli workspace manifest <dir>
dexclub-cli workspace res-table <dir>
dexclub-cli workspace decode-xml <dir>
dexclub-cli workspace list-res <dir>
```

## `bundle` 语义

`bundle` 用来表示“一个目录里混合存在多类静态分析物料”，例如：

- `classes.dex` + `resources.arsc`
- `classes.dex` + `AndroidManifest.xml`
- `resources.arsc` + `AndroidManifest.xml`
- `classes*.dex` + `res/`
- `AndroidManifest.xml` + `res/`
- `dex + manifest + arsc + res/`

`bundle` 不等于 APK。

它表示的是“解包后或收集后的静态分析物料目录”，不是压缩容器。

### `bundle` 能力开启规则

建议按目录内发现的核心物料动态开启能力：

- 发现 `classes*.dex`
  - 开启 `findClass/findMethod/findField/exportDex/exportSmali/exportJava`
- 发现 `AndroidManifest.xml`
  - 开启 `manifestDecode`
- 发现 `resources.arsc`
  - 开启 `resourceTableDecode`
- 发现可识别 XML 资源
  - 开启 `xmlDecode`
- 发现 `res/`
  - 开启 `resourceEntryList`

示例：

- `dex + arsc`
  - 支持 dex 查询导出 + 资源表解码
- `manifest + arsc`
  - 支持 manifest 解码 + 资源表解码
- `arsc + axml`
  - 支持资源表解码 + XML 解码
- `dex + manifest + arsc + res/`
  - 支持全部静态分析能力

### `bundle` 与 `dexs` / `resdir` 的边界

- `dexs`
  - 纯 dex 集输入
- `resdir`
  - 纯资源目录输入
- `bundle`
  - 混合物料目录输入

不建议让 `dexs` 或 `resdir` 兼容混合物料，否则能力矩阵会变得含糊。

## 命令语义

### `workspace init <dir>`

职责：

- 建立工作区目录
- 解析输入类型
- 生成最小 identity metadata
- 构建必要的 derived state

输入限制：

- `workspace` 仍然只绑定一个“单输入工作区”
- 其中 `dexs` 属于一个逻辑输入类型，不是多工程

### `workspace status <dir>`

职责：

- 输出工作区身份信息
- 输出输入类型
- 输出 fingerprint
- 输出 cache/runs 是否存在

不输出：

- 重型分析摘要
- 大量类数/资源项内容

### `workspace inspect <dir>`

职责：

- 输出当前输入的分析摘要
- 不同输入类型输出不同摘要字段

示例：

- `apk`: dex 数、包名、manifest 存在、arsc 存在、res 项数
- `dexs`: dex 数、class 数
- `manifest`: 编码类型、根节点、package
- `arsc`: package 数、entry 数
- `axml`: 根节点、命名空间、是否二进制 XML
- `resdir`: 文件数、资源类型分布

## 输出契约

v2 明确规定：

- `--output-format json` 时，stdout 只能输出 JSON。
- 第三方库日志不得污染 stdout。
- 运行日志如确有保留必要，必须转到 stderr，或默认静默。
- `--output-file` 只写目标文件，不得再在 stdout 重复打印机器可读结果。

这是一条硬约束，因为 `workspace` 的核心场景包含脚本化和缓存复用。

## 工作区结构

v2 继续沿用 `<dir>/.dexclub-cli/`，不在本轮引入新的根所有者。

最小结构：

```text
<dir>/
  .dexclub-cli/
    workspace.json
    cache/
      v2/
    runs/
      v2/
```

说明：

- 目录 layout 升为 v2。
- metadata schema 与 layout version 应同步升级。
- 不要求这轮公开 `cache/*` 和 `runs/*` 命令面。

## metadata 方向

metadata 仍保持最小 identity contract，只记录：

- `workspaceId`
- `createdAt`
- `updatedAt`
- `toolVersion`
- `schemaVersion`
- `layoutVersion`
- `input.type`
- `input.binding`
- `input.fingerprint`

不把分析结果塞入 metadata。

## 输入识别建议

### auto 推断

- 单个 `.apk` -> `apk`
- 单个 `.dex` -> `dex`
- 单个目录且目录内为 dex 集 -> `dexs`
- 单个目录且出现两类及以上核心物料 -> `bundle`
- 单个文件名为 `AndroidManifest.xml` -> `manifest`
- 单个 `.arsc` -> `arsc`
- 单个二进制 XML -> `axml`
- 单个 `res/` 目录 -> `resdir`

若存在歧义，要求显式 `--type`。

核心物料建议定义为：

- `classes*.dex`
- `AndroidManifest.xml`
- `resources.arsc`
- `res/`
- 可识别 AXML 文件

目录输入的建议判断顺序：

1. 只有 dex 文件 -> `dexs`
2. 只有标准 `res/` 目录形态 -> `resdir`
3. 存在两类及以上核心物料 -> `bundle`
4. 只有一种核心物料但不满足 `dexs/resdir` 的纯输入约束 -> `bundle`
5. 无法稳定判断 -> 要求显式 `--type`

### `bundle` 是否需要手动指定

建议：

- 默认自动识别
- 保留 `--type bundle` 作为强制覆盖

也就是说：

- 自动识别是主路径
- 手动指定是歧义兜底

不建议把 `bundle` 设计成只能手动指定的隐藏类型。

### `apk`

`apk` 仍然是最强输入，因为它天然包含：

- dex
- manifest
- resources.arsc
- 二进制 XML
- res/ assets/

但 v2 不再把它当作资源分析的唯一入口。

## 实现建议

按顺序分三层推进：

### 第 1 层：CLI 结构重排

- 所有 `workspace` 子命令改成 `<dir>` 位置参数
- 重写 help/usage
- 删除 `--workspace` 相关解析

### 第 2 层：能力模型重构

- 扩展 `WorkspaceInputKind`
- 重构 `WorkspaceCapabilities`
- 把旧的 `manifest/res` 开关拆成显式资源子能力
- 统一能力 gate 错误文案

### 第 3 层：资源输入扩展

- `manifest`
- `arsc`
- `axml`
- `resdir`

首轮允许只把命令面和 metadata 先搭好，再逐个补实现。

## 命名建议

现有 `workspace res` 命名过宽，v2 建议拆开：

- `manifest`
- `res-table`
- `decode-xml`
- `list-res`

不建议继续保留一个语义模糊的 `res` 命令。

## 测试要求

v2 至少应新增或重写这些测试：

- `workspace` 子命令位置参数解析
- 不再接受 `--workspace`
- `apk|dex|dexs|bundle|manifest|arsc|axml|resdir` 的 input infer/gate
- capability matrix 输出正确
- 不支持能力时的显式失败提示
- `--output-format json` 时 stdout 无日志污染
- 顶层命令依旧无状态，不读写 `.dexclub-cli`

## 风险

### 1. 输入类型扩展后，`inspect` 摘要结构不再同构

这是可接受的。

`inspect` 的职责是“给当前输入一个合理摘要”，不是强行让所有输入拥有同一字段集合。

### 2. 第三方库日志污染会破坏机器可读契约

这是 v2 必修项，不能继续容忍。

### 3. `jadx` 对单文件资源输入的库接口边界可能不完全对称

即使如此，产品面也不应被 `jadx` 当前接口绑死。

必要时：

- `apk` 走 `jadx`
- `manifest/axml/arsc` 走独立解析路径

CLI 层不暴露实现差异。

## 建议的 v2 最小交付顺序

1. 位置参数替换 `--workspace`
2. `workspace inspect` JSON 修复
3. stdout/stderr/logging 契约收紧
4. `workspace res` 拆分为明确命令名
5. 新增 `manifest|arsc|axml|resdir` 输入类型

## 结论

v2 不应只是“把 `--workspace` 改成 `<dir>`”。

更合理的定位是：

`workspace v2 = 更自然的 CLI 形态 + 更明确的能力模型 + 不被 APK 容器边界绑死的静态分析输入系统`
