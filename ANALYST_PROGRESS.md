# Analyst 进度文档

## 目的

这份文档用于记录 `skills/dexclub-cli-launcher/analyst` 这一条线的实际落地进度。

它和 [ANALYST_PLANNER_PLAN.md](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/ANALYST_PLANNER_PLAN.md) 的分工不同：

- `ANALYST_PLANNER_PLAN.md`
  - 记录设计目标、边界和版本 1 方案
- `ANALYST_PROGRESS.md`
  - 记录当前真实实现状态、阶段节奏、下一步入口和流转规则

下次会话如果没有上下文，先读这份文档，再决定是否需要回看设计文档或具体代码。

## 当前快照

- 当前最新相关提交
  - `7007464` `Support descriptor-aware analyst anchors`
  - `a51c692` `Add APK summarize resolution to analyst`
  - `d14413d` `Add analyst ambiguous sample validation`
  - `b85be18` `Add analyst planner and runner v1`
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
  - descriptor-aware 的 `summarize_method_logic`，当前只支持 `smali`
  - 大方法 `smali` summarize 的结构化压缩
    - 固定阈值：`line_count >= 120`
    - 压缩输出：`large_method_analysis`
    - 当前按 `method_calls / strings / numbers / field_accesses / branch_hotspots` 分组，并补充行簇聚合
- 当前验证脚本
  - [validate_v1_sample.sh](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/validate_v1_sample.sh)
- 最近一次通过验证的命令
  - `bash ./skills/dexclub-cli-launcher/analyst/scripts/validate_v1_sample.sh`
- 最近一次通过验证的结果目录
  - `/tmp/dexclub-analyst-v1.8pPbfd/results`

## 当前边界

- `summarize_method_logic` 的 descriptor-aware 精确切片目前只支持 `smali`
- `language=java` 的普通 summarize 可以用，但“descriptor-aware + java”当前明确返回 `unsupported`
- `large_method_analysis` 当前只对 `smali` 大方法启用；`java` 和非大方法会保留 `is_large_method=false`
- 当前仍然是 analyst 层编排，不是主仓库 `cli / core / dexkit` 的正式产品化能力扩展
- 当前工作区存在无关状态，继续开发时不要混入
  - `dexkit/vendor/DexKit` 子模块改动
  - 未跟踪的 `ANALYST_PLANNER_PLAN.md`

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
| A-01 | analyst v1 planner/runner 基础落地 | 已完成 | 提交 `b85be18` |
| A-02 | v1 样例验证与真实输出样例文档 | 已完成 | 提交 `d14413d` |
| A-03 | APK 输入下自动定位目标 dex 后 summarize | 已完成 | 提交 `a51c692` |
| A-04 | descriptor-aware 的 trace/summarize 最小闭环 | 已完成 | 提交 `7007464` |
| A-05 | 大方法 smali 的二级拆解与上下文压缩 | 已完成 | 本次会话完成。新增 `large_method_analysis`，阈值 `120`，并补了样例验证 |
| A-06 | descriptor-aware + `java` summarize | 待开始 | 当前明确不优先做 |
| A-07 | 更强的 summary 结构化输出 | 待开始 | 例如按基本块、调用簇、常量簇输出 |

## 最近一次状态流转

- `A-05`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 目标代码已落地
    - `validate_v1_sample.sh` 已补充大方法压缩断言
    - `README` 与样例文档已同步 `large_method_analysis`

## 下一步推荐入口

下一个对话优先做 `A-07`，不要先做 `A-06`。

原因：

- `A-05` 已经把“大方法全文直接喂模型”的问题降下来了
- 下一步更值得做的是把现有热点分组再继续结构化，例如基本块级摘要、调用簇和常量簇
- `java exact summarize` 仍然不是当前最痛点

## A-05 完成记录

本次完成内容：

1. 定义了“大方法”阈值
   - 当前使用固定规则：`smali` 且 `line_count >= 120`
2. 给 summarize 结果增加 `large_method_analysis`
   - 按 `method_calls / strings / numbers / field_accesses / branch_hotspots` 分组
   - 每组补 `top_items`
   - 每组补基于行号邻近度的 `clusters`
3. 保留原始导出代码产物
   - 仍然通过 `export_path` 指向完整文件
   - 没有移除原有统计字段

本次刻意没做：

1. CFG
2. 完整语义块推断
3. 更细粒度的局部代码片段抽取

## 下次会话快速开始

下次会话如果没有任何历史上下文，按下面顺序开始：

1. 先读本文件
   - [ANALYST_PROGRESS.md](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/ANALYST_PROGRESS.md)
2. 再读设计文档的相关边界
   - [ANALYST_PLANNER_PLAN.md](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/ANALYST_PLANNER_PLAN.md)
3. 确认工作区无关状态，不要误处理
   - `git status --short`
4. 确认最近相关提交
   - `git log --oneline -n 6`
5. 直接进入 `A-07`
   - 重点看：
     - [code_analysis.py](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/code_analysis.py)
     - [export_and_scan.py](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py)
     - [runner.py](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/runner.py)
     - [validate_v1_sample.sh](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/validate_v1_sample.sh)

## 下次会话可直接使用的提示词

如果下次会话要最快恢复，可以直接用这句：

```text
先阅读仓库根目录的 ANALYST_PROGRESS.md，按其中“下次会话快速开始”执行，然后继续推进 A-07，不要先做 java exact summarize。
```

## 文档维护规则

后续每次继续这条线时，优先更新这份文档，而不是把状态散落在多个地方。

至少同步以下内容：

- 当前主推进任务是哪一个
- 哪个任务从什么状态流转到了什么状态
- 新增提交号
- 新增验证命令或验证结果
- 如果下一步优先级变化，要同步更新“下一步推荐入口”
