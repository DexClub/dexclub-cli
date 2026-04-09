# Analyst 文档索引

## 口径说明

- [ANALYST_PROGRESS.md](./ANALYST_PROGRESS.md)
  - 当前真实实现状态的主依据
  - 如果文档之间出现“现状”和“目标”口径差异，以这份文档记录的已实现状态为准
- [OUTPUT_NORMALIZATION.md](./OUTPUT_NORMALIZATION.md)
  - 后续要收敛到的目标方案与结果契约
  - 除非 [ANALYST_PROGRESS.md](./ANALYST_PROGRESS.md) 已明确记为完成，不应把其中方案视为当前代码已全部落地
- 本文档
  - 只负责索引、分工和推荐阅读顺序
  - 不单独承担“当前真实状态”或“目标方案已落地状态”的判定职责

## 当前文档

- [ANALYST_PROGRESS.md](./ANALYST_PROGRESS.md)
  - 当前真实状态、最近提交、验证结果、下次会话入口
- [ANALYST_ROADMAP.md](./ANALYST_ROADMAP.md)
  - `V1 / V2 / V3` 路线与后续扩展方向
- [OUTPUT_NORMALIZATION.md](./OUTPUT_NORMALIZATION.md)
  - 输出净化、工作区内聚、跨会话接续与 analyst 对外结果契约

## 目标方案速览

如果当前要处理的是 helper 输出污染、工作区产物落点、断会话接续或结果对象约定，建议先读 [OUTPUT_NORMALIZATION.md](./OUTPUT_NORMALIZATION.md)。

下面这组结论是“目标收敛口径”的摘要，主要用于帮助快速进入设计上下文，不代表当前仓库代码已经全部实现。

其中最关键的结论可以先压成下面几条：

1. 第三方 `stdout/stderr` 一律视为不可信原始流；`--format json` 时，正式 `stdout` 只能由 helper / runner 自己重发纯净 JSON。
2. `release` 缓存之外的 skill 内部状态统一落在当前工作区下的 `.dexclub-cli/`，不再使用 `build/dexclub-cli/`。
3. 用户可见产物继续分流到 `artifacts/`、`reports/`、`patched/`，但默认先完整保留内部结果，再按需要提升关键产物。
4. `run-root` 只放 run 级对象，`step-root` 聚合单步结果、raw log 和局部附属文件；不再平铺 `results/`、`resolved/`、`logs/`。
5. 跨会话机器复用靠 `cache key`，无上下文新会话接手靠 `latest.json + run-summary.json`；方案支持“可恢复”，但不等于默认自动续跑。
6. `partial` 只用于 run 级状态，定义为“未完成，但已留下可接手资产”；`ok` 必须按 `task_type` 的完成条件判断。
7. 当前阶段先采用“文档约定 + 轻量 validator + schema_version”，后续字段稳定后再考虑正式 schema。

## 归档文档

- [ANALYST_PLANNER_PLAN.md](./archive/ANALYST_PLANNER_PLAN.md)
  - `V1` 规划器 / 执行器设计记录
- [ANALYST_STORAGE_PLAN.md](./archive/ANALYST_STORAGE_PLAN.md)
  - `V1` 工作目录与输入缓存设计记录
- [ANALYST_V1_HISTORY.md](./archive/ANALYST_V1_HISTORY.md)
  - `V1` 阻塞、恢复与完成过程的历史记录

## 推荐阅读顺序

1. 先读 [ANALYST_PROGRESS.md](./ANALYST_PROGRESS.md)
2. 再读 [ANALYST_ROADMAP.md](./ANALYST_ROADMAP.md)
3. 如果要处理输出污染、JSON 契约、工作区产物落点、跨会话接续或 helper 串联问题，再读 [OUTPUT_NORMALIZATION.md](./OUTPUT_NORMALIZATION.md)
4. 如果要回看 `V1` 的设计依据或历史过程，再看 `archive/` 下的记录
