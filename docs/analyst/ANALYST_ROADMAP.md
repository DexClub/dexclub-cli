# Analyst 路线图

## 目的

这份文档用于记录 `skills/dexclub-cli-launcher/analyst` 的版本路线。

它不替代进度文档，也不替代 V1 的设计记录。分工如下：

- [README.md](./README.md)
  - 记录 analyst 文档索引与推荐阅读顺序
- [ANALYST_PROGRESS.md](./ANALYST_PROGRESS.md)
  - 记录当前真实状态、最近提交、验证结果和下次会话入口
- [ANALYST_PLANNER_PLAN.md](./archive/ANALYST_PLANNER_PLAN.md)
  - 归档的 V1 规划器 / 执行器设计边界
- [ANALYST_STORAGE_PLAN.md](./archive/ANALYST_STORAGE_PLAN.md)
  - 归档的 V1 工作目录与输入缓存设计边界
- `ANALYST_ROADMAP.md`
  - 记录 V1 之后的扩展方向和版本节奏

## 当前判断

- `V1` 已收口
- `V1` 后续原则上不再扩展新的主能力边界
- `V1` 仍可接受：
  - 回归修复
  - 验证补强
  - 文档维护
  - 不改变 analyst / cli 边界的低风险维护性改动

## V1

### 当前定位

`V1` 是当前最优、最稳的 analyst 方案。

它已经覆盖：

- 受限任务集下的 `plan` / `run`
- `search_methods_by_string`
- `search_methods_by_number`
- `trace_callers`
- `trace_callees`
- `summarize_method_logic`
- APK 输入下自动解析目标 dex
- descriptor-aware 的 `trace_*`
- descriptor-aware 的 `summarize_method_logic`
- `smali` 的 `structured_summary`
- 大方法 `smali` 的 `large_method_analysis`
- 工作目录下的 run 产物与输入缓存

### V1 原则

- 不再继续向 `V1` 塞新的主能力
- 新需求若明显超出当前受限任务集，应优先归入 `V2`
- `cli` 继续保持独立，skill 继续直接控制 `cli`

## V2

### 目标

在不推翻 `V1` 核心边界的前提下，扩 analyst 的任务覆盖面和多步能力。

### 候选方向

- 增加新的任务类型，而不只是当前五类任务
- 支持更强但仍受控的多步工作流
- 支持更好的结果关联、范围缩小和后续建议
- 增强已有辅助脚本之间的协同，而不是堆砌新的独立脚本
- 在保持 skill 边界不下沉到 `cli / core / dexkit` 的前提下，提升 analyst 层的组合能力

### 可能包含的具体工作

- 更强的调用关系追踪，但仍保留边界清晰的停止条件
- 更细粒度的导出与复用策略
- 更结构化的证据组织
- 更好的 APK / dex 多输入处理能力
- 与现有缓存、run 产物、验证脚本更紧密的一致性设计

## V3

### 目标

面向更复杂问题，支持更长链路、更动态的分析编排。

### 候选方向

- 更长链路的规划与重规划
- 更结构化的交互接口
- 更高层的 Android 语义分析能力
- 更强的上下文保持和中间结果复用能力

### 说明

`V3` 目前只保留方向，不提前承诺具体交付项。

## 独立于版本路线的存储后续项

下面这些不是 `V2 / V3` 的唯一路标，但属于已经明确可继续推进的工程项：

- 清理入口，例如 `clean-runs` / `clean-cache`
- 更细粒度的缓存复用
- `dex_set` 组合缓存
- APK / 会话索引
- 基于真实使用数据观察缓存体积、复用率和回归情况

## 边界提醒

无论后续进入 `V2` 还是 `V3`，都保持下面这条约束不变：

- `cli` 保持独立
- skill 直接控制 `cli`
- 不把 skill 内部状态下沉到 `cli / core / dexkit`
