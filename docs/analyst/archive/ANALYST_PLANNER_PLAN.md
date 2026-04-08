# Analyst 规划方案

> 状态：V1 设计归档记录。对应能力已落地完成，本文保留为规划器 / 执行器边界与实现依据。

## 状态

这份文档定义了 `skills/dexclub-cli-launcher` 中 analyst 层的 V1 规划与执行模型。

它是偏实现导向的设计文档，但不是代码。目标是在 `plan_schema.py`、`planner.py`、`runner.py`、`analyze.py` 落地前，把整体边界先稳定下来。

## 范围边界

这份方案只适用于 `skills/dexclub-cli-launcher/` 下基于 Python 的 analyst 层。

它不会重定义主仓库的 Kotlin 模块边界：

- `cli/`
  - 继续作为主项目的命令行入口
- `core/`
  - 继续作为稳定的业务能力层
- `dexkit/`
  - 继续作为 KMP DexKit 包装层

V1 的规划器 / 执行器工作在 launcher skill 暴露出来的 released `dexclub-cli` 行为之上，不应额外引入对 `cli / core / dexkit` 内部实现的假设。

## 基础现状

当前仓库已经有三层基础能力：

1. `launcher/`
   - 准备并运行缓存的 `dexclub-cli` release
2. analyst 低层辅助脚本
   - query 组合与执行
   - export and scan
   - exported-code analysis
3. analyst 文档
   - capability 说明
   - 工作流说明

下一步不是替换这些层，而是在它们之上增加一个受限的 planning / execution 层。

## 目标

引入一个结构化规划器和执行器，使其能够：

- 把逆向分析请求归类到一小组受支持的任务类型
- 生成结构化执行计划
- 通过现有辅助脚本和 launcher-backed CLI 执行计划
- 产出结构化结果，以及可选的人类可读摘要

## 非目标

V1 不做以下事情：

- 处理任意自然语言逆向问题
- 做无界多跳分析
- 自动解析 Android 入口组件
- 自动分析 Android resources
- 自动推断强反射行为
- 仅仅为了“结构化”而引入 MCP

## 指导原则

- 只保留一个用户可见的 skill。
- launcher 关注点与 analyst 关注点分离。
- 规划器输出视为结构化数据，不是自由推理文本。
- 第一版任务集刻意保持小而稳定。
- 优先依赖显式输入，不做过度猜测。
- 能力不足时明确返回 `partial_support` 或 refusal，而不是假装已经自动化。

## 当前约束

V1 规划必须与 `analyst/scripts/` 下已经存在的辅助脚本保持一致。

当前辅助脚本约束如下：

- `run_find.py`
  - 通过 launcher 执行一次 `find-*`
  - 支持基于直接 query 的搜索 workflow
  - 当前接受 APK 或 dex 输入，并可重复传入 `--input`
  - 当前是执行辅助脚本，不是标准化结果契约
- `export_and_scan.py`
  - 只对一个 dex 输入导出一个 class
  - 不能直接处理 APK 输入
  - 目前只按 method name 做方法级 scope
  - 不能通过 descriptor 消除 overload 歧义
  - 当前输出的是辅助脚本风格字段，例如 `exportPath`
- `code_analysis.py` 与 `scan_exported_code.py`
  - 分析导出的 Java 或 smali 文本
  - 提供 strings、numbers、field access、direct calls 等直接信号
  - 不提供完整语义级 call graph

规划器不应描述这些辅助脚本当前做不到的能力，除非明确伴随新的实现工作。

当前辅助脚本摘要：

- `run_find.py`
  - 输入形状：CLI 风格 flags
  - 输出形状：原始 `dexclub-cli` stdout
  - 硬限制：没有 total-hit count、没有规划器自有的 normalization、没有 overload disambiguation
- `export_and_scan.py`
  - 输入形状：一个 dex path 加一个 class anchor，再加可选 method name
  - 输出形状：辅助脚本级 JSON 或文本摘要
  - 硬限制：不支持 APK 输入、不支持 descriptor 强制方法 scope、每次只处理一个导出 class
- `scan_exported_code.py`
  - 输入形状：一个导出的 Java 或 smali 文件
  - 输出形状：code-analysis 摘要切片
  - 硬限制：仅限文本级扫描，不是语义程序分析

## 未决假设

在与真实 released CLI 输出对齐前，下面这些都应被视为明确假设：

- `find-method --output-format json` 返回的精确字段名
- direct search 结果是否稳定包含 method descriptor
- 多 dex 输入时，direct search 结果是否稳定包含 source dex 标识
- 通过 `invokeMethods` 和 `callerMethods` 返回的 relation hits 是否暴露了足够的数据，足以做精确 normalization

V1 实现应尽早验证这些假设，并在宣称对外契约稳定前，同步更新方案或执行器的 normalization 规则。

## 分层

skill 应保持如下分层：

1. `launcher/`
   - release 准备
   - cache 复用
   - 真实 CLI 执行
2. analyst 低层执行原语
   - `build_query.py`
   - `run_find.py`
   - `export_and_scan.py`
   - `scan_exported_code.py`
   - `code_analysis.py`
3. 新的 analyst 规划层
   - `plan_schema.py`
   - `planner.py`
   - `runner.py`
   - `analyze.py`

## V1 范围

### 支持的任务类型

V1 只支持以下任务类型：

- `search_methods_by_string`
- `search_methods_by_number`
- `summarize_method_logic`
- `trace_callers`
- `trace_callees`

在第一版稳定前，不应继续新增任务类型。

### 任务注册表

实现层应保留一个统一的 task registry，作为规划器行为的唯一真相来源。

每个 task definition 至少声明：

- required arguments
- optional arguments
- accepted input kinds
- default planning strategy
- result shape
- current limits
- `max_direct_hits`

V1 中，`max_direct_hits` 应在 task registry 中显式定义，而不是运行时隐式推断。

推荐默认值：

- direct-search tasks 默认 `max_direct_hits = 20`
- runner 侧 threshold probing 默认使用 `internal_limit = max_direct_hits + 1`，除非某个 task definition 明确覆盖

### 阈值规则

当某个 task 结果超过 `max_direct_hits` 时，planner 或 runner 不应自动展开搜索，而应返回结构化的 narrowed-search recommendation。

当前 CLI 对齐前提：

- 当前 CLI 会先应用 `--limit`，再输出 JSON 或文本
- 因此 runner 侧 threshold detection 不能依赖一个当前并不存在的 helper total count
- direct-search tasks 应区分“用户可见 limit”和“内部执行 limit”
- 对 V1 direct-search steps，runner 应至少从底层 `run_find` 取到 `max_direct_hits + 1` 条，以便探测 overflow
- 一旦探测到 overflow，应返回 narrowing recommendation，而不是悄悄截断并把结果当成完整结果

threshold-overflow 的结果规则：

- threshold overflow 不属于 `execution_error`
- V1 可以返回一个 truncated preview，再附带 recommendation，但必须显式标出 `truncated = true`
- 由于当前 CLI 路径拿不到精确 pre-limit total，V1 不要求单独提供 `total_count`
- 在这种情况下，`count` 应表示当前 payload 中实际返回的条目数，而不是猜测的全局总数
- 如果仍然返回了非空 preview，则最终 run `status` 可以保持 `ok`
- 如果选择不返回 preview，只返回 narrowing guidance，则最终 run `status` 应是 `empty`

external `limit` 规则：

- 外部输入的 `limit` 是展示约束，不是唯一的执行约束
- direct-search tasks 可以用比 external `limit` 更大的 internal limit 执行，以便可靠检测 overflow
- 若两者同时存在，最终 payload 必须遵守 external `limit`，但 overflow detection 仍可依赖更大的 internal window
- 如果结果只是因为 external `limit` 被截断，也仍应标记 `truncated = true`
- 仅由 external `limit` 造成的截断，不要求额外给 narrowing recommendation

## 输入

### 共享输入规则

- 每个任务都必须声明其 accepted input types。
- 外部任务字段名统一使用 `input`。
- 外部 `input` 可以是：
  - 一个路径字符串
  - 一个路径字符串数组
- planner 必须把这两种形式都归一化成共享的内部 `paths` 列表
- 规划前必须校验文件路径
- planner 不得静默转换不受支持的输入类型
- planner 和 runner 必须把外部输入归一化成统一的内部输入模型

### 归一化输入模型

preflight 之后，输入应被归一化成统一形状。

必需字段：

- `primary_kind`
- `paths`
- `path_count`

允许的 `primary_kind`：

- `apk`
- `dex`
- `dex_set`
- `exported_code`

归一化规则：

- 单 APK 输入 -> `primary_kind = "apk"`
- 单 dex 输入 -> `primary_kind = "dex"`
- 多 dex 输入 -> `primary_kind = "dex_set"`
- 导出的 Java 或 smali 输入 -> `primary_kind = "exported_code"`
- APK 与 dex 混合输入在 V1 中无效
- 基于 export 的任务必须要求 `primary_kind = "dex"`

示例：

```json
{
  "primary_kind": "dex",
  "paths": [
    "/path/to/classes.dex"
  ],
  "path_count": 1
}
```

### 任务与输入矩阵

V1 接受的输入类型矩阵如下：

- `search_methods_by_string`
  - `apk`
  - `dex`
  - `dex_set`
- `search_methods_by_number`
  - `apk`
  - `dex`
  - `dex_set`
- `summarize_method_logic`
  - `dex`
  - 必须且只能有一个 dex path
- `trace_callers`
  - `apk`
  - `dex`
  - `dex_set`
- `trace_callees`
  - `apk`
  - `dex`
  - `dex_set`

任何超出这个矩阵的 task / input-kind 组合，V1 都必须拒绝。

V1 对 `exported_code` 的处理：

- `exported_code` 可以保留在归一化模型中，为未来扩展做准备
- 但 V1 中没有任何任务接受 `exported_code` 作为外部输入
- 如果 V1 任务收到 `exported_code`，planner 必须返回 `unsupported`

### 各任务的外部输入

#### `search_methods_by_string`

必需：

- `input`
- `string`

可选：

- `declared_class`
- `search_package`
- `limit`

#### `search_methods_by_number`

必需：

- `input`
- `number`

可选：

- `declared_class`
- `search_package`
- `limit`

#### `summarize_method_logic`

必需：

- `input`
- `method_anchor`

可选：

- `language`
- `mode`

#### `trace_callers`

必需：

- `input`
- `method_anchor`

可选：

- `search_package`
- `limit`

#### `trace_callees`

必需：

- `input`
- `method_anchor`

可选：

- `search_package`
- `limit`

## Method Anchor 模型

方法级任务不能只依赖 `class_name + method_name`，因为在 overload 存在时这个信息不够稳定。

必需字段：

- `class_name`
- `method_name`

可选字段：

- `descriptor`
- `params`
- `return_type`

V1 规则：

- 当用户不知道 descriptor 时，可以接受放宽版的 `class_name + method_name`
- 但放宽版必须被标记为“可能有歧义”
- planner 必须在 `limits` 中记录这种歧义
- 如果找到多个 overload，而任务又要求精确目标，runner 必须返回 `ambiguous`，而不是猜一个
- V1 不会做隐藏的 overload resolution，把一个 relaxed anchor 自动升级成 precise anchor

与当前 helper 的对齐：

- 归一化后的 planner model 可以携带 `descriptor`、`params` 或 `return_type`
- 当前 query helpers 只会用 `ClassName#methodName` 构造 direct method anchor
- 当前 export-and-scan helpers 也只按 method name 对导出代码做 scope
- 因此 descriptor-aware disambiguation 首先是 planner / runner 的职责

如果 planner 收到 descriptor-aware 输入，但选定的 V1 执行路径无法保证这种精度，就必须返回 `unsupported` 或 `ambiguous`，而不是默默丢掉精度。

V1 执行约束：

- planner / runner 可以在 normalized data 中保留 descriptor-aware 输入，即使当前 helper 还不能直接消费
- 但在选定执行路径无法真正 enforce 或 verify 精度时，不得宣称“已做 descriptor 精确执行”
- V1 不会在声明之外偷偷加一层 overload-resolution step，让 relaxed anchor 看起来像 precise
- 如果根据提供的 anchor 和声明的执行路径，无法建立精确性，则结果必须保持 `ambiguous` 或 `unsupported`
- 对 `trace_callers` / `trace_callees`，descriptor-aware anchor 必须先解析成唯一 concrete target，才能把 relation 结果标成 precise
- 对 `summarize_method_logic`，如果导出 class 中存在多个同名 overload，而当前路径无法隔离目标 descriptor，就必须返回 `ambiguous`
- 在当前 V1 的单步 `run_find` 策略下，descriptor-aware relation tracing 通常应尽早失败为 `ambiguous` 或 `unsupported`，除非执行路径获得了可 enforce descriptor 的能力

推荐的外部形式：

- 结构化形式
  - `class_name`
  - `method_name`
  - 可选 `descriptor`
- 紧凑形式
  - `ClassName#methodName`
  - 未来可扩展成 `ClassName#methodName(signature)`

示例：

```json
{
  "class_name": "com.example.Target",
  "method_name": "login",
  "descriptor": "(Ljava/lang/String;)Z"
}
```

## Preflight

在真正 planning 之前，轻量 preflight 阶段应检查：

- 文件是否存在
- 文件是否可读
- 输入类型是否兼容
- export-based task 是否满足单 dex 要求
- 必需任务参数是否存在

V1 的 preflight 文件类型规则：

- 输入类型检测应基于显式路径形状和文件扩展名，而不是深度内容探测
- `.apk` -> APK 输入候选
- `.dex` -> dex 输入候选
- `.java` 或 `.smali` -> exported-code 输入候选
- 未知扩展名应在 preflight 直接失败，除非某个任务 contract 明确允许
- 即使每个路径都可读，APK 与 dex 混合输入在 V1 中仍无效

preflight 只负责输入和环境的基本校验，不负责业务推理。

## Planner Schema

planner 必须输出结构化 JSON，不能把自由文本计划当成主 contract。

顶层必需字段：

- `schema_version`
- `planner_version`
- `task_type`
- `inputs`
- `normalized_inputs`
- `steps`
- `limits`
- `expected_outputs`
- `stop_conditions`

示例：

```json
{
  "schema_version": "1",
  "planner_version": "1",
  "task_type": "summarize_method_logic",
  "inputs": {
    "input": "/path/to/classes.dex",
    "method_anchor": {
      "class_name": "com.example.Target",
      "method_name": "login"
    }
  },
  "normalized_inputs": {
    "primary_kind": "dex",
    "paths": [
      "/path/to/classes.dex"
    ],
    "path_count": 1
  },
  "steps": [
    {
      "step_id": "step-1",
      "kind": "export_and_scan",
      "tool": "export_and_scan.py",
      "args": {
        "input_dex": "/path/to/classes.dex",
        "class_name": "com.example.Target",
        "method": "login",
        "language": "smali",
        "mode": "summary",
        "output_dir": "<run-root>/exports"
      }
    }
  ],
  "limits": [
    "only direct method body analysis",
    "no automatic resource analysis"
  ],
  "expected_outputs": [
    "structured step result",
    "human-readable summary"
  ],
  "stop_conditions": [
    "stop after the planned steps complete",
    "do not expand into additional hops automatically"
  ]
}
```

## Step Schema

每个计划步骤都应使用受限 schema。

必需字段：

- `step_id`
- `kind`
- `tool`
- `args`

推荐字段：

- `allow_empty_result`

artifact 路径说明：

- 如果某一步预期会产出 artifact，则 plan args 应显式携带 runner 持有的输出位置，而不是依赖 helper 自己创建临时目录
- 对 V1 来说，这一点最直接适用于 `export_and_scan` 的 `output_dir`

V1 的 step kinds：

- `run_find`
- `export_and_scan`

第一版不应引入动态 step kind。

## Runner 规则

runner 不是第二个 planner。

runner 必须：

- 消费已有的结构化 plan
- 按顺序执行 steps
- 收集结构化结果
- 把 helper 输出规范化成稳定的结果 payload
- 负责 subprocess capture
- 管理 run artifact root，以及传给 helper 脚本的显式 artifact 路径
- 生成一个最终结构化报告，以及可选的人类可读摘要

runner 不得：

- 发明新的 steps
- 改变 task type
- 不经新 plan 就递归扩搜索
- 只是简单透传 helper 输出

## Step Result Schema

step result 由 runner 持有。现有 helper script 只是执行原语，不是结果 contract 边界。

必需字段：

- `step_id`
- `step_kind`
- `status`
- `exit_code`
- `command`
- `stdout`
- `stderr`
- `artifacts`
- `result`

允许的 step `status`：

- `ok`
- `empty`
- `execution_error`

含义：

- `ok`：执行成功，且规范化结果非空
- `empty`：执行成功，但没有 hits 或没有相关条目
- `execution_error`：计划中的 command 或 helper 失败

runner capture 规则：

- runner 必须捕获 stdout、stderr、exit code 和执行命令
- runner 必须把 helper 输出规范化成 step result schema
- V1 中，helper script 不需要直接输出最终 step result schema

runner normalization 规则：

- helper 输出字段名不是公共 contract 边界
- 最终结果里使用什么字段命名，由 runner 负责
- V1 的 normalized result keys 应使用一致的命名风格
- 如果现有 helper 输出 camelCase 字段，例如 `exportPath`、`branchLineCount`、`methodCalls`、`fieldAccesses`，runner 必须显式映射，而不是把混合风格漏到最终 contract 里

step-status 边界规则：

- `ambiguous` 和 `unsupported` 是 run 级状态，不是 step 级状态
- 即使最终 run 因为精度要求变成 `ambiguous`，step 本身仍然可以是 `ok` 或 `empty`
- 如果 runner 在任何 step 执行前就能判定 `unsupported`，则应直接返回 run-level `unsupported`，而不是伪造 step result

示例：

```json
{
  "step_id": "step-1",
  "step_kind": "run_find",
  "status": "ok",
  "exit_code": 0,
  "command": [
    "python3",
    "./skills/dexclub-cli-launcher/analyst/scripts/run_find.py",
    "method",
    "--input",
    "/path/to/app.apk",
    "--using-string",
    "login"
  ],
  "stdout": "[...]",
  "stderr": "",
  "artifacts": [],
  "result": {
    "count": 3,
    "items": []
  }
}
```

## 规范化结果 payload

runner 应把 step payload 规范化成少量固定 result shapes，而不是让 `step_results[].result` 完全开放。

### 通用 hit-list 结果

用于 direct query 驱动的步骤。

必需字段：

- `count`
- `items`

推荐字段：

- `truncated`

字段语义：

- `count` 表示当前 payload 中返回的 normalized `items` 数量
- `truncated = true` 表示还有更多匹配项已知或高度可推定存在，但未包含在当前 payload 中
- V1 不要求单独的 `total_count`，因为当前 CLI 路径拿不到精确 pre-limit total
- `truncated = true` 既可能来自 threshold overflow，也可能来自 external `limit`，或者两者同时存在

当数据源提供时，通用 hit item 推荐字段为：

- `class_name`
- `method_name`
- `descriptor`

如果可用，还推荐：

- `source_dex_path`

如果数据源没有这些字段，runner 可以省略，但不能把等价数据悄悄改成另一套字段名。

### 共享 relation 结果

`trace_callers` 和 `trace_callees` 必须共享一套 relation result schema。

这套 schema 是 common hit-list 的特化形式，保留 `count` 和 `items`，再增加 relation-specific context。

顶层必需字段：

- `relation_direction`
- `anchor`
- `count`
- `items`

item 必需字段：

- `class_name`
- `method_name`

推荐字段：

- `descriptor`
- `source_dex_path`

示例：

```json
{
  "relation_direction": "callers",
  "anchor": {
    "class_name": "com.example.Target",
    "method_name": "login",
    "descriptor": "(Ljava/lang/String;)Z"
  },
  "count": 2,
  "items": [
    {
      "class_name": "com.example.EntryActivity",
      "method_name": "onClick",
      "descriptor": "(Landroid/view/View;)V"
    }
  ]
}
```

### Export-and-scan 结果

用于基于 `export_and_scan` 的步骤。

必需字段：

- `export_path`
- `kind`
- `scope`

推荐字段：

- `branch_line_count`
- `return_line_count`
- `strings`
- `numbers`
- `method_calls`
- `field_accesses`

规范化说明：

- 当前 helper 输出使用 camelCase，例如 `exportPath`、`branchLineCount`、`returnLineCount`、`methodCalls`、`fieldAccesses`
- V1 runner 输出必须统一映射成这里定义的 schema key names

## 分析结果 Schema

runner 必须为整个 run 输出一个最终结构化结果。

顶层必需字段：

- `schema_version`
- `run_id`
- `status`
- `task_type`
- `artifact_root`
- `plan`
- `step_results`
- `summary`
- `artifacts`
- `recommendations`

推荐顶层字段：

- `started_at`
- `finished_at`
- `limits`
- `evidence`

允许的 run `status`：

- `ok`
- `empty`
- `input_error`
- `execution_error`
- `unsupported`
- `ambiguous`

含义：

- `ok`：执行成功并产出非空结果
- `empty`：执行成功但没有 hits
- `input_error`：输入或 preflight 校验失败
- `execution_error`：计划中的 tool 或 command 失败
- `unsupported`：task type 或 input 组合超出 V1 支持范围
- `ambiguous`：需要精确目标时却找到了多个候选

示例：

```json
{
  "schema_version": "1",
  "run_id": "2026-04-08T02-00-00Z-001",
  "status": "ok",
  "task_type": "search_methods_by_string",
  "artifact_root": "<run-root>",
  "plan": {},
  "step_results": [],
  "summary": {
    "text": "Found 3 matching methods.",
    "style": "partial_support"
  },
  "artifacts": [],
  "limits": [
    "only direct query matching"
  ],
  "evidence": [],
  "recommendations": []
}
```

### Summary schema

必需字段：

- `text`
- `style`

推荐字段：

- `highlights`

### Recommendation schema

当 planner 或 runner 需要建议一个更窄的 follow-up，而不是自动扩搜索时，使用这一结构。

必需字段：

- `kind`
- `message`

推荐字段：

- `reason`
- `suggested_filters`

示例：

```json
{
  "kind": "narrow_search",
  "message": "Too many direct hits. Narrow by package or declaring class.",
  "reason": "max_direct_hits_exceeded",
  "suggested_filters": {
    "search_package": [
      "com.example.feature"
    ],
    "declared_class": "com.example.Target"
  }
}
```

### Evidence schema

Evidence 必须是 machine-readable 且可追溯到具体 step 和 artifact。

必需字段：

- `step_id`
- `kind`
- `value`

推荐字段：

- `source_path`
- `line_numbers`
- `notes`

示例：

```json
{
  "step_id": "step-1",
  "kind": "string_hit",
  "value": "hello-needle",
  "source_path": "<run-root>/exports/Target.smali",
  "line_numbers": [
    47
  ],
  "notes": "Observed in the exported method body."
}
```

final result 中的 evidence 应能回答：

- 哪个 step 产出了该 hit
- 哪个 class 或 method 匹配了
- 分析的是哪个导出文件
- 观察到了哪些 strings、numbers 或 method calls

## Artifact 规则

artifact 处理从第一版就必须显式化。

必需规则：

- 所有生成的 artifact 都必须位于可预测的临时根目录下
- 导出的文件必须在结构化输出中被引用
- runner 不得隐藏 artifact 位置
- 早期版本可以默认保留临时目录，便于调试

artifact 必需字段：

- `type`
- `path`
- `produced_by_step`

V1 预留的 artifact `type`：

- `run_root`
- `exported_code`
- `step_result`

run 级 artifact 布局：

- `<tmp-root>/<run-id>/`
- `<tmp-root>/<run-id>/exports/`
- `<tmp-root>/<run-id>/results/`

V1 规则：

- 这套布局由 runner 持有
- 只要预期会产出 artifact，runner 就应尽量向 helper 传显式 output path 或 output dir
- 默认保留 artifacts
- 通过顶层 `artifact_root` 暴露 run artifact root
- 在 artifact 用法尚未稳定前，避免隐藏式清理

step-to-step 数据规则：

- 后续 step 可以引用前一步产出的 artifact 路径
- 后续 step 不得原地修改或重解释前一步的 step result

## 失败语义

不同失败模式必须被清楚区分。

### `input_error`

当失败原因是输入本身格式错误、缺失、不可读或内部不一致时，使用 `input_error`。

例如：

- 缺少必需参数
- 输入不可读
- 输入类型无效
- 方法任务缺少 `method_anchor`
- 文件路径不存在

### `empty`

执行成功但产出 0 个 hit 时，使用 `empty`。

### `execution_error`

计划中的 tool 或 command 失败时，使用 `execution_error`。

例如：

- CLI failure
- export failure
- helper-script failure

runner 应保留 command、stderr 和当前 artifact 状态。如果失败前 stdout 中已经带有部分 machine-readable 输出，也可以一并保留下来用于诊断。

### `unsupported`

当请求本身格式正确，但超出 V1 能力或 task / input 支持策略时，使用 `unsupported`。

例如：

- `summarize_method_logic` 搭配 APK 输入
- 任意任务使用外部 `exported_code` 输入
- 超出初始任务集的请求

### `ambiguous`

当选定执行路径下目标不够精确时，使用 `ambiguous`。

例如：

- 某个 method anchor 解析成多个 overload
- 某个任务要求精确目标，但候选集过宽

## 任务策略

V1 应把 task type 映射到固定策略。

### `search_methods_by_string`

步骤：

- 一个 `run_find` step

matcher 与 target：

- target: `method`
- key matcher: `usingStrings`

输入限制：

- 允许的 input kinds 由 V1 任务与输入矩阵定义

语义限制：

- 当 `max_direct_hits` 超限时不自动扩搜索
- threshold detection 应依赖内部 over-fetch window，而不是依赖当前并不存在的 helper total count
- 一旦 overflow，V1 可以返回 truncated preview 加 recommendation，但不能把它当成完整结果集

结果形状：

- common hit-list result

### `search_methods_by_number`

步骤：

- 一个 `run_find` step

matcher 与 target：

- target: `method`
- key matcher: `usingNumbers`

输入限制：

- 允许的 input kinds 由 V1 任务与输入矩阵定义

语义限制：

- 当 `max_direct_hits` 超限时不自动扩搜索
- threshold detection 应依赖内部 over-fetch window，而不是依赖当前并不存在的 helper total count
- 一旦 overflow，V1 可以返回 truncated preview 加 recommendation，但不能把它当成完整结果集

结果形状：

- common hit-list result

### `summarize_method_logic`

步骤：

- 一个 `export_and_scan` step

matcher 与 target：

- 默认导出语言：`smali`
- 默认扫描模式：`summary`

输入限制：

- 允许的 input kinds 由 V1 任务与输入矩阵定义
- 必须且只能有一个 dex path

语义限制：

- 只扫描一个导出 class，以及可选的 method-name scope
- 仅提供 direct body-level analysis
- 如果 method anchor 无法在当前路径下精确解析，返回 `ambiguous`
- descriptor-aware 请求不得被静默降级成“只按 method name 执行”的结果宣称
- V1 不会在声明之外偷偷插入额外 overload-resolution step 来增强 `export_and_scan`

结果形状：

- export-and-scan result

### `trace_callers`

步骤：

- 一个 `run_find` step

matcher 与 target：

- target: `method`
- key matcher: `invokeMethods`

输入限制：

- 允许的 input kinds 由 V1 任务与输入矩阵定义

语义限制：

- V1 只支持 direct callers
- 输出 items 是 anchor 的 candidate caller methods
- `relation_direction = "callers"`
- 这是受 matcher 约束的 direct relation search，不是完整 call graph expansion
- descriptor-aware 请求必须先精确解析 target，才能返回 precise caller 结果
- V1 不会在声明之外偷偷增加额外 resolution step 来辅助 `run_find`

结果形状：

- shared relation result

### `trace_callees`

步骤：

- 一个 `run_find` step

matcher 与 target：

- target: `method`
- key matcher: `callerMethods`

输入限制：

- 允许的 input kinds 由 V1 任务与输入矩阵定义

语义限制：

- V1 只支持 direct callees
- 输出 items 是在 anchor 约束下的 candidate callee methods
- `relation_direction = "callees"`
- 这是受 matcher 约束的 direct relation search，不是完整 call graph expansion
- descriptor-aware 请求必须先精确解析 target，才能返回 precise callee 结果
- V1 不会在声明之外偷偷增加额外 resolution step 来辅助 `run_find`

结果形状：

- shared relation result

## 输出 contract

每次高层分析 run 应支持两层输出：

1. 结构化输出
   - plan
   - step results
   - artifact paths
   - machine-readable hits 与 summaries
2. 人类可读输出
   - 简短 summary
   - key evidence
   - explicit limits

V1 命令 contract：

- `analyze.py` 的 stdout 默认应是 machine-readable
- JSON 是默认且主要的 stdout contract
- 如果需要人类可读模式，应通过显式 output mode 提供，而不是在默认 stdout 上混排 JSON 与 prose

V1 CLI 形状：

- 使用显式 subcommands，而不是一个过载的 free-form entrypoint
- 推荐顶层形式：
  - `analyze.py plan --task-type ... --input-json ...`
  - `analyze.py run --task-type ... --input-json ...`
- 如果以后再增加紧凑形式，也必须只是同一 planner / runner contract 之上的兼容壳

推荐示例：

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py plan \
  --task-type search_methods_by_string \
  --input-json '{"input":["/path/to/classes.dex"],"string":"login"}'
```

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run \
  --task-type summarize_method_logic \
  --input-json '{"input":["/path/to/classes.dex"],"method_anchor":{"class_name":"com.example.Target","method_name":"login"}}'
```

最小示例要求：

- 文档最终应至少包含一份 concrete final-result JSON example，对应：
  - `search_methods_by_string`
  - `trace_callers`
  - `summarize_method_logic`
- 这些示例必须基于真实 helper 输出观察结果，而不是凭空发明字段

## 推荐文件放置

第一版实现推荐使用：

- `skills/dexclub-cli-launcher/analyst/scripts/plan_schema.py`
- `skills/dexclub-cli-launcher/analyst/scripts/planner.py`
- `skills/dexclub-cli-launcher/analyst/scripts/runner.py`
- `skills/dexclub-cli-launcher/analyst/scripts/analyze.py`

## 验证计划

在把 planner layer 视为稳定之前，V1 至少应满足以下验证门槛：

1. 语法校验
   - 对新增 analyst scripts 运行 `python3 -m py_compile`
2. planner contract 校验
   - 确认 `plan` 输出是合法 JSON，且 stdout 不混入 prose
3. runner contract 校验
   - 确认 `run` 输出是合法 JSON，并包含 `plan`、`step_results`、`summary`、`artifact_root`
4. task 覆盖校验
   - 对五个支持任务类型各跑一条小型端到端样例
5. threshold 校验
   - 确认 direct-search tasks 能通过 internal over-fetch window 探测 overflow，并发出 narrowing recommendation
6. ambiguity 校验
   - 确认 overload-ambiguous 的 method target 会返回 `ambiguous`，而不是静默选择一个候选

在早期版本中，这些验证 artifact 应默认保留，便于失败时直接排查。

## 第一版实现顺序

推荐顺序：

1. `plan_schema.py`
2. `planner.py`
3. `runner.py`
4. `analyze.py`
5. analyst 文档更新
6. 小样本 dex 的端到端验证

## V1 已固定的决策

- 使用统一的 normalized input model。
- 使用统一的 normalized method anchor model。
- V1 外部任务字段统一使用 `input`。
- 每次 run 输出一个最终 analysis result object。
- 通过顶层 `artifact_root` 暴露 run artifact root。
- 通过顶层 `recommendations` 统一暴露 threshold 后的 follow-up guidance。
- artifacts 默认保留。
- method target 不够精确时，返回 `ambiguous`，而不是猜测。
- summary 文案风格使用 `partial_support`，不使用 `best_effort`。
- V1 的 `analyze.py` 使用显式 subcommands。
- `trace_callers` 与 `trace_callees` 共用一套 relation result schema，用 `relation_direction` 区分方向。

## 当前建议

继续沿受限的第一版实现推进。

在以下条件未稳定前，不应扩展任务范围：

- schema 稳定
- step result handling 稳定
- artifact handling 可预测
- 前五个任务类型表现一致
