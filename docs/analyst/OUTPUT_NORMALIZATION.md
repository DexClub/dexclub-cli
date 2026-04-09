# Analyst 输出净化与结果契约

## 目的

这份文档用于明确 `skills/dexclub-cli-launcher/analyst` 在执行底层 CLI、DexKit、JADX 或其他子进程时，如何处理原始输出，以及 analyst 层对外暴露什么样的稳定结果契约。

除非 [ANALYST_PROGRESS.md](./ANALYST_PROGRESS.md) 已明确记录对应事项为“已完成”，否则本文档中的目录、对象和流程应视为目标收敛方案，不代表当前代码已经全部按此实现。

它不讨论任务规划，也不试图覆盖完整存储系统设计，只聚焦一件事：

- 如何让 analyst 的 `stdout` 成为稳定、可机器消费、不会被第三方日志污染的正式输出

为支撑这件事，文档会补充最小必要的目录、索引和结果对象约定；但这些约定只服务于输出净化、结果契约和跨会话接续，不展开更宽泛的存储体系设计。

## 背景

当前 analyst 层会调用多类底层实现：

- `dexclub-cli`
- DexKit native bridge
- `jadx-core`
- analyst 内部 helper script

这些实现的输出行为并不完全受 analyst 控制。

已确认的现实问题包括：

- DexKit 在非 Android 宿主下会把 `I/DexKit: ...` 这类日志直接写到 `stdout`
- 某些 helper 在 `--format json` 下仍会先输出前缀文本，再输出 JSON
- runner 和部分脚本已经不得不通过“从 stdout 里猜 JSON 起点”的方式容错

这说明当前 analyst 对外输出还不是“正式契约”，而更像“底层输出的拼接结果”。

## 设计目标

1. `--format json` 时，analyst 的 `stdout` 必须是纯净 JSON。
2. `--format text` 时，analyst 的 `stdout` 只承载面向用户的正文，不混入底层诊断噪音。
3. 底层原始输出不能丢，但要从正式输出路径中分离出来。
4. helper、runner、workflow 应共享一套统一的输出处理模型，而不是各自做临时补救。

## 非目标

下面这些不属于本文档要解决的范围：

- 修改上游 DexKit 或 JADX 的默认日志行为
- 改变 CLI 的功能边界
- 设计一套独立于输出契约之外的完整存储体系
- 扩展新的 analyst 任务类型

## 阅读建议

这份文档覆盖范围较大，建议按下面顺序阅读：

1. 先读下方“核心决策摘要”
2. 再读“核心原则”“输出模式契约”“统一处理模型”
3. 再读“工作区目录定稿”“跨会话复用与接续规则”
4. 再读“`partial` 与可接手资产判定”“按 `task_type` 定义完成条件”
5. 最后读“Schema 与轻量代码约束策略”“公共执行器模块草案”

## 核心决策摘要

如果只需要把当前方向快速对齐到实现口径，先看这一节即可。

### 1. 输出净化

- 第三方 CLI、DexKit、JADX、子进程脚本的 `stdout/stderr` 一律视为不可信原始流。
- analyst 是结果网关，不是透传器。
- `--format json` 时，`stdout` 只能输出一个正式 JSON 文档；日志和诊断信息不得混入。
- 原始 `stdout/stderr` 不丢弃，统一归档到 step 级产物中。

### 2. 工作区落点

- `release` 缓存可以继续保留全局缓存。
- 除此之外的 skill 内部状态统一放当前工作区下的 `.dexclub-cli/`。
- 用户可见产物统一落到工作区下的 `artifacts/`、`reports/`、`patched/`。
- 不再使用 `build/dexclub-cli/` 作为 analyst 工作根。

### 3. run / step 组织

- `run-root` 只承载 run 级对象，例如 `run-meta.json`、`run-summary.json`。
- `step-root` 负责聚合单步结果、原始日志和局部附属文件。
- 原始日志、`step-result.json`、step 附属 artifacts 都按 step 聚合，不再在 run 根平铺 `results/`、`resolved/`、`logs/`。

### 4. 跨会话接续

- `step_id` 只解决 run 内定位和排障，不承担跨会话复用主键职责。
- 机器复用依赖内容与参数指纹，也就是 `cache key`。
- 无上下文新会话的接手依赖 `latest.json + run-summary.json`。
- 方案支持“断会话后可恢复”，但不默认等同于“自动续跑”。

### 5. run 状态聚合

- `partial` 只用于 run 级状态。
- `partial` 的定义是“未完成，但已留下可接手资产”。
- `ok` 不能靠全局固定步骤列表判断，而应按 `task_type` 定义：
  - `required_step_kinds`
  - `success_condition`
  - `empty_is_success`

### 6. 用户可见产物提升

- 所有 step 结果默认先完整留在 `.dexclub-cli/`。
- 只有值得人直接复核、直接引用、下次还会直接查看的结果，才提升到 `artifacts/` 或 `reports/`。
- 提升决策应主要由 workflow / run 层负责，而不是让每个 helper 自由写入工作区可见目录。

### 7. 约束与模块边界

- 当前阶段先采用“文档约定 + 轻量 validator + `schema_version`”，暂不上重型正式 schema。
- `process_exec.py` 只负责执行、捕获、raw log、payload 提取。
- `output_contract.py` 负责 `latest.json`、`run-summary.json`、`step_index`、`key_artifact` 等输出契约校验。
- `runner.py` 和各 helper 负责生产对象，并在写出前调用 validator。

## 核心原则

### 1. 第三方输出不可信

来自底层 CLI、原生库、反编译库、子进程脚本的 `stdout` / `stderr` 都视为原始流，不直接等同于 analyst 的正式结果。

### 2. Analyst 是结果网关，不是透传器

analyst 层的职责不是把底层输出原样转发给用户或下游脚本，而是：

- 捕获原始输出
- 解析有效载荷
- 归档原始流
- 重新组织正式结果
- 按统一契约重新输出

### 3. 正式输出只能由 helper 自己生成

只有 helper 或 runner 在完成解析和归一化之后重新写到 `stdout` 的内容，才算 analyst 的正式输出。

## 输出模式契约

### JSON 模式

当 `--format json` 生效时：

- `stdout` 必须且只能输出一个完整 JSON 文档
- 不允许输出任何前缀文本、提示文本、日志行、进度行
- 即使底层返回错误，只要 helper 仍在对外提供结构化结果，也应优先输出结构化错误 JSON

### Text 模式

当 `--format text` 生效时：

- `stdout` 只输出正文
- `stderr` 承载诊断、建议、原始日志摘录或调试信息
- 不要求 `stdout` 可直接机器解析，但仍不应混入随机底层日志

## 统一处理模型

所有会执行底层命令的 helper，统一遵循下面的流程：

1. 使用捕获模式执行子进程，不直通底层 `stdout`
2. 保存原始 `stdout` / `stderr`
3. 从原始输出中提取有效 payload
4. 归一化成 analyst 约定的数据结构
5. 将原始流写入 artifact
6. 根据 `--format` 重新生成正式输出

这意味着：

- “从 stdout 中猜 JSON 起点”的逻辑只能存在于内部兼容层
- 一旦对外输出，必须已经完成净化

## 原始流归档要求

每次执行底层命令时，都应允许追溯原始流。建议至少保存：

- `raw_stdout`
- `raw_stderr`
- `command`
- `exit_code`

推荐落盘形式：

- `.dexclub-cli/runs/v1/<run-id>/steps/<step-id>/step-result.json`
- `.dexclub-cli/runs/v1/<run-id>/steps/<step-id>/raw.stdout.log`
- `.dexclub-cli/runs/v1/<run-id>/steps/<step-id>/raw.stderr.log`

如果当前实现阶段不方便拆成多个文件，至少也应在结果 JSON 中提供原始流 artifact 路径。

## 结果对象总览

为避免每个脚本各写一套字段名，正式结果建议统一包含以下信息：

- `status`
- `result`
- `diagnostics`
- `artifacts`
- `raw_process`

其中：

- `diagnostics`
  - 放 analyst 归纳后的诊断结论
  - 不直接塞整段原始 stdout/stderr
- `raw_process`
  - 放底层执行元信息
  - 例如 `command`、`exit_code`、`stdout_path`、`stderr_path`

## 统一执行器返回对象

为避免 `run_find.py`、`resolve_apk_dex.py`、`export_and_scan.py` 和 `runner.py` 各自维护一套执行结果结构，建议先定义 analyst 内部统一对象：

- `ProcessExecutionResult`

它是 helper / runner 内部消费的工程对象，不等同于最终写到 `stdout` 的对外 JSON。

这里描述的是长期目标对象形状；若后续分阶段实现，第一版公共执行器可以只先落其中的稳定子集，具体边界见后文“公共执行器模块草案”。

### 顶层字段

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `step_id` | 是 | `str` | 当前执行步骤的稳定标识 |
| `status` | 是 | `str` | `ok | empty | input_error | execution_error | normalization_error` |
| `exit_code` | 是 | `int` | 子进程退出码 |
| `command` | 是 | `list[str]` | 实际执行命令，保留原始参数顺序 |
| `started_at` | 是 | `str` | UTC ISO-8601 时间 |
| `finished_at` | 是 | `str` | UTC ISO-8601 时间 |
| `duration_ms` | 是 | `int` | 执行总耗时 |
| `raw_process` | 是 | `object` | 原始流和落盘位置 |
| `payload_kind` | 是 | `str` | `json | text | none`，表示预期载荷类型 |
| `payload` | 否 | `object` | 从 stdout 提取出的原始载荷 |
| `result` | 否 | `object` | 归一化后的结果对象 |
| `diagnostics` | 是 | `object` | analyst 归纳后的诊断信息 |
| `artifacts` | 是 | `list[object]` | 本步骤产生的 artifact 列表 |

### `raw_process` 字段

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `stdout_path` | 是 | `str` | 原始 stdout 落盘路径 |
| `stderr_path` | 是 | `str` | 原始 stderr 落盘路径 |
| `stdout_size` | 否 | `int` | stdout 字节数 |
| `stderr_size` | 否 | `int` | stderr 字节数 |

### `diagnostics` 字段

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `message` | 是 | `str` | 当前步骤的人类可读结论 |
| `cause` | 否 | `str` | 失败或异常原因 |
| `next_action` | 否 | `str` | 建议的下一步动作 |
| `notes` | 否 | `list[str]` | 补充说明 |

### 状态语义

- `ok`
  - 命令成功，且提取、归一化成功，结果非空
- `empty`
  - 命令成功，提取成功，但业务结果为空
- `input_error`
  - 输入参数、路径、前置条件错误，通常无需重试
- `execution_error`
  - 子进程执行失败，退出码非零
- `normalization_error`
  - 子进程成功，但 stdout 不符合预期格式，或归一化过程失败

## 对外 JSON 结果对象

helper 在 `--format json` 下最终写到 `stdout` 的正式结果，建议统一为一层更精简的对外对象。

它应当稳定、可机器消费，但不需要暴露所有内部执行细节。

### 顶层字段

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `status` | 是 | `str` | 对外状态，默认沿用内部状态 |
| `result` | 是 | `object | list | null` | 业务结果主体 |
| `diagnostics` | 是 | `object` | 面向用户和下游的诊断信息 |
| `artifacts` | 是 | `list[object]` | 可复核产物路径 |
| `input_source` | 否 | `str` | `apk_direct | apk_cached_extracted_dex | workspace_dex_set` |
| `cache_hit` | 否 | `bool` | 是否命中缓存 |
| `raw_process` | 否 | `object` | 默认保留精简版执行元信息 |

### 设计约束

1. 对外 JSON 默认不直接暴露内部 `payload`
2. 对外字段统一使用 `snake_case`
3. 与下游消费强相关的信息必须进入结构字段，而不是前缀文本

## `artifacts` 字段对象形状

为避免各脚本对 artifact 描述方式不一致，建议统一为：

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `type` | 是 | `str` | `step_result | exported_code | raw_stdout | raw_stderr | resolved_dex | run_root` 等 |
| `path` | 是 | `str` | 绝对路径 |
| `produced_by_step` | 否 | `str` | 来源步骤 |

## 高频脚本的 `result` 约束

为降低下游分支判断成本，建议对高频 helper 补充以下约束：

- `run_find.py`
  - `result` 始终为列表
- `resolve_apk_dex.py`
  - `result` 始终为对象
  - 至少包含 `candidate_dex_paths`、`resolved_dex_path`
- `export_and_scan.py`
  - `result` 始终为对象
  - 至少包含 `export_path`、`scope` 以及分析摘要字段

## 错误结果最小骨架

为了让失败路径也保持稳定契约，建议在 `json` 模式下至少输出如下结构：

```json
{
  "status": "execution_error",
  "result": null,
  "diagnostics": {
    "message": "步骤执行失败",
    "cause": "底层命令返回非零退出码",
    "next_action": "检查 raw stderr 或修正输入参数"
  },
  "artifacts": [],
  "raw_process": {
    "stdout_path": "...",
    "stderr_path": "..."
  }
}
```

这份骨架不是要求所有脚本字面值完全一致，而是要求字段语义保持一致。

对于 analyst 自己可以明确归因的失败，还建议同时保持下面的约束：

- 进程退出码仍然保留非零
- `stdout` 输出结构化错误 JSON
- `stderr` 可补充原始诊断信息

错误结果至少应包含：

- `status`
- `message`
- `cause`
- `next_action`
- `raw_process`

这样下游既能保留 shell 语义，也能稳定读取错误结构。

## JSON 模式下的字段约束

凡是会影响下游消费的重要信息，都必须以 JSON 字段提供，而不是打印成前缀文本。

典型字段包括但不限于：

- `export_path`
- `cache_root`
- `artifact_root`
- `input_source`
- `cache_hit`
- `resolved_dex_path`

不再允许下面这类形式出现在 `json` 模式的 `stdout` 中：

- `output=/tmp/...`
- `cache_root=...`
- `resolved_dex_path=...`

## 输入来源可见性

为避免“明明已有 dex 集合却又回退到 APK 解析”这类问题在结果层不可见，正式输出应明确标注本次实际使用的输入来源，例如：

- `apk_direct`
- `apk_cached_extracted_dex`
- `workspace_dex_set`

这类信息应进入结构化结果，而不是只存在中途 commentary 或临时日志里。

## 工作区目录定稿

### 总原则

当前路线明确约束如下：

1. `release` 缓存继续保留在全局缓存体系内
2. 除 `release` 缓存外，skill 产生的内部状态必须工作区内聚
3. 用户可见产物与 skill 内部状态分层存放
4. 不再使用 `build/dexclub-cli/` 作为 analyst 工作根

### 目标目录结构

```text
<workspace>/
  .dexclub-cli/
    cache/
      v1/
        inputs/
    runs/
      v1/
        <run-id>/
          run-meta.json
          run-summary.json
          steps/
            <step-id>/
              step-result.json
              raw.stdout.log
              raw.stderr.log
              artifacts/
  artifacts/
  reports/
  patched/
```

这里的 `<workspace>` 指当前工作目录。例如：

- `/root/termux-home/demo/`
- `/root/termux-home/demoAx/`
- `/root/termux-home/demoB/`

不同工作目录之间的 analyst 内部状态、输入缓存和运行产物必须天然隔离。

### 各目录职责

#### `.dexclub-cli/`

这是 skill 的内部状态目录，用于存放：

- 输入缓存
- run 元数据
- step 级执行结果
- raw stdout / stderr
- 内部索引和后续扩展状态

它不是给用户日常手工浏览的主目录，因此优先追求边界清晰、可复用、可排障，而不是“像构建产物目录一样平铺”。

#### `artifacts/`

用于存放可复核的导出物和中间分析文件，例如：

- 导出的 `.smali`
- 导出的 `.java`
- 导出的单类 dex
- 用户明确要求保留的中间分析文件

#### `reports/`

用于存放更偏结论和汇总的输出，例如：

- 结构化分析摘要
- IOC 提取结果
- 方法逻辑报告
- 调用链汇总

#### `patched/`

用于存放修补、重打包、签名等修改性产物。

### 为什么去掉 `build/`

不再使用 `build/dexclub-cli/` 的主要原因是：

- `build/` 更像 Gradle 构建产物目录，不适合作为 skill 内部状态目录
- 它容易让人误以为这些内容只是“可随手清理的构建垃圾”
- 对 APK 逆向分析工作区来说，`build/` 缺少明确业务语义
- 当前工作区本来已经有 `artifacts/`、`reports/`、`patched/` 这组更贴近分析语义的目录

因此当前更推荐：

- 内部状态放隐藏目录 `.dexclub-cli/`
- 用户可见产物放 `artifacts/` / `reports/` / `patched/`

### `run-root` 与 `step-root`

`run-root` 不是整个工作目录，而是一次分析运行的实例目录：

```text
<workspace>/.dexclub-cli/runs/v1/<run-id>/
```

在这层目录下，建议只保留 run 级对象：

- `run-meta.json`
- `run-summary.json`
- `steps/`

每个 `step` 再拥有自己的局部目录：

```text
<workspace>/.dexclub-cli/runs/v1/<run-id>/steps/<step-id>/
```

建议其中包含：

- `step-result.json`
- `raw.stdout.log`
- `raw.stderr.log`
- `artifacts/`

### 为什么按 `step` 聚合

不再推荐在 `run-root` 下平铺：

- `results/`
- `resolved/`
- `logs/`

原因是：

- `resolved` 本质上只是某类 step 结果，不是一级目录语义
- `logs` 也是某个 step 的过程证据，不适合独立成全局平铺目录
- 按 `step` 聚合后，一步的结果、日志、附属文件天然闭环，更利于排障和后续扩展

### 产物落点规则

建议固定以下规则：

1. 原始过程证据
   - 统一落到 `step-root`
2. step 归一化结果
   - 统一落到 `step-root/step-result.json`
3. helper 内部附属文件
   - 默认落到 `step-root/artifacts/`
4. 面向用户复核的导出物
   - 复制、移动或显式写入到工作区的 `artifacts/`、`reports/`、`patched/`

一句话概括：

- `.dexclub-cli/` 解决内部状态组织
- `artifacts/` / `reports/` / `patched/` 解决用户可见产物落点

## 跨会话复用与接续规则

### 总原则

当前更推荐的做法不是再新起一套落地方案，而是在既有目标目录方案上补齐“复用”和“接续”两层能力。

这三层职责应明确拆开：

1. `step_id`
   - 用于 run 内步骤定位和排障
2. `cache key`
   - 用于机器复用中间结果
3. `latest.json + run-summary.json`
   - 用于无上下文新会话接续

不能把这三种职责混在一个标识上。

### `step_id` 的职责边界

`step_id` 主要解决：

- 同一 run 内这一步叫什么
- 这一步的日志、结果、附属文件放在哪
- 排障时如何快速定位某一步

`step_id` 不承担下面这些职责：

- 跨 run 缓存复用主键
- 跨会话结果命中主键
- 内容相等性的唯一判定

### 为什么 `step_id` 不能承担复用主键

即使新开一个没有上下文历史的会话，真正决定“能不能复用”的也不是 `step_id`，而是：

- APK 内容是否相同
- dex 内容是否相同
- 类名是否相同
- 方法 descriptor 是否相同
- 导出语言是否相同
- 分析模式是否相同

因此：

- `step_id` 适合描述步骤语义
- `cache key` 才适合描述复用身份

### 机器复用层

跨会话复用的主键，建议统一放在：

```text
<workspace>/.dexclub-cli/cache/v1/
```

这里的缓存对象不按 `step_id` 组织，而按内容和参数指纹组织。

推荐主键输入包括但不限于：

- APK hash
- dex hash
- 类名
- 方法 descriptor
- 导出语言
- 分析模式

一句话约束：

- 缓存命中依赖内容和参数指纹，不依赖 `run-id` 或 `step-id`

### 新会话接续层

如果新开一个没有聊天上下文的新会话，代理仍应能快速找到最近一次有效运行。

为此建议补两类 run 索引：

```text
<workspace>/.dexclub-cli/runs/v1/latest.json
<workspace>/.dexclub-cli/runs/v1/<run-id>/run-summary.json
```

#### `latest.json`

职责：

- 指向最近一次有效 run
- 作为新会话进入工作区时的默认发现入口

建议至少包含：

- `run_id`
- `run_root`
- `summary_path`
- `updated_at`
- `status`
- `selection_reason`

建议最小对象形状：

```json
{
  "schema_version": "v1",
  "run_id": "...",
  "run_root": "...",
  "summary_path": ".../.dexclub-cli/runs/v1/<run-id>/run-summary.json",
  "status": "ok|partial|execution_error|input_error|cancelled",
  "updated_at": "...",
  "selection_reason": "latest_active|latest_successful|latest_partial"
}
```

说明：

- `latest.json` 只负责“指向哪次 run”
- 它不负责承载 run 的详细摘要
- 新会话拿到 `latest.json` 后，下一步应进入 `run-summary.json`

#### `latest.json` 的选择策略

`latest.json` 不应机械地永远指向最后一次执行，而应优先服务“接手价值”。

更推荐的默认顺序是：

1. 最近一次 `partial` 或 `ok` 的 run
2. 如果最近 run 是纯失败且没有可复用中间结果，则回退到最近一次 `ok`
3. 只有在工作区里没有更好候选时，才指向纯失败 run

一句话约束：

- `latest.json` 是入口指针，不是时间戳最大的 run 记录

#### `run-summary.json`

职责：

- 记录这次 run 的简要完成状态
- 记录关键步骤和关键产物
- 作为新会话“快速接手”的第一入口，而不是要求代理先翻所有 `step-root`

建议至少包含：

- `run_id`
- `run_root`
- `status`
- `task_type`
- `input_source`
- `primary_inputs`
- `started_at`
- `finished_at`
- `updated_at`
- `latest_step_id`
- `latest_successful_step_id`
- `key_artifacts`
- `step_index`
- `summary`

其中：

- `key_artifacts`
  - 指向本次 run 最值得复用或复核的产物
- `step_index`
  - 提供 `step_id -> status -> path` 的索引
- `summary`
  - 给出 run 级简述，避免新会话先深入每个 step 才知道整体状态

建议最小对象形状：

```json
{
  "schema_version": "v1",
  "run_id": "...",
  "run_root": "...",
  "status": "ok|partial|execution_error|input_error|cancelled",
  "task_type": "...",
  "input_source": "apk_direct|apk_cached_extracted_dex|workspace_dex_set",
  "primary_inputs": ["..."],
  "started_at": "...",
  "finished_at": "...",
  "updated_at": "...",
  "latest_step_id": "...",
  "latest_successful_step_id": "...",
  "key_artifacts": [],
  "step_index": [],
  "summary": {
    "text": "...",
    "style": "ok|warning|error"
  }
}
```

#### `run-summary.json` 的边界

`run-summary.json` 应刻意保持“目录页”定位，不应长成第二份完整 `run result`。

明确不建议放入：

- 完整 `plan`
- 完整 `step_results`
- 原始 stdout / stderr 内容
- 大段 evidence
- 大段 recommendations
- 完整业务 payload

一句话约束：

- `run-summary.json` 负责导航和接手，不负责替代 step 细节

#### `key_artifacts` 的筛选规则

`key_artifacts` 应当被视为“接手入口清单”，而不是“全部产物目录索引”。

一句话约束：

- `artifacts` 是全部可复核产物
- `key_artifacts` 是其中最值得新会话第一眼看到的少数结果

##### 总原则

建议只有满足下面至少一个条件的产物，才进入 `key_artifacts`：

1. 能直接帮助新会话继续推进
2. 能直接支撑当前 run 的核心结论
3. 是用户最可能第一时间打开查看的文件

如果不满足这三个方向，则不建议进入 `key_artifacts`。

##### 推荐筛选维度

###### 接手价值

优先选择能让下个会话少走弯路的结果，例如：

- 已定位出的唯一 dex 结果
- 已导出的目标类代码
- 已生成的方法级摘要
- 已收敛出的调用链汇总

###### 结论价值

优先选择能支撑当前 run 核心判断的结果，例如：

- 关键查询结果汇总
- 结构化摘要
- 最终报告或中间结论报告

###### 可读价值

优先选择人类可直接打开理解的结果，例如：

- `.smali`
- `.java`
- `.json` 摘要
- `.md` 报告

不建议优先选择只适合程序消费的内部索引文件。

##### 不建议进入 `key_artifacts` 的内容

下面这些内容通常不应进入 `key_artifacts`：

- raw stdout / stderr
- 完整 `step-result.json`
- 重复可生成的候选缓存
- 空查询结果
- 纯调试文件
- 只有路径意义、没有阅读价值的中间文件
- 同类文件的全量列表

这些内容可以继续保留在 `.dexclub-cli/` 或完整 `artifacts` 列表中，但不应作为接手入口暴露。

##### 数量控制

建议为 `key_artifacts` 设置软上限：

- 默认最多 `3-7` 个

超过上限时，建议只保留最能代表当前 run 完成度和后续接手价值的结果，避免它退化成另一份目录列表。

##### 按任务类型的建议口径

###### `resolve_class_dex`

建议最多放：

- `resolved_dex_path` 对应的结论文件或摘要
- 若尚未唯一收敛，则放候选摘要而不是所有 dex 路径

###### `search_methods_by_string`

建议最多放：

- 关键查询结果汇总 report

如果命中很多，不建议把全量结果直接塞入 `key_artifacts`，更推荐放一份 summary / report。

###### `trace_callers` / `trace_callees`

建议最多放：

- 调用链汇总 report
- 若有关键命中类或方法导出，再补一个核心导出文件

###### `summarize_method_logic`

这是最适合产生 `key_artifacts` 的任务类型之一，建议优先放：

- 目标类导出代码
- 方法逻辑摘要 report
- 结构化 summary JSON

##### 类型优先级

若后续需要自动筛选，建议大致按下面优先级选择：

1. `report`
2. `structured_summary`
3. `exported_code`
4. `resolved_target`
5. `query_result_summary`

推荐原因：

- 新会话通常应先看摘要，再决定是否继续打开代码实体

##### 建议对象形状

`key_artifacts` 中的每一项建议至少包含：

- `type`
- `path`
- `label`
- `reason`

示例：

```json
{
  "type": "report",
  "path": ".../reports/handle_double_click_summary.json",
  "label": "双击点赞方法摘要",
  "reason": "本次 run 的核心结论，适合作为新会话接手入口"
}
```

一句话总结：

- `key_artifacts` 不是“最重要的文件类型集合”
- 它是“下一位接手者最该先看的文件清单”

#### `step_index` 的信息边界

`step_index` 应被定义为“步骤导航索引”，而不是“步骤详情副本”。

一句话约束：

- `step_index` 解决“有哪些 step、每步状态如何、该去哪里看”
- `step-result.json` 解决“这一步具体发生了什么”
- `key_artifacts` 解决“新会话最该先看哪些结果”

##### `step_index` 应承载的信息

建议 `step_index` 只保留足够支撑导航和粗粒度判断的字段，例如：

- `step_id`
- `step_kind`
- `status`
- `step_root`
- `attempt_count`
- `is_reusable_step`
- `is_required`

如果后续需要补充，也应优先增加“导航型字段”，而不是摘要正文。

##### 建议对象形状

```json
{
  "step_id": "02_export_and_scan_base_list_fragment_panel_handle_double_click",
  "step_kind": "export_and_scan",
  "status": "ok",
  "step_root": ".../.dexclub-cli/runs/v1/<run-id>/steps/02_export_and_scan_base_list_fragment_panel_handle_double_click",
  "attempt_count": 1,
  "is_reusable_step": true,
  "is_required": true
}
```

##### 字段说明

- `step_id`
  - run 内稳定步骤标识
- `step_kind`
  - 步骤类型，来自受控枚举
- `status`
  - 该 step 当前最终状态
- `step_root`
  - 指向 step 局部目录，供进一步排障和深入查看
- `attempt_count`
  - 当前 step 的执行次数
- `is_reusable_step`
  - 该 step 是否留下了值得后续会话复用或接手的结果
- `is_required`
  - 对当前 `task_type` 而言，该 step 是否属于必需步骤

##### 不建议进入 `step_index` 的信息

下面这些内容不建议写入 `step_index`：

- 完整 `result`
- 完整 `diagnostics`
- 原始 stdout / stderr 路径全集
- 大段错误消息
- 大段 evidence
- 详细 artifact 列表
- 结构化摘要正文

原因是：

- 这些内容都已经有更合适的承载位置
- 一旦塞进 `step_index`，它很快会退化成另一份压缩版 `step-result.json`

##### 与其他结构的分工

建议固定为下面的职责边界：

- `step_index`
  - 给 run-summary 提供步骤导航和状态总览
- `key_artifacts`
  - 给新会话和用户提供“先看什么”的入口
- `step-result.json`
  - 提供完整步骤细节和机器可消费结果

##### 数量和粒度控制

`step_index` 应覆盖当前 run 的全部 step，但每个 step 项应保持轻量。

推荐做法是：

- 覆盖全量 step
- 只保留稳定、简短、可索引的字段
- 如需深入，再跳转到 `step_root` 中查看详情

### 三层关系总结

建议固定成下面的分工：

- `step_id`
  - 解决“这一步是谁”
- `cache key`
  - 解决“这一步能不能复用”
- `latest.json + run-summary.json`
  - 解决“新会话该从哪次 run 开始接手”

### 目标效果

在这套分工下，一个没有上下文历史的新会话进入工作区后，应至少具备下面的能力：

1. 通过 `latest.json` 快速定位最近一次有效 run
2. 通过 `run-summary.json` 快速理解这次 run 做了什么、成功到哪一步
3. 通过 `cache/` 下的内容指纹复用已有中间结果
4. 如需排障，再进入具体 `step-root` 查看原始 stdout / stderr 和 step 结果

### 对断会话恢复的支持边界

这套设计应显式支持下面这种现实场景：

- 上一个会话做到一半突然中断
- 新开一个没有聊天上下文的新会话
- 仍希望在当前工作目录中继续利用已有进度

当前推荐边界是：

- 支持“可恢复”
- 不默认等同于“自动续跑”

#### 系统负责的部分

系统应负责把可接手状态保存并暴露出来，包括：

- 最近一次可接手 run 的入口
- run 级摘要
- step 级结果和原始过程证据
- 可复用缓存和中间结果

#### 用户或新会话负责的部分

用户或新会话仍需决定：

- 这次是否继续同一个目标
- 是否沿用上一次 run 的方向
- 是继续执行，还是只把旧结果当参考

#### 为什么不做“默认自动续跑”

因为没有聊天上下文时，系统无法可靠判断：

- 用户是不是仍想继续同一个任务
- 上一次 run 是否已经跑偏
- 当前会话是要接着做，还是仅查看已有结论

因此更合理的默认行为应是：

1. 自动发现最近一次可接手 run
2. 自动暴露 `latest.json` 和 `run-summary.json`
3. 把“是否继续执行”留给当前会话的任务判断

一句话约束：

- 这套方案支持断会话恢复，但不把恢复等同于自动续跑

### `partial` 与可接手资产判定

为避免 `latest.json` 的选择策略和 run 级状态聚合在实现时发散，建议把 `partial` 明确定义为：

- 这次 run 没有完整完成目标
- 但已经留下了可复用、可接手的中间结果

一句话定义：

- `partial` = 未完成，但已留下可接手资产

#### 判定层级

`partial` 只用于 run 级状态，不用于 step 级状态。

建议分层如下：

- step 级状态
  - `ok`
  - `empty`
  - `ambiguous`
  - `unsupported`
  - `input_error`
  - `execution_error`
  - `normalization_error`
- run 级状态
  - `ok`
  - `partial`
  - `input_error`
  - `execution_error`
  - `cancelled`

#### `partial` 的最小判据

一个 run 判成 `partial`，建议至少同时满足：

1. 最终不能判成 `ok`
2. 至少存在一个“可接手资产”

如果没有任何可接手资产，则不应为了“做过一些事”而判成 `partial`。

#### 什么是可接手资产

这里的“可接手资产”指对下一次会话继续推进任务有实际价值的结果，而不是单纯存在原始日志或空目录。

推荐至少包括以下几类：

- 已成功解析出的唯一 dex 或候选 dex
- 已成功导出的 `.smali` / `.java` / 单类 dex
- 已产生非空查询结果列表
- 已生成结构化摘要、分析报告或关键结果文件
- 已明确记录下一跳信息，例如最近成功 step 和关键 artifact

#### 不应算作可接手资产的情况

下面这些情况不建议单独支撑 `partial`：

- 只有 raw stdout / stderr，没有业务结果
- 只有空目录或空文件
- 只有打印帮助、环境探测、无推进价值的准备动作
- 第一步就因输入错误或执行错误而终止，且没有任何成功产物

#### 建议的 step 级复用标记

为降低 run 状态聚合时的歧义，建议让每个 step 在归一化结果中显式输出一个内部标记：

- `is_reusable_step`

它不一定暴露给最终用户，但应作为 run 级状态聚合和 `latest.json` 选择的重要依据。

一句话约束：

- `is_reusable_step` 判断的是“这一步结果是否值得后续会话继续接手”

#### 按 step-kind 的建议规则

##### `resolve_apk_dex`

建议判为 `is_reusable_step = true` 的情况：

- 找到唯一 `resolved_dex_path`
- 没找到唯一结果，但 `candidate_dex_paths` 非空

建议判为 `false` 的情况：

- 没有任何候选 dex
- 仅有原始日志，无解析结果

##### `run_find`

建议判为 `is_reusable_step = true` 的情况：

- 返回非空结果列表
- 返回 `ambiguous`，但候选列表本身非空且有继续分析价值

建议判为 `false` 的情况：

- 结果为空
- 只有参数错误或执行错误，没有任何查询结果

##### `export_and_scan`

建议判为 `is_reusable_step = true` 的情况：

- 导出成功，且存在 `export_path`
- 已生成结构化摘要或范围明确的扫描结果

建议判为 `false` 的情况：

- 导出失败，且未留下可复核导出物
- 只有 raw log，没有导出代码和分析结果

##### 其他 step-kind

对于后续新增 step，建议统一遵循这条判断原则：

- 这一步是否留下了下一次会话无需重做即可继续利用的结果

如果答案是“是”，则倾向于 `is_reusable_step = true`。

#### run 级状态聚合建议

建议 run 状态按下面顺序聚合：

1. 如果所有必需步骤已完成，判为 `ok`
2. 否则，如果存在至少一个 `is_reusable_step = true` 的 step，判为 `partial`
3. 否则，如果首个阻断原因为输入问题，判为 `input_error`
4. 其余失败情况，判为 `execution_error`

#### 与 `latest.json` 的关系

在这套规则下，`latest.json` 更适合优先指向：

- 最近一次 `ok`
- 或最近一次 `partial`

因为这两类 run 都具有实际接手价值。

相反，纯失败且没有任何可接手资产的 run，不应优先作为默认入口。

### 按 `task_type` 定义完成条件

为避免 `ok / partial` 在不同任务上出现漂移，建议不要维护一份全局固定的“必需步骤名单”，而应按 `task_type` 定义任务完成契约。

一句话约束：

- `ok` 的判断基于当前 `task_type` 的必需步骤和成功条件
- `partial` 的判断基于未完成 `ok`，但已经存在可接手资产

#### 为什么不能只看 step-kind

同一个 step-kind 在不同任务里的地位并不相同。例如：

- `resolve_apk_dex`
  - 在 `resolve_class_dex` 中是核心步骤
  - 在 `summarize_method_logic` 的 APK 输入路径中只是前置步骤
- `export_and_scan`
  - 在 `summarize_method_logic` 中通常是必需步骤
  - 在 `trace_callers` 中更可能是可选增强，而不是任务完成的硬前提

因此：

- “跑过哪些步骤”不足以判断任务是否完成
- 必须同时结合 `task_type` 和该任务的成功条件

#### 建议的任务完成契约

每个 `task_type` 建议至少定义下面三类信息：

- `required_step_kinds`
  - 该任务完成所需的步骤类型
- `success_condition`
  - 该任务的结果层成功判据
- `empty_is_success`
  - 空结果是否仍可视为任务完成

#### 推荐判定顺序

建议 run 级聚合按下面顺序判断：

1. 当前 `task_type` 的必需步骤是否全部满足
2. 当前 `task_type` 的成功条件是否成立
3. 若不成立，是否已有可接手资产
4. 再决定是 `partial`、`input_error` 还是 `execution_error`

#### 建议任务口径

##### `resolve_class_dex`

- `required_step_kinds`
  - `resolve_apk_dex`
- `success_condition`
  - 存在唯一 `resolved_dex_path`
- `empty_is_success`
  - `false`

建议解释：

- 只有候选 dex 列表但未唯一收敛时，更适合判为 `partial`
- 没有任何候选 dex 时，不应判为 `ok`

##### `search_methods_by_string`

- `required_step_kinds`
  - `run_find`
- `success_condition`
  - `run_find` 已成功返回结构化查询结果
- `empty_is_success`
  - `true`

建议解释：

- 这类任务的目标是“完成查询”
- 查询执行成功但没有命中，通常仍应算任务完成

##### `search_methods_by_number`

- `required_step_kinds`
  - `run_find`
- `success_condition`
  - `run_find` 已成功返回结构化查询结果
- `empty_is_success`
  - `true`

##### `trace_callers`

- `required_step_kinds`
  - `run_find`
- `success_condition`
  - 已得到可用调用者结果
- `empty_is_success`
  - `false`

建议解释：

- 若只得到模糊候选、未完成收敛，通常更适合判为 `partial`

##### `trace_callees`

- `required_step_kinds`
  - `run_find`
- `success_condition`
  - 已得到可用被调用方法结果
- `empty_is_success`
  - `false`

##### `summarize_method_logic`

- APK 输入路径：
  - `required_step_kinds`
    - `resolve_apk_dex`
    - `export_and_scan`
- dex 输入路径：
  - `required_step_kinds`
    - `export_and_scan`
- `success_condition`
  - 已生成方法级 summary 或等价结构化摘要
- `empty_is_success`
  - `false`

建议解释：

- 仅完成 dex 定位，不应判为 `ok`
- 仅完成导出但未生成最终摘要，更适合判为 `partial`

#### 关键边界

要明确区分：

- step 成功
- 任务完成

例如：

- `resolve_apk_dex` 成功
- `export_and_scan` 导出成功
- 但摘要生成失败

这时：

- 某些 step 可以是 `ok`
- 但整个 run 仍不应判为 `ok`
- 更合理的是判为 `partial`

#### 总结规则

建议固定以下判断逻辑：

- `ok`
  - 当前 `task_type` 的必需步骤已满足
  - 且满足该 `task_type` 的成功条件
- `partial`
  - 不满足 `ok`
  - 但已有至少一个可接手资产
- `input_error`
  - 因输入问题导致无法进入有效任务推进
- `execution_error`
  - 未满足 `ok`
  - 且没有任何可接手资产
- `cancelled`
  - 当前 run 被明确中止

## 职责分层

### 底层 CLI / 第三方库

- 负责产生真实能力结果
- 不承担 analyst 对外输出契约的稳定性责任

### Helper Script

- 负责捕获、净化、归一化和重新输出
- 是正式输出契约的第一责任层

### Runner / Workflow

- 负责串联多个 helper
- 只消费 helper 的正式输出
- 不应继续依赖“从脏 stdout 中猜 JSON”的兼容逻辑作为长期方案

## 用户可见产物提升规则

### 总原则

为避免每个 helper 各自决定产物落点，建议固定下面这条分流原则：

- 内部状态和过程证据默认留在 `.dexclub-cli/`
- 只有值得人直接复核、后续还会直接引用的结果，才提升到 `artifacts/` 或 `reports/`

一句话约束：

- 默认内聚到内部目录
- 只提升关键结果，不做全量镜像

### 三档分流策略

#### 1. 必留内部

所有 step 默认至少保留在：

- `.dexclub-cli/.../step-result.json`
- `.dexclub-cli/.../raw.stdout.log`
- `.dexclub-cli/.../raw.stderr.log`

这些内容属于：

- 过程证据
- 机器复用输入
- 排障基础材料

不应跳过内部保留而只写工作区可见目录。

#### 2. 条件提升

只有满足“对人有长期复核价值”的结果，才建议提升到工作区可见目录。

典型包括：

- 导出代码实体
- 结构化分析摘要
- 关键查询结果汇总
- 用户后续会直接点开或引用的结果

#### 3. 不自动提升

下面这些内容不建议自动写到 `artifacts/` / `reports/`：

- 重复可生成的候选列表缓存
- 纯调试日志
- 无结果查询
- 空导出
- 只服务内部兼容的中间格式文件

### 与目录方案的关系

本节不再重复定义 `.dexclub-cli/`、`artifacts/`、`reports/`、`patched/` 的目录职责。

目录边界以前文“工作区目录定稿”为准；本节只补充“哪些结果应从内部状态提升为用户可见产物”的规则。

### 按 helper 的建议落点

#### `run_find`

默认保留在 `.dexclub-cli/`：

- 原始 stdout / stderr
- step-result.json
- 归一化后的完整查询结果

建议提升到 `reports/` 的情况：

- 该查询结果是本次 run 的关键结论
- 结果非空
- 后续会被用户或新会话直接引用

一般不建议自动写入 `artifacts/`，因为查询结果不是代码实体导出物。

#### `resolve_apk_dex`

默认保留在 `.dexclub-cli/`：

- candidate dex 列表
- resolved dex 路径
- 原始过程日志

一般不建议自动写入 `artifacts/`，因为类定位结果本身不是新的导出产物。

在下面这种情况下，可考虑提升到 `reports/`：

- 类定位本身就是本次 run 的关键中间结论
- 后续会由新会话或用户直接复用该定位结论

#### `export_and_scan`

默认保留在 `.dexclub-cli/`：

- 原始 stdout / stderr
- step-result.json
- 内部完整扫描结果

建议提升到 `artifacts/`：

- 导出的 `.smali`
- 导出的 `.java`
- 导出的单类 dex

建议提升到 `reports/`：

- 方法逻辑摘要
- structured summary
- 调用 / 常量 / 字段汇总
- 面向接手的扫描摘要

一句话约束：

- 代码实体进 `artifacts/`
- 分析摘要进 `reports/`

### 提升决策应由谁负责

当前更推荐：

- helper 先完整写入 `.dexclub-cli/`
- workflow / run 层再决定哪些结果要提升为 `key_artifacts`

不建议让每个 helper 自由决定是否大量写入 `artifacts/` / `reports/`，否则很容易重新回到工作区污染问题。

### 最终建议

建议固定为下面这条执行口径：

- 内部默认全留 `.dexclub-cli/`
- 只有“值得人直接看、直接复核、下次还会直接引用”的结果，才提升到 `artifacts/` 或 `reports/`

## Schema 与轻量代码约束策略

### 当前建议

当前阶段更推荐采用三层策略：

1. 文档约定
2. 代码内轻量约束
3. 字段稳定后再考虑正式 schema 文件

一句话约束：

- 现在先不急着引入完整 JSON Schema 文件
- 但不能只靠文档而完全没有代码侧约束

### 为什么当前不优先上正式 JSON Schema

当前仍处在边界收敛阶段，下面这些结构还在讨论和定型中：

- `latest.json`
- `run-summary.json`
- `step_index`
- `key_artifacts`
- `ProcessExecutionResult`

如果此时直接引入完整 JSON Schema：

- 变更成本会过高
- 每次字段调整都要同步修改正式 schema
- 很容易让 schema 先成为负担，而不是约束资产

因此当前更重要的是：

- 先把语义收敛
- 再把关键约束落实到代码里

### 为什么不能完全只靠文档

如果只有文档，没有最低限度的代码校验，后续实现很容易出现：

- 字段名漂移
- 状态枚举不一致
- 必填字段缺失
- 某些 helper 回退到临时字段或不同命名风格

所以当前更合理的方案是：

- 文档定义语义
- 代码做最小必要校验

### 推荐的轻量约束形式

当前阶段更推荐：

- `TypedDict`
- `dataclass`
- 少量手写 validator 函数

而不推荐一开始就引入完整独立 schema 文件和全量 schema 验证链。

### 第一批建议校验的对象

优先级建议如下：

1. `latest.json`
2. `run-summary.json`
3. `step_index` item
4. `key_artifact` item

推荐原因：

- 这些对象最容易被新会话直接消费
- 它们的稳定性最直接影响接手体验
- 它们的字段数量相对可控，适合先做轻量约束

### validator 的职责边界

当前阶段的 validator 不应试图做复杂业务判断，而应主要检查：

- 顶层是否为预期对象类型
- 必填字段是否存在
- 字段类型是否合理
- 枚举值是否在允许范围内
- 路径字段是否至少是非空字符串
- 输出字段命名是否符合既定约定

一句话约束：

- validator 负责防止结构漂移
- 不负责替代业务逻辑

### 不建议在 validator 中承担的职责

下面这些事情不建议放进当前阶段的 validator：

- 深层业务语义判断
- 文件是否真实存在的全量校验
- 跨对象一致性的复杂推导
- 自动修复缺失字段
- 任务规划层的正确性判断

这些属于更高层逻辑，应交给调用方、runner 或专门的校验流程处理。

### 推荐的 fail-fast 与宽容边界

当前阶段建议把校验分成两类：

#### 必须 fail-fast 的问题

- 顶层结构类型错误
- 关键必填字段缺失
- `status`、`selection_reason`、`step_kind` 等枚举值非法
- 明显违反核心 contract 的字段命名或形状

这类问题继续向下传递，只会让接手和调试成本更高，应尽早失败。

#### 可以暂时宽容的问题

- 某些可选字段缺失
- 新增的非关键扩展字段
- 某些路径当前还未物理存在，但字符串形状正确

这类问题当前更适合保留扩展空间，而不是把 schema 约束得过死。

### 推荐的校验入口

后续进入实现时，建议把校验放在对象写出前，而不是只在读取时补救。

优先入口建议：

- 写 `latest.json` 前
- 写 `run-summary.json` 前
- 生成 `step_index` item 时
- 生成 `key_artifact` item 时

这样做的好处是：

- 错误尽可能在生产方暴露
- 避免坏数据进入工作区后再传播

### `schema_version` 的作用

即使当前不立刻引入正式 schema 文件，也建议继续保留：

- `schema_version`

原因是：

- 它能明确当前输出契约版本
- 后续真正引入正式 schema 文件时，不需要再额外迁移这层概念
- 新会话读取对象时，也能先按版本做分支兼容

### 何时再升级到正式 schema 文件

建议至少满足下面两个条件后，再考虑增加正式 JSON Schema：

1. 经过一轮真实实现，字段和边界已相对稳定
2. 输出对象已经存在跨脚本、跨会话、跨验证脚本的稳定消费场景

在此之前，推荐保持：

- 文档约定
- 轻量 validator
- `schema_version`

### validator 模块分工建议

当前更推荐的做法是：

- 不把 validator 塞进 `process_exec.py`
- 也不继续堆进 `runner.py`
- 单独放一层“输出契约模块”

建议分层如下：

1. `process_exec.py`
2. `output_contract.py`
3. `runner.py` 与各 helper

#### `process_exec.py`

职责建议保持很窄，只负责：

- 子进程执行
- 原始 stdout / stderr 捕获
- raw log 落盘
- payload 提取
- 返回 `ProcessExecutionResult`

它只应做这层的最小内部校验，例如：

- `payload_kind` 是否合法
- 执行结果对象顶层字段是否完整

不建议让它理解：

- `latest.json`
- `run-summary.json`
- `step_index`
- `key_artifacts`

一句话约束：

- `process_exec.py` 只懂“进程结果”
- 不懂“run 级输出契约”

#### `output_contract.py`

建议新增一层独立模块，例如：

- `analyst/scripts/output_contract.py`

用于承载：

- 轻量对象定义
- validator 函数
- run/output 级结构约束

第一批建议纳入的对象包括：

- `LatestIndex`
- `RunSummary`
- `StepIndexItem`
- `KeyArtifactItem`

第一批建议提供的校验函数包括：

- `validate_latest_index(...)`
- `validate_run_summary(...)`
- `validate_step_index_item(...)`
- `validate_key_artifact_item(...)`

必要时，后续再补：

- `validate_step_result_envelope(...)`

一句话约束：

- `output_contract.py` 管输出契约
- 不参与子进程执行

#### `runner.py`

建议把 `runner.py` 定位为：

- 契约模块的调用方
- 而不是契约模块的宿主

它负责：

- 聚合 step 结果
- 构造 `step_index`
- 构造 `run-summary.json`
- 构造 `latest.json`
- 在写出前调用 `output_contract.py` 做轻量校验

#### 各 helper

各 helper 负责：

- 生成自己的业务结果
- 写 `step-result.json` 前做轻量 envelope 校验

但不负责：

- 维护 `latest.json`
- 维护 `run-summary.json`

#### 为什么不继续放在 `runner.py`

如果把所有 validator 继续堆进 `runner.py`，会出现两个问题：

- 独立运行 helper 时无法自然复用
- `runner.py` 会进一步膨胀成“大总管文件”

因此更推荐：

- `runner.py` 生产对象
- `output_contract.py` 约束对象

#### 校验时机建议

建议固定为：

- 生产方写出前校验
- 消费方读取时做防御性校验

推荐入口：

- helper 写 `step-result.json` 前
- runner 写 `run-summary.json` 前
- runner 写 `latest.json` 前

这样可以把坏数据尽量拦在写入工作区之前，而不是等读取时再被动补救。

#### 与 `plan_schema.py` 的边界

当前不建议继续把这些输出契约塞进 `plan_schema.py`。

原因是：

- `plan_schema.py` 当前主要承载 planner / task 定义
- 输出契约属于另一个语义层级

更推荐的分工是：

- `plan_schema.py`
  - 任务与计划约束
- `output_contract.py`
  - 输出与索引约束

## 公共执行器模块草案

### 模块定位

建议新增一个很小的公共模块，例如：

- `skills/dexclub-cli-launcher/analyst/scripts/process_exec.py`

这层模块只负责“执行与净化”，不负责任务规划、业务归一化或最终文本渲染。

一句话边界：

- `process_exec.py` 负责把“不可信子进程输出”变成“可消费的执行结果对象”
- 业务脚本负责把“执行结果对象”变成“任务结果”

### 第一版职责

第一版只做下面四件事：

1. 执行子进程并捕获 `stdout` / `stderr`
2. 将原始流落盘到 artifact
3. 提取 payload
4. 返回统一的内部结果对象

### 第一版非职责

第一版明确不做下面这些事：

- 不参与业务命令拼装
- 不理解 `run_find`、`resolve_apk_dex`、`export_and_scan` 的业务语义
- 不负责 recommendations / evidence / limits 这类 runner 级逻辑
- 不直接决定最终 `stdout` 渲染内容

### 建议主接口

建议第一版只暴露一个主函数：

```python
def run_captured_process(
    *,
    step_id: str,
    command: list[str],
    artifact_dir: Path,
    payload_kind: Literal["json", "text", "none"],
    extractor: Callable[[str], object] | None = None,
    encoding: str = "utf-8",
) -> ProcessExecutionResult:
    ...
```

### 参数语义

- `step_id`
  - 当前执行步骤的稳定标识
  - 同时用于生成原始流 artifact 文件名
- `command`
  - 已经拼装完成的最终命令
  - 公共执行器不再参与命令构造
- `artifact_dir`
  - 当前步骤的结果目录
- `payload_kind`
  - 表示调用方预期的 stdout 载荷类型
- `extractor`
  - 可选自定义提取器
  - 当 `payload_kind == "json"` 且未显式传入时，默认走公共 JSON 提取器
- `encoding`
  - 当前默认 `utf-8`
  - 保留为显式参数，便于后续兼容特殊输出源

### 建议辅助函数

建议第一版只补最小辅助函数：

```python
def extract_json_payload(stdout: str) -> object:
    ...

def write_raw_stream(path: Path, content: str, *, encoding: str = "utf-8") -> int:
    ...

def default_raw_paths(*, artifact_dir: Path, step_id: str) -> tuple[Path, Path]:
    ...
```

### 返回对象建议

建议公共执行器返回内部对象 `ProcessExecutionResult`。

第一版可以接受 `TypedDict` 或等价轻量结构，但不建议继续让裸 `dict[str, object]` 无约束增长。

建议字段如下：

| 字段 | 必填 | 类型 | 说明 |
| --- | --- | --- | --- |
| `step_id` | 是 | `str` | 步骤标识 |
| `status` | 是 | `str` | `ok | execution_error | normalization_error` 为第一版核心状态 |
| `exit_code` | 是 | `int` | 子进程退出码 |
| `command` | 是 | `list[str]` | 实际执行命令 |
| `started_at` | 是 | `str` | 开始时间 |
| `finished_at` | 是 | `str` | 结束时间 |
| `duration_ms` | 是 | `int` | 执行耗时 |
| `payload_kind` | 是 | `str` | 预期载荷类型 |
| `payload` | 否 | `object` | 从 stdout 提取出的原始载荷 |
| `diagnostics` | 是 | `object` | 基础诊断信息 |
| `raw_process` | 是 | `object` | 原始流 artifact 信息 |
| `artifacts` | 是 | `list[object]` | 至少包含 `raw_stdout` / `raw_stderr` |

第一版建议暂不在公共执行器层直接填充业务 `result`，避免执行层过早沾染业务语义。

### 状态判定边界

第一版公共执行器只负责基础状态判定：

- 子进程非零退出
  - `execution_error`
- 子进程零退出，但 payload 提取失败
  - `normalization_error`
- 子进程零退出，且 payload 提取成功
  - `ok`

`empty` 是否成立，交由业务层判断，不在第一版公共执行器中推断。

### 为什么第一版不判断 `empty`

“空结果”是业务语义，而不是进程语义。例如：

- `run_find.py` 返回 `[]`
- `resolve_apk_dex.py` 返回多个候选 dex
- `export_and_scan.py` 返回对象，但某些摘要字段为空

这些情况是否属于 `empty`，必须由调用方按业务语义决定，不能提前固化在执行器中。

### 返回语义

建议 `run_captured_process(...)` 永远返回统一对象，而不是把子进程失败、提取失败都表达成异常。

只有下面这类真正的调用错误，才考虑直接抛异常：

- `artifact_dir` 非法
- `payload_kind` 非法
- 公共执行器自身被错误使用

### 标准伪流程

```python
completed = subprocess.run(..., capture_output=True, text=True)

write stdout/stderr raw logs

if completed.returncode != 0:
    return execution_error_result(...)

if payload_kind == "none":
    return ok_result(payload=None)

payload = extractor(stdout)  # 或按 payload_kind 走默认提取器

if extractor failed:
    return normalization_error_result(...)

return ok_result(payload=payload)
```

### 与现有脚本的关系

建议公共执行器作为基础层，被以下脚本复用：

- `resolve_apk_dex.py`
- `export_and_scan.py`
- `runner.py`

其中：

- `resolve_apk_dex.py`
  - 是第一批最适合接入的脚本，边界最小、最独立
- `runner.py`
  - 应作为公共执行器的调用方，而不是公共执行器的宿主
- `export_and_scan.py`
  - 可在第二批接入，因为它本身包含“执行导出 + 本地分析”两阶段流程

### 第一版成功标准

第一版不追求一次性彻底统一，只要求达到下面这些目标：

1. `extract_json_payload` 不再在多个脚本里重复复制
2. 至少一个高频 helper 和 `runner.py` 开始复用同一套执行捕获逻辑
3. `raw_stdout` / `raw_stderr` 有稳定 artifact 落点
4. 后续增强输出净化时，不需要再触碰业务层代码

### 当前建议

当前更推荐的路线是：

1. 先补公共模块文档与接口约定
2. 后续如果进入实现，优先从 `resolve_apk_dex.py` 接入
3. 等公共层稳定后，再让 `runner.py` 和其他 helper 统一迁移

## 推荐落地顺序

1. 先整理工作区目录与产物落点，去掉 `build/`，统一收拢到 `.dexclub-cli/`
2. 再补输出净化，确保 `json` 模式下 `stdout` 只输出正式结果
3. 然后落地 `latest.json`、`run-summary.json`、`step_index`、`key_artifacts` 这组接续对象
4. 再补轻量 validator，先校 `latest.json`、`run-summary.json`、`step_index item`、`key_artifact item`
5. 最后抽公共执行器，并逐步让 `resolve_apk_dex.py`、`runner.py`、`export_and_scan.py` 迁移复用

## 当前决策

当前路线明确选择：

- 不依赖修改上游 DexKit / JADX 作为输出稳定性的前提
- 优先在 analyst 层建立输出净化与结果契约

如果后续上游日志行为发生改善，这套契约仍然成立；如果未来新增新的底层噪音源，这套契约也仍然有效。
