# dexclub-cli skill 改进清单

## 范围说明

本文基于独立工作区 `/data/data/com.termux/files/home/demo` 中的实际会话记录审计结果整理，聚焦 `dexclub-cli-launcher` skill 在设计、接口、工作流和可用性上的问题。

本清单明确不处理环境侧问题，例如 `bwrap` / sandbox 差异、宿主权限模型或外部运行时限制。以下条目均属于非环境问题，且应纳入后续修复范围。

本文定位是“问题清单与验收要求”。如果需要看当前整理出的目录方案、输出契约、跨会话接续、`partial` 判定或轻量 validator 边界，统一以 [`docs/analyst/OUTPUT_NORMALIZATION.md`](./docs/analyst/OUTPUT_NORMALIZATION.md) 为详细设计参考，以 [`docs/analyst/README.md`](./docs/analyst/README.md) 为入口索引。

## 修复目标

1. 降低代理在独立工作区使用 skill 时的误用率。
2. 先统一导出与分析产物路径，再处理其他能力问题，避免修复过程中继续写乱目录。
3. 收敛 skill 的可调用面，避免 launcher / analyst / helper script 三层接口混用。
4. 降低多 dex APK 分析的重复成本，避免同一轮任务反复全量扫描。
5. 让“已验证事实”和“推断结论”的边界在工作流层面天然清晰，而不是依赖代理临场自律。
6. 让常见 APK 逆向任务可以通过稳定、低试错成本的标准路径完成。

## 当前状态快照

以下状态基于当前仓库实现整理，用于回答“这份清单里的问题现在解决到什么程度”。本节不改动原始问题定义，也不代表“部分解决”的条目已经达到本文最初设定的完整验收标准。

| 条目 | 状态 | 当前判断 |
| --- | --- | --- |
| 1. 统一缓存与产物路径策略 | 已解决 | analyst 默认根已收口到工作区 `.dexclub-cli/`，并支持 `DEXCLUB_ANALYST_WORK_ROOT` / `DEXCLUB_ANALYST_CACHE_DIR` |
| 2. 收敛 skill 对外入口 | 已解决 | `analyze.py` 已明确为稳定 analyst 入口，`run_find.py` / `resolve_apk_dex.py` / `export_and_scan.py` 被标为内部 helper |
| 3. 统一参数契约 | 部分解决 | 任务级入口显著降低了误用率，但 helper 与真实 CLI 之间仍未完全统一，也未全面补兼容层与迁移提示 |
| 4. 修复多 dex APK 高成本类定位流程 | 已解决 | APK 提取 dex、class->dex 索引和缓存命中信息均已落地 |
| 5. 提供稳定任务级入口 | 已解决 | `analyze.py run` 已承接方法逻辑、调用链、类定位等高频任务 |
| 6. 增强错误消息 | 部分解决 | 已有 `PlannerError` 与 step `diagnostics` 骨架，但“原因 + 建议动作”的覆盖面仍不完整 |
| 7. 明确“已验证事实 / 推断”结构边界 | 已解决 | `analyze.py run`、planner 错误输出与 `runs inspect --include-final-result` 已固定 `verifiedFacts / inferences / unknowns / nextChecks` 结构，并在可用时附带证据定位 |
| 8. 增加结果复用与中间态管理 | 已解决 | APK 提取 dex、class->dex、export-and-scan、跨 run step reuse 均已支持复用并暴露来源 |
| 9. 保证 JSON 输出纯净 | 部分解决 | 主线脚本已明显净化，但当前实现仍保留“从 stdout 中定位 JSON 起点”的兜底解析 |
| 10. 把工作区 dex 集合提升为一等输入源 | 部分解决 | 已支持 dex 路径数组与 `dex_set`，但尚未补 `--input-dex-dir` / `--input-dex-list` 这类显式一等入口 |

如果后续继续推进，建议优先按下面顺序收尾：

1. 先收紧条目 9，真正去掉 JSON 模式下对 stdout 起始位置猜测的兜底逻辑。
2. 再补条目 3、6、10 中尚未完成的契约统一、纠错提示和 dex 集合显式输入。

## 前置原则

1. `dexclub-cli` 的 release 缓存允许继续保留在全局缓存体系内，这不是本轮要解决的问题。
2. 本轮要解决的是 analyst 层导出、解析、扫描、汇总等分析产物的默认落点混乱问题。
3. 在导出与分析产物路径统一之前，不应继续推进其他会新增或复制产物的功能修复，避免边修边继续污染目录。
4. 后续所有新增工作流或 helper，必须先继承统一后的路径规则，再谈接口或能力扩展。

## 必须修

### 1. 统一缓存与产物路径策略，消除全局缓存、工作区产物与临时目录混放

#### 问题

当前 skill 在“缓存”和“产物”上至少同时存在三套路径策略：

1. launcher release 缓存：
   - 默认走 `DEXCLUB_CLI_CACHE_DIR`
   - 未指定时走 `${XDG_CACHE_HOME:-$HOME/.cache}/dexclub-cli/releases`
2. analyst 输入缓存与 run 产物：
   - 走 `repo_root()/build/dexclub-cli/...`
   - 但这里的 `repo_root()` 不是用户当前工作区，而是 skill 自身所在仓库根目录
   - 当 skill 从 `/root/.codex/skills/dexclub-cli-launcher` 运行时，实际会落到 `/root/.codex/build/dexclub-cli/...`
3. 部分 helper 的默认导出目录：
   - 例如 `export_and_scan.py` 未传 `--output-dir` 时直接走 `tempfile.mkdtemp(...)`
   - 这会把导出文件落到 `/tmp/dexclub-analyst-*`

这会导致几个实际问题：

- 文档写的是 `build/dexclub-cli/...`，但用户看到的却可能是 `/root/.codex/build/...`
- 同一轮任务中，输入缓存、提取 dex、导出 smali/java、扫描摘要可能分散在不同根目录
- 工作区 `artifacts/` 与 skill 默认缓存目录没有稳定映射关系
- 用户很难判断哪些是“可复用缓存”，哪些只是“一次性临时产物”

这不是 `AGENTS.md` 导致的。`demo/AGENTS.md` 只建议分析产物放在 `artifacts/`、`reports/`、`patched/`，并没有定义 skill 的缓存根目录，也没有改写 skill 的路径决策。

#### 改进要求

1. 明确区分两类路径，并固定规则：
   - 工具缓存：供复用，允许放在稳定缓存根目录
   - 任务产物：面向当前工作区，应落在可见、可管理的位置
2. 明确豁免范围：
   - launcher release 缓存可以继续保留现状
   - 本轮只要求统一 analyst 层输入缓存、导出文件、运行产物、扫描结果的默认落点
3. 不要再用“skill 安装目录的上级仓库”推导 analyst 缓存根。
   - `repo_root()` 不应隐式绑定到 `/root/.codex`
   - 应显式使用用户工作区、调用时传入的 `artifact_root`，或专门的 analyst cache env
4. 为 analyst 层增加统一配置项，例如二选一或同时支持：
   - `DEXCLUB_ANALYST_CACHE_DIR`
   - `DEXCLUB_ANALYST_WORK_ROOT`
5. 给默认策略定规矩：
   - 输入缓存可放统一 analyst cache 根
   - 当前任务内部状态与运行产物默认放工作区 `.dexclub-cli/`
   - 用户可见导出物与报告默认放工作区 `artifacts/` / `reports/` / `patched/`
   - 不应再默认落到匿名 `/tmp` 目录
6. 所有 helper script 的默认路径行为必须一致：
   - `resolve_apk_dex.py`
   - `export_and_scan.py`
   - `analyze.py run`
   - 以及后续统一入口
7. 输出结果中应明确标注：
   - `cache_root`
   - `artifact_root`
   - `temporary_paths`
8. 如果确实需要临时目录，任务结束时至少要：
   - 把最终保留产物复制/移动到稳定目录
   - 或在结果中明确说明临时目录路径和生命周期

#### 验收标准

1. 用户在独立工作区运行 skill 时，能稳定预期缓存和产物会落到哪里。
2. 不再出现一部分结果在 `/root/.codex/build/...`、一部分在工作区 `artifacts/`、一部分在 `/tmp/...` 的混乱状态。
3. release 缓存与 analyst 产物边界清晰，不再被混为同一种“缓存”。
4. 文档、实际行为和最终输出中的路径说明保持一致。

详细目录方案和 run / step 结构建议，见 [`docs/analyst/OUTPUT_NORMALIZATION.md`](./docs/analyst/OUTPUT_NORMALIZATION.md) 中的“工作区目录定稿”与“跨会话复用与接续规则”。

### 2. 收敛 skill 对外入口，禁止把内部 helper 暴露成并列主接口

#### 问题

当前 skill 名义上要求“launcher 是单一入口”，但实际使用中代理会直接调用：

- `launcher/scripts/run_latest_release.sh`
- `analyst/scripts/run_find.py`
- `analyst/scripts/resolve_apk_dex.py`
- `analyst/scripts/export_and_scan.py`

这导致 skill 对外看起来像一组松散脚本，而不是一个稳定能力边界。代理很容易在不同层级之间来回切换，最终出现：

- 参数风格不一致
- 输入输出契约不一致
- 出错后难以判断该修哪一层
- “遵循 launcher 单一入口”在事实上被破坏

#### 改进要求

1. 明确分层：
   - `launcher/` 只负责缓存版 CLI 准备与真实 CLI 执行。
   - `analyst/` 只负责工作流组合，不应再暴露多个彼此并列的裸脚本入口给代理随意拼接。
2. 为 analyst 层增加稳定入口：
   - 至少提供一个统一脚本，例如 `analyst/scripts/run_workflow.py` 或等价入口。
   - 由该入口承接常见任务，而不是让代理直接记忆多个内部脚本。
3. 在 `SKILL.md` 中明确标注：
   - 哪些是唯一推荐入口
   - 哪些脚本仅供内部复用，不建议代理直接调用
4. 对内部 helper 增加注释或文档声明：
   - “内部脚本，不保证参数稳定性”
   - 或直接改名为更明显的内部实现命名

#### 验收标准

1. 常见任务文档中不再要求代理直接拼装多个 analyst 裸脚本。
2. 新代理按 `SKILL.md` 首次使用时，能够只依赖 1 个稳定入口完成常见分析任务。
3. 会话日志中 launcher 与 analyst helper 混用的频率明显下降。

### 3. 统一参数契约，消除真实 CLI 与 helper script 的接口漂移

#### 问题

当前会话里连续出现了几类误用：

- `run_find.py` 的 `--input`、子命令、`--limit` 所在位置容易写错
- helper script 参数风格和真实 CLI 不一致
- 同样是“查找方法”，launcher CLI 和 `run_find.py` 的调用心智模型不同
- 方法重载一旦出现，helper 很难表达“精确到 descriptor 的调用关系查询”，代理会被迫退回手写原始 `query-json`

这不是单次手误，而是接口设计本身在诱发误用。

#### 改进要求

1. 为 analyst 包装层建立统一参数规范，优先向真实 CLI 靠拢：
   - 选项顺序
   - 多输入写法
   - `--limit` 等通用参数位置
   - 输出格式参数命名
2. `run_find.py` 若保留，至少做到以下两点之一：
   - 完全兼容真实 CLI 的主要参数组织方式
   - 或在参数错误时输出明确迁移提示，而不是只给 argparse 默认报错
3. 为高频误用场景补兼容层：
   - 例如接受更宽松的参数位置
   - 或自动识别常见错误并给出纠正后的推荐命令
4. 所有 helper script 的 `-h/--help` 输出应补充：
   - 与真实 CLI 的关系
   - 参数位置示例
   - 常见错误示例
5. 关系查询必须支持精确目标表达，至少满足以下其一：
   - `--invoke-method` / `--caller-method` 可直接接受完整方法 descriptor
   - 或新增显式参数传入类名、方法名、参数列表、返回类型
6. 对重载方法的高频场景，应避免把“必须手写整段原始 JSON”作为常规路径。

#### 验收标准

1. 新代理首次调用 `run_find.py` 不需要试错即可成功执行常见查询。
2. 常见错误输入能返回明确、可操作的纠正提示。
3. 会话中不再频繁出现“参数顺序踩坑后再重跑”。
4. 遇到重载方法时，代理无需手写底层 `query-json` 也能精确查询调用关系。

### 4. 修复多 dex APK 的高成本类定位流程

#### 问题

`resolve_apk_dex.py` 当前做法是：

1. 枚举 APK 内全部 `classes*.dex`
2. 逐个对每个 dex 执行类查找
3. 每查一个类都重复做一遍

在大 APK 上代价非常高。一次会话里多次查类，会反复消耗几十秒到一分钟级别，严重拖慢任务，也放大会话 token 和命令数量。

#### 改进要求

1. 为 APK -> class -> dex 的解析结果建立稳定缓存：
   - 至少按 APK 指纹缓存已解析的类所在 dex
   - 同一 APK 同一类二次查询应直接命中
2. 对已提取 dex 的扫描结果建立索引，而不是每次重新全量遍历。
3. 支持批量类定位：
   - 一次输入多个类名，统一扫描，统一返回
4. 对大 APK 的重复查询提供“预构建索引”模式：
   - 先做一次索引准备
   - 后续任务直接查索引
5. 输出中增加缓存命中信息，便于判断当前是否在重复做重活。

#### 验收标准

1. 同一 APK 下重复解析不同类时，不再每次都触发全量 dex 扫描。
2. 第二次查询相同类时，耗时应显著下降。
3. 常见逆向分析会话中，`resolve_apk_dex.py` 不再成为最长耗时热点。

### 5. 提供面向常见任务的稳定工作流入口，减少“边跑边试”

#### 问题

当前 skill 有文档说明，但缺少足够稳定的“任务级入口”。用户一旦要求：

- 方法逻辑分析
- 调用链追踪
- APK 中类所在 dex 反推
- 导出后继续扫描

代理往往只能自己拼工作流，导致：

- 重复读取文档
- 先试错再收敛
- 中途频繁切换工具层

#### 改进要求

至少补齐以下任务级入口，名称可调整，但职责应单一明确：

1. `analyze-method-logic`
   - 输入 APK/dex、类名、方法名
   - 自动完成类定位、导出、摘要
2. `find-callers`
   - 输入目标方法
   - 返回调用者列表和必要证据
3. `resolve-class-dex`
   - 输入 APK、类名
   - 返回唯一 dex 或候选 dex
4. `export-class-and-scan`
   - 输入 dex/APK、类名、方法名
   - 导出并生成结构化摘要

这些入口可以仍由 analyst 层组合实现，但对代理应表现为稳定能力，而不是脚本拼装题。

#### 验收标准

1. 方法逻辑分析、调用链追踪等高频任务有明确单入口。
2. 新代理完成同类任务时，不需要先读多份 workflow 文档再自己组装。
3. 一轮常见 APK 分析任务的命令数明显下降。

### 6. 增强错误消息，把“失败”变成“可纠正”

#### 问题

当前多数错误仍停留在：

- argparse 原始报错
- 底层 CLI 用法提示
- “类不在该 dex 中”这类孤立事实

这些信息对人类维护者还算够用，但对代理不够友好，无法快速纠偏。

#### 改进要求

1. 为常见错误加结构化提示：
   - 参数位置错误
   - 多 dex 输入方式错误
   - 类不在当前 dex
   - 需要先走 APK->dex 解析
2. 错误输出应包含下一步建议，例如：
   - 推荐改用哪个脚本
   - 推荐改成什么参数顺序
   - 推荐先运行哪一步
3. 如果能够确定错误原因，应优先输出诊断结论，而不是只转发底层 stderr。

#### 验收标准

1. 同类错误发生一次后，代理能直接按提示完成修正。
2. 错误输出中包含“原因 + 建议动作”，而不只是“原因”。

### 7. 明确“已验证事实 / 推断”在工作流输出中的结构边界

#### 当前状态补充

当前仓库实现已经补上统一任务级结构：

- `verifiedFacts`
- `inferences`
- `unknowns`
- `nextChecks`

其中 `analyze.py run` 的最终结果、`plan` 的结构化错误输出，以及 `runs inspect --include-final-result` 回读到的持久化 `final_result.json` 都已带出这四组字段；当现有 evidence 可用时，还会附带 `source_path` / `line_numbers` 等轻量定位信息。

#### 问题

当前 skill 依赖代理自己在最终答复里区分“已验证事实”和“基于证据的推断”。这条原则是对的，但没有落到工具输出层，就会导致：

- 中途状态更新容易说得过满
- 某一步还没完全验证就先口头闭环
- 用户难判断当前结论的确定性

#### 改进要求

1. 为 analyst 结果增加统一输出结构，例如：
   - `verifiedFacts`
   - `inferences`
   - `unknowns`
   - `nextChecks`
2. 对方法逻辑、调用链、安装点定位等分析结果，默认返回证据片段和证据位置。
3. 在 workflow 文档中明确：
   - 哪些结论必须有导出代码或查询结果支撑
   - 哪些只能写成推断

#### 验收标准

1. 任务级输出天然区分已验证与推断，不依赖代理自由发挥。
2. 会话中“先说找到、后面又补验证”的情况明显减少。

### 8. 为大 APK 分析增加结果复用与中间态管理，避免重复劳动

#### 问题

当前 skill 虽然提到缓存，但更多聚焦 release cache。分析态缓存还不够完整，导致：

- 同一类反复导出
- 同一方法反复扫描
- 同一 APK 重复做中间解析
- 最终会话又长又重

#### 改进要求

1. 为以下中间结果建立复用策略：
   - APK 提取 dex
   - 类所在 dex
   - 导出的 smali/java
   - 方法扫描摘要
2. 结果缓存应可追溯到输入指纹：
   - APK hash
   - dex hash
   - 类名
   - 方法签名
3. 若命中已有中间结果，应默认复用，并在输出里说明复用来源。

#### 验收标准

1. 重复导出、重复扫描显著减少。
2. 同一任务二次执行时，总命令数和总耗时明显下降。

### 9. 保证结构化输出纯净，禁止在 JSON 模式下混入日志和非 JSON 前缀

#### 问题

当前 analyst 层的多个脚本虽然支持 `--format json`，但实际 stdout 仍会混入额外内容，例如：

- 底层 CLI 的信息日志
- `output=...` 之类的导出路径前缀
- 其他非 JSON 的辅助说明

这会导致几个实际问题：

- helper 之间串联时，不能直接把 stdout 当成稳定 JSON 消费
- 调用方不得不自己猜“哪一行开始才是真正的 JSON”
- 脚本内部被迫增加脆弱的 JSON 提取逻辑，而不是基于稳定契约组合

这不是单个脚本的小瑕疵，而是 analyst 层可组合性和自动化可靠性的基础问题。

#### 改进要求

1. 明确输出契约：
   - `--format json` 时，stdout 只能输出单个完整 JSON 文档
   - 所有日志、提示、诊断信息统一改走 stderr
2. helper script 间如果需要串联，应默认假设上游 `json` 输出可直接被下游解析，而不是再做“JSON 提取”
3. 导出路径、缓存路径、命中信息等辅助字段，应作为 JSON 字段返回，而不是拼成前缀文本
4. 若底层 CLI 无法完全静默其日志，应由 analyst 层统一做隔离或转发，避免污染结构化输出

#### 验收标准

1. 任一支持 `--format json` 的脚本，其 stdout 都可直接交给 `jq`、Python `json.loads` 或下游 helper 消费。
2. 不再需要通过“跳过前几行”或“从 stdout 中猜 JSON 起始位置”的方式解析结果。
3. 导出路径、缓存命中等辅助信息均能在 JSON 字段中稳定取得。

详细输出契约、错误骨架、`latest.json` / `run-summary.json` 与轻量校验边界，见 [`docs/analyst/OUTPUT_NORMALIZATION.md`](./docs/analyst/OUTPUT_NORMALIZATION.md)。

### 10. 把工作区已有 dex 集合提升为一等输入源，避免明明已解包仍被迫重走 APK 解析

#### 问题

当前 skill 虽然能处理 APK 并从中提取 `classes*.dex`，但对“工作区里已经存在的一组 dex”支持不够自然，导致：

- 代理即使已经发现 `artifacts/demo_dex/` 之类的现成产物，也难以把它们当成稳定输入源复用
- 一旦直接传 `classes*.dex` 通配或一组 dex 路径，参数组织和多输入行为很容易踩坑
- 后续工作流又退回到 APK -> 提取 dex -> 再解析的路径，重复制造中间态

这不是单纯的路径问题，而是 analyst 层缺少“dex 目录 / dex 集合”这一层稳定输入模型。

#### 改进要求

1. 为 analyst 层补充对“已存在 dex 集合”的一等支持，例如：
   - `--input-dex-dir`
   - `--input-dex-list`
   - 或等价的 manifest / 索引文件方案
2. 当工作区已存在可复用的 `classes*.dex` 集合时，常见工作流应能直接基于该集合执行，而不是默认退回 APK 解析
3. 对 dex 集合输入的排序、去重、多输入展开规则给出统一约定，避免通配展开和参数位置再次诱发误用
4. 在输出结果中明确说明本次使用的是：
   - APK 直查
   - APK 缓存提取 dex
   - 工作区已有 dex 集合

#### 验收标准

1. 工作区已存在 `classes*.dex` 目录时，代理无需重提取即可直接完成常见查询和导出任务。
2. dex 集合输入方式稳定、可文档化，不再依赖 shell 通配碰运气。
3. 会话里“明明已有 dex 产物却仍回退到 APK 全量解析”的情况明显减少。

## 建议的实施顺序

1. 先修缓存与产物路径统一。
2. 再修入口收敛和参数契约统一。
3. 再补结构化输出纯净化，先把 JSON 契约做稳。
4. 然后修多 dex 定位缓存、工作区 dex 集合输入与任务级入口。
5. 再补错误消息、事实/推断结构化输出。
6. 最后完善分析态缓存和中间结果复用。

## 最低交付要求

如果当前迭代无法一次性完成全部改造，至少应先做到以下最低交付：

1. 新增一个统一 analyst 入口，承接高频任务。
2. 统一 analyst 缓存根与任务产物根，去掉隐式 `/root/.codex/build` 和匿名 `/tmp` 默认落点。
   - 目标方向是工作区 `.dexclub-cli/` 内聚，而不是继续沿用 `build/dexclub-cli/`
3. 统一 `run_find.py` 与真实 CLI 的参数组织方式，或补足兼容层。
4. 保证所有 `--format json` 输出在 stdout 上都是纯净 JSON。
5. 为 `resolve_apk_dex.py` 加 class->dex 缓存，并支持工作区已有 dex 集合作为稳定输入。
6. 为常见错误补“原因 + 建议动作”。
7. 在任务级输出中固定区分“已验证事实”和“推断”。

## 备注

本清单的目标不是把 skill 做成“大而全”的自动化黑箱，而是把现有能力边界做稳、做清楚、做低误用。只要这些基础问题不修，后续继续堆 workflow 和 helper，只会让代理更容易在独立工作区里跑偏。
