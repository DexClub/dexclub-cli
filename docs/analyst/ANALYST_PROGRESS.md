# Analyst 进度文档

## 目的

这份文档用于记录 `skills/dexclub-cli-launcher/analyst` 这一条线的实际落地进度。

它和 [README.md](./README.md)、[ANALYST_ROADMAP.md](./ANALYST_ROADMAP.md)、[ANALYST_PLANNER_PLAN.md](./archive/ANALYST_PLANNER_PLAN.md)、[ANALYST_STORAGE_PLAN.md](./archive/ANALYST_STORAGE_PLAN.md) 的分工不同：

- `docs/analyst/README.md`
  - 记录 analyst 文档索引与推荐阅读顺序
- `ANALYST_ROADMAP.md`
  - 记录 analyst 的版本路线与后续扩展方向
- `ANALYST_PLANNER_PLAN.md`
  - 归档的 `V1` 设计目标、边界和版本 1 方案
- `ANALYST_STORAGE_PLAN.md`
  - 归档的 `V1` 工作目录与输入缓存落地边界
- `ANALYST_PROGRESS.md`
  - 记录当前真实实现状态、阶段节奏、下一步入口和流转规则

下次会话如果没有上下文，先读这份文档，再决定是否需要回看设计文档或具体代码。

与“当前真实状态”相对，后续目标方案、输出契约、工作区目录收敛和跨会话接续规则，以 [OUTPUT_NORMALIZATION.md](./OUTPUT_NORMALIZATION.md) 为准。本文件允许继续记录当前实现仍停留在旧路径或旧结构上的现实，不把“目标方案”误写成“已实现状态”。

## 当前快照

- 当前最新相关提交
  - `c102896` `Add analyst local artifact storage`
  - `57804d9` `Finalize analyst Java summarize release validation`
  - `8f41ae6` `Support analyst exact Java summarize`
  - `7cc459f` `Fix export-java in packaged CLI`
  - `dcc2030` `Add structured analyst method summaries`
  - `3af1500` `Add analyst large-method summary compression`
  - `7007464` `Support descriptor-aware analyst anchors`
  - `a51c692` `Add APK summarize resolution to analyst`
- 当前已稳定的 analyst v1 能力
  - `plan` / `run` 入口已经落地
  - `search_methods_by_string`
  - `search_methods_by_number`
  - `trace_callers`
  - `trace_callees`
  - `summarize_method_logic`
  - APK 输入下自动解析目标 dex 后再 summarize
  - overloaded 方法在 relaxed anchor 下返回 `ambiguous`
  - descriptor-aware 的 `trace_*`
  - descriptor-aware 的 `summarize_method_logic`
    - `smali` 已稳定
    - `java` 已在默认 published release 上完成端到端验证
  - `smali` summarize 的结构化摘要
    - 输出：`structured_summary`
    - 当前包含：`basic_blocks / call_clusters / constant_clusters`
    - 当前包含：`focus_snippets`
  - 大方法 `smali` summarize 的结构化压缩
    - 固定阈值：`line_count >= 120`
    - 压缩输出：`large_method_analysis`
    - 当前按 `method_calls / strings / numbers / field_accesses / branch_hotspots` 分组，并补充行簇聚合
  - 当前本地已落地的 analyst 存储结构
    - 下面这些是“当前已实现状态”，不是新的目标目录方案
    - `analyze.py run` 默认写入 `.dexclub-cli/runs/v1/<run-id>/`
    - APK / dex 输入缓存写入 `.dexclub-cli/cache/v1/inputs/`
    - run 目录当前会写入 `run-meta.json`、`final_result.json`、`run-summary.json`
    - 单步结果当前聚合到 `.dexclub-cli/runs/v1/<run-id>/steps/<step-id>/`
    - step 目录当前会写入 `step-result.json`、`raw.stdout.log`、`raw.stderr.log`
    - runs 根当前会写入 `.dexclub-cli/runs/v1/latest.json`
    - `run-summary.json` 当前已包含最小 `step_index`、`key_artifacts`
    - 输入缓存目录会写入 `input-meta.json`
- 当前验证脚本
  - [validate_v1_sample.sh](../../skills/dexclub-cli-launcher/analyst/scripts/validate_v1_sample.sh)
  - 运行时需要通过首个参数或 `DEXCLUB_ANALYST_SAMPLE_APK` 显式提供样例 APK 路径
- 最近一次通过验证的命令
  - `DEXCLUB_CLI_CACHE_DIR=<cache-dir> bash ./skills/dexclub-cli-launcher/launcher/scripts/run_latest_release.sh --reset-remote-failures --update-cache --prepare-only`
  - `DEXCLUB_CLI_CACHE_DIR=<cache-dir> python3 ./skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py --input-dex <dex-path> --class androidx.compose.foundation.ImageKt --method Image --method-descriptor 'Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V' --language java --mode summary --format json`
  - `DEXCLUB_CLI_CACHE_DIR=<cache-dir> python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run --task-type summarize_method_logic --input-json '{"input":["<apk-path>"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image","descriptor":"Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V"},"language":"java"}'`
- 最近一次通过验证的结果目录
  - `<results-dir>`

## 当前边界

- `summarize_method_logic` 的 descriptor-aware 精确切片当前支持：
  - `smali`
  - `java`
- 仓库当前代码里的 `export-java` 修复已经落地
  - 默认 published release `v0.0.1` 已确认包含 `A-09` 修复
  - 基于新的空缓存可成功下载 release 产物并完成 Java exact summarize
- `structured_summary` 当前只支持 `smali`
  - `java` summarize 不报错，但会返回 `kind=none` 和 `supported=false`
- `focus_snippets` 当前是受控预览
  - 只抽高信号 block / cluster
  - 会做截断，不保证覆盖每个 block
- `large_method_analysis` 当前只对 `smali` 大方法启用；`java` 和非大方法会保留 `is_large_method=false`
- 当前仍然是 analyst 层编排，不是主仓库 `cli / core / dexkit` 的正式产品化能力扩展
- 当前工作区存在无关状态，继续开发时不要混入
  - `dexkit/vendor/DexKit` 子模块改动

## 状态定义

只允许使用以下状态：

- `待开始`
  - 已确认需要做，但还没开工
- `进行中`
  - 已开始实现或验证，尚未收口
- `阻塞`
  - 已开工，但被外部条件、设计冲突或未决问题卡住
- `已完成`
  - 代码、验证、必要文档都已经收口
- `已取消`
  - 明确决定不做，或改成由新任务替代

## 状态流转约束

状态不能随意跳。后续维护这份文档时，必须遵守下面的约束。

### 允许的流转

- `待开始 -> 进行中`
- `进行中 -> 阻塞`
- `进行中 -> 已完成`
- `进行中 -> 已取消`
- `阻塞 -> 进行中`
- `阻塞 -> 已取消`

### 不允许的流转

- 不允许 `待开始 -> 已完成`
  - 除非该项被判定为纯记录性条目，并在备注中明确写出“未开发，直接关闭”的原因
- 不允许 `已完成 -> 进行中`
  - 如果完成后出现新需求或回归，必须新建一个任务，不要回退旧任务状态
- 不允许 `已取消 -> 进行中`
  - 如果方向恢复，必须新建一个任务
- 不允许同时把多个互相依赖的主任务都标成 `进行中`
  - analyst 这条线默认一次只保留一个主推进项为 `进行中`

### 从 `进行中` 流转到 `已完成` 的必要条件

一个任务只有在以下条件都满足时，才能标成 `已完成`：

- 目标代码已经落地
- 直接影响路径已经做了最小可用验证
- 如果输出 contract 或使用方式发生变化，相关 README 或样例已经同步
- 如果做了提交，文档里要记录对应提交号

### 从 `进行中` 流转到 `阻塞` 的必要条件

- 必须写清楚阻塞原因
- 必须写清楚恢复条件
- 必须写清楚当前停在什么文件或什么判断点

## 任务清单

| ID | 任务 | 状态 | 备注 |
| --- | --- | --- | --- |
| A-01 | analyst v1 规划器 / 执行器基础落地 | 已完成 | 提交 `b85be18` |
| A-02 | v1 样例验证与真实输出样例文档 | 已完成 | 提交 `d14413d` |
| A-03 | APK 输入下自动定位目标 dex 后 summarize | 已完成 | 提交 `a51c692` |
| A-04 | descriptor-aware 的 trace/summarize 最小闭环 | 已完成 | 提交 `7007464` |
| A-05 | 大方法 smali 的二级拆解与上下文压缩 | 已完成 | 本次会话完成。新增 `large_method_analysis`，阈值 `120`，并补了样例验证 |
| A-06 | descriptor-aware + `java` summarize | 已完成 | `A-09` 已在仓库内修复，规划器对 `language=java` 的硬拒绝已移除，Java exported-code 已支持 descriptor-aware 精确切片；已补 fixed-launcher-build 与默认 published release 两级 `export_and_scan.py` / `analyze.py run` 端到端验证，并同步脚本与文档 |
| A-07 | 更强的 summary 结构化输出 | 已完成 | 本次会话完成。新增 `structured_summary`，包含 `basic_blocks / call_clusters / constant_clusters` |
| A-08 | 基于结构化摘要的局部片段提取 | 已完成 | 本次会话完成。新增 `focus_snippets`，按高信号 block / cluster 回抽原始 smali 片段 |
| A-09 | `export-java` 导出失败定位与修复 | 已完成 | 本次会话完成。根因包括 fat jar 中缺失 Jadx `dex-input` service 声明，以及 Java 导出时在 decompiler 生命周期外读取 `JavaClass.code` |
| A-10 | analyst 工作目录产物与输入缓存落地 | 已完成 | 提交 `c102896`。后续已把当前真实实现默认 run 根目录切到 `.dexclub-cli/runs/v1`，输入缓存切到 `.dexclub-cli/cache/v1/inputs`，并把单步结果收口到 `steps/<step-id>/`；更完整的目标方案另见 [OUTPUT_NORMALIZATION.md](./OUTPUT_NORMALIZATION.md) |
| A-11 | run 接续索引最小落地 | 已完成 | 本次会话完成。当前真实实现已补 `run-summary.json`、`latest.json`、最小 `step_index` 与 `key_artifacts`，并覆盖 `run_plan` 与 `analyze.py run` 的 `input_error` 落盘路径 |
| A-12 | run 接续对象轻量校验 | 已完成 | 本次会话完成。已新增 `output_contract.py`，并在写 `run-summary.json`、`latest.json` 前做最小字段与枚举校验 |
| A-13 | 公共执行捕获层最小抽取 | 已完成 | 本次会话完成。已新增 `process_exec.py`，并让 `runner.py` 与 `resolve_apk_dex.py` 复用同一套执行、raw log 与 JSON payload 提取逻辑 |
| A-14 | `export_and_scan` 接入公共执行捕获层 | 已完成 | 本次会话完成。`export_and_scan.py` 已改为通过 `process_exec.py` 调 launcher 导出，`json` 模式下不再直通底层 `stdout` 噪音 |

## 最近一次状态流转

- `A-05`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 目标代码已落地
    - `validate_v1_sample.sh` 已补充大方法压缩断言
    - `README` 与样例文档已同步 `large_method_analysis`
- `A-07`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 目标代码已落地
    - `validate_v1_sample.sh` 已补充 `structured_summary` 断言
    - `README`、工作流说明与样例文档已同步新契约
- `A-08`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 目标代码已落地
    - `validate_v1_sample.sh` 已补充 `focus_snippets` 断言
    - `README`、工作流说明与样例文档已同步新契约
- `A-06`
  - `待开始 -> 进行中`
  - `进行中 -> 阻塞`
  - 阻塞原因
    - 已确认 analyst 层在 [`planner.py`](../../skills/dexclub-cli-launcher/analyst/scripts/planner.py) 中会提前拒绝 `descriptor-aware + language=java`
    - 但进一步实测发现，当前发布版 `export-java` 对样例 APK 中的 `com.shadcn.ui.compose.MainActivity` 与 `androidx.compose.foundation.ImageKt` 都会直接失败，尚未进入 analyst 的 Java 精确切片阶段
  - 恢复条件
    - 先定位并修复 `export-java` 的失败原因，或至少确认一个稳定可复现且可导出的 Java 样例链路
  - 当前停点
    - analyst 观察停在 [`export_and_scan.py`](../../skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py) 调用 launcher 后的 subprocess 失败
    - 契约判断停在 [`planner.py`](../../skills/dexclub-cli-launcher/analyst/scripts/planner.py) 对 `language=java` 的硬拒绝
- `A-09`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 已定位 `export-java` 失败根因
    - 已修复 [`JadxDecompilerService.kt`](../../core/src/jvmMain/kotlin/io/github/dexclub/core/export/JadxDecompilerService.kt) 的导出实现
    - 已补 [`jadx.api.plugins.JadxPlugin`](../../cli/src/main/resources/META-INF/services/jadx.api.plugins.JadxPlugin) service 声明，保证 fat jar 可加载 `dex-input`
    - 本地 fat jar 已验证 `MainActivity` / `ImageKt` Java 导出成功
- `A-06`
  - `阻塞 -> 进行中`
  - 恢复原因
    - `A-09` 已在仓库内完成，Java 导出链路不再是当前代码的前置失败点
    - [`planner.py`](../../skills/dexclub-cli-launcher/analyst/scripts/planner.py) 已移除 `descriptor-aware + language=java` 的硬拒绝
    - [`code_analysis.py`](../../skills/dexclub-cli-launcher/analyst/scripts/code_analysis.py) 已支持 Java exported-code 的 descriptor-aware 精确切片
  - 当前停点
    - 基于包含 `A-09` 修复的 launcher 构建的 `export_and_scan.py` / `analyze.py run` 端到端验证已补完
    - 当前只剩默认 published release 的对齐判断与最终口径维护
- `A-06`
  - `进行中 -> 已完成`
  - 完成依据
    - 默认 published release `v0.0.1` 已确认可通过 launcher 的空缓存下载路径获取
    - 已基于新的空缓存完成 `export_and_scan.py` / `analyze.py run` 的 Java exact summarize 端到端验证
    - `README`、样例文档与验证脚本口径已收口到 published release
- `A-10`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - [`runner.py`](../../skills/dexclub-cli-launcher/analyst/scripts/runner.py) 已把当前真实实现的默认 run 根目录切到 `.dexclub-cli/runs/v1`
    - [`analyst_storage.py`](../../skills/dexclub-cli-launcher/analyst/scripts/analyst_storage.py) 已补 APK / dex 输入缓存、`input-meta.json` 与原子落盘
    - 已完成同 APK 连续执行复用、删 `runs/` 不影响缓存、删 `cache/` 可重建的最小验证
    - `README`、样例文档与进度文档已同步当时的存储口径
    - 代码与文档已提交到 `c102896`
- `A-11`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - [`runner.py`](../../skills/dexclub-cli-launcher/analyst/scripts/runner.py) 已补 `run-summary.json`、`latest.json`、最小 `step_index` 与 `key_artifacts`
    - [`analyze.py`](../../skills/dexclub-cli-launcher/analyst/scripts/analyze.py) 已把 `run` 的 `input_error` 路径纳入同一套 run 级落盘
    - 已完成 `run_plan` 成功链路与 `analyze.py run` 输入错误链路的最小验证
- `A-12`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 已新增 [`output_contract.py`](../../skills/dexclub-cli-launcher/analyst/scripts/output_contract.py)
    - [`runner.py`](../../skills/dexclub-cli-launcher/analyst/scripts/runner.py) 已在写 `run-summary.json`、`latest.json` 前调用轻量 validator
    - 已完成成功链路与 `input_error` 链路下的 validator 通过验证
- `A-13`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 已新增 [`process_exec.py`](../../skills/dexclub-cli-launcher/analyst/scripts/process_exec.py)
    - [`runner.py`](../../skills/dexclub-cli-launcher/analyst/scripts/runner.py) 已复用公共执行捕获层，不再内联重复的 raw log 与 JSON 提取流程
    - [`resolve_apk_dex.py`](../../skills/dexclub-cli-launcher/analyst/scripts/resolve_apk_dex.py) 已复用同一套执行与 payload 提取逻辑
    - 已完成 `run_plan` 成功链路、`resolve_apk_dex.py` 类定位链路与 `analyze.py run` 输入错误链路的最小验证
- `A-14`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - [`export_and_scan.py`](../../skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py) 已改为通过 [`process_exec.py`](../../skills/dexclub-cli-launcher/analyst/scripts/process_exec.py) 捕获 launcher 导出过程
    - `export_and_scan.py --format json` 下已确认不会把底层导出日志混入正式 `stdout`
    - 已完成导出成功链路下的最小 raw log 落盘与纯净 JSON 输出验证

- 本次会话补充
  - 当前真实实现默认内部状态根已从 `build/dexclub-cli/` 切到工作区 `.dexclub-cli/`
  - 当前真实实现已把单步结果收口到 `steps/<step-id>/step-result.json`，并为每步补 `raw.stdout.log`、`raw.stderr.log`
  - `final_result.json` 已回收到 run 根，不再写入 run 根下的 `results/` 目录
  - 当前真实实现已补 `run-summary.json`、`latest.json`、最小 `step_index` 与 `key_artifacts`
  - 当前真实实现已补 `output_contract.py`，并对 `run-summary.json`、`latest.json` 落地前做最小校验
  - 当前真实实现已补 `process_exec.py`，并让 `runner.py`、`resolve_apk_dex.py`、`export_and_scan.py` 复用同一套执行捕获逻辑

## 下一步推荐入口

当前这条 analyst 主线已收口，暂无新的主推进项。

如果下一个对话继续沿这条线推进，优先做以下两类事情之一：

- 推送 `c102896`，然后观察真实使用里的缓存体积、复用率和回归情况
- 如果要继续扩展或收敛当前工程方案，先对照 [OUTPUT_NORMALIZATION.md](./OUTPUT_NORMALIZATION.md)，再考虑补充清理入口、更细的缓存复用粒度或输出契约相关整理

## 历史归档

`A-05 / A-06 / A-09` 的长历史记录已经从当前进度文档移出。

如果需要回看这些任务的阻塞、恢复和完成过程，直接读：

- [ANALYST_V1_HISTORY.md](./archive/ANALYST_V1_HISTORY.md)

## 下次会话快速开始

下次会话如果没有任何历史上下文，按下面顺序开始：

1. 先读本文件
   - [ANALYST_PROGRESS.md](./ANALYST_PROGRESS.md)
2. 再读路线和设计边界
   - [README.md](./README.md)
   - [OUTPUT_NORMALIZATION.md](./OUTPUT_NORMALIZATION.md)
   - [ANALYST_ROADMAP.md](./ANALYST_ROADMAP.md)
   - [ANALYST_PLANNER_PLAN.md](./archive/ANALYST_PLANNER_PLAN.md)
   - [ANALYST_STORAGE_PLAN.md](./archive/ANALYST_STORAGE_PLAN.md)
3. 确认工作区无关状态，不要误处理
   - `git status --short`
4. 确认最近相关提交
   - `git log --oneline -n 6`
5. 如果后续没有新任务，优先只做回归观察
   - 重点看：
     - [ANALYST_PROGRESS.md](./ANALYST_PROGRESS.md)
     - [code_analysis.py](../../skills/dexclub-cli-launcher/analyst/scripts/code_analysis.py)
     - [export_and_scan.py](../../skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py)
     - [planner.py](../../skills/dexclub-cli-launcher/analyst/scripts/planner.py)
     - [runner.py](../../skills/dexclub-cli-launcher/analyst/scripts/runner.py)
     - [JadxDecompilerService.kt](../../core/src/jvmMain/kotlin/io/github/dexclub/core/export/JadxDecompilerService.kt)
     - [validate_v1_sample.sh](../../skills/dexclub-cli-launcher/analyst/scripts/validate_v1_sample.sh)
     - [README.md](../../skills/dexclub-cli-launcher/analyst/README.md)
     - [analyze-v1-examples.md](../../skills/dexclub-cli-launcher/analyst/references/analyze-v1-examples.md)
   - 当前主线已完成，不需要再回到 `A-06`

## 下次会话可直接使用的提示词

如果下次会话要最快恢复，可以直接用这句：

```text
先阅读 docs/analyst/ANALYST_PROGRESS.md 确认当前真实状态，再阅读 docs/analyst/OUTPUT_NORMALIZATION.md 区分后续目标方案，然后根据新的回归问题或新需求决定是否继续扩展 launcher / analyst 能力。
```

## 文档维护规则

后续每次继续这条线时，优先更新这份文档，而不是把状态散落在多个地方。

至少同步以下内容：

- 当前主推进任务是哪一个
- 哪个任务从什么状态流转到了什么状态
- 新增提交号
- 新增验证命令或验证结果
- 如果下一步优先级变化，要同步更新“下一步推荐入口”
