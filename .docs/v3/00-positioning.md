# DexClub CLI V3 定位

## 目标

DexClub CLI V3 从零开始设计，不继承 v1 / v2 的命令形态、兼容语义或实现约束。

V3 的核心产品概念是：

`通用工作区`

用户先初始化一个工作区，然后在这个工作区上执行后续静态分析命令。

## 产品定义

V3 的产品定义是：

`DexClub CLI V3 = 以通用工作区为统一执行上下文的静态分析 CLI`

这一定义包含以下结论：

1. `workdir` 是正式产品概念，不隐藏。
2. `.dexclub` 是工作区是否已初始化的唯一结构标记。
3. `init` 是唯一允许创建 `.dexclub` 的命令。
4. 除 `init` 外，其余命令都只消费已存在的工作区。
5. CLI 不再暴露 `--workspace` 参数，也不保留 `workspace` 子命令组。

## CLI 输出语言

V3 规定 CLI 所有用户可见输出一律使用英文。

包括：

- help / usage
- stdout 正文
- stderr 错误信息
- text 输出中的键名与文案
- json 输出中的字段名与文案
- `output=...` 这类提示行

仓库协作、设计文档与实现注释可以继续使用中文，但 CLI 对外契约必须统一为英文。

## 非目标

V3 首版明确不做以下内容：

- 对 v1 / v2 命令语法的兼容
- 多工作区聚合
- 一个命令同时绑定多个输入
- 自动向上查找祖先目录中的工作区
- 未初始化时自动创建 `.dexclub`
- Git / 分支 / 提交管理
- 后台服务、守护进程、数据库
- APK 重打包、签名、安装、运行

以下能力可以作为后续增强项讨论，但不进入 v3 首版必须完成范围：

- 方法结构图 / 方法流程图
  - 仅针对单方法
  - 以 JSON 形式输出方法内 block / edge / op
  - 调用边只标记出口，不递归 deep 展开目标方法
- AST 风格语法树导出
  - 优先级低于方法结构图

## 工作区模型

V3 中一个工作区由两部分组成：

1. `workdir`
   - 对用户可见的工作目录
2. `.dexclub`
   - 工作区状态根

用户可以通过：

```text
cli init <input>
```

创建工作区。

其中：

- `input` 必须是单个文件
- `workdir = input.parent`

## 使用模型

V3 的使用模型固定为两段式：

1. `init`
   - 初始化工作区
   - 建立当前绑定目标
2. 后续命令
   - 在工作区上执行 inspect / find / export / resource 相关能力

也就是说：

```text
cli init <input>
cli <command> [workdir] [options]
```

## workdir 省略规则

除 `init` 外，其余命令都允许省略 `workdir`。

省略时的规则是：

- 隐式使用当前进程的 `cwd`
- `cwd` 必须直接存在 `.dexclub`
- 若不存在，则直接失败
- 不向上查找祖先目录

这条规则只依赖当前工作目录，不依赖 CLI 可执行文件是否位于该目录。

## 输入文件规则

V3 收敛后，`init` 首版只接受单个文件输入，不再接受目录输入。

推荐输入类型包括：

```text
.apk
.dex
AndroidManifest.xml
resources.arsc
单个二进制 xml 文件
```

例如：

```text
cli init D:/abc/app.apk
cli init D:/abc/AndroidManifest.xml
```

若传入目录，则 `init` 必须直接失败，而不是尝试扫描目录内物料。

## V3 的硬约束

1. `init` 是唯一允许创建 `.dexclub` 的命令。
2. 其余命令不允许隐式初始化。
3. `init` 首版只接受单个文件输入。
4. 省略 `workdir` 时只看 `cwd`，不向上查找。

## 直接收益

1. 用户心智明确，先 `init`，再使用。
2. 工作区生命周期清晰，不会因为随手执行命令而污染目录。
3. CLI 语法稳定，命令优先，工作区作为可选位置参数。
4. `core` 可以围绕工作区内核建立稳定边界。

## 建议阅读顺序

建议按以下顺序阅读 v3 文档：

1. `00-positioning.md`
2. `10-domain-model.md`
3. `20-workspace-resolution.md`
4. `30-capability-model.md`
5. `40-cli-surface.md`
6. `50-storage-layout.md`
7. `51-state-file-schemas.md`
8. `60-execution-flow.md`
9. `70-pseudocode.md`

