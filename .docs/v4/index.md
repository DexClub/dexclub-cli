# DexClub V4

## 目录

本页只负责给 `v4` 文档定一个稳定入口。

## 当前状态

当前代码结构已经收口为：

- 根工程 4 个子项目：`cli-app / app-service / domain-core / mcp-app`
- 通过 `includeBuild("dexkit-binding")` 接入 1 个独立绑定工程：`dexkit-binding`

补充约束：

- `gui-app` 只保留为未来兼容方向，当前不实现
- `v4` 当前的主线是稳定边界与文档口径，不是继续做模块改名

## 阅读入口

第一次进入 `v4`，建议先读下面这 10 篇主干文档：

1. [positioning.md](./positioning.md)
   - `v4` 的定位、非目标与未来兼容边界
2. [architecture.md](./architecture.md)
   - 当前结构、依赖方向与职责分层
3. [application-layer.md](./application-layer.md)
   - 应用层为什么单独成立，以及它现在承接什么
4. [adapters.md](./adapters.md)
   - `cli / mcp` 入口层各自保留什么、回收什么
5. [workspace-and-session.md](./workspace-and-session.md)
   - `workspace / target session / handle / dex context` 的关系
6. [stabilization.md](./stabilization.md)
   - 当前已经固定下来的边界与短期不再优先动的事
7. [build-and-delivery.md](./build-and-delivery.md)
   - 构建链、交付路径、vendor 与 native 维护边界
8. [completion.md](./completion.md)
   - `v4` 为什么可以按完成状态理解，以及稳定维护期看什么
9. [module-decision.md](./module-decision.md)
   - 当前采用的模块结构结论
10. [module-rollout.md](./module-rollout.md)
   - 这套模块结构按什么顺序落地，以及为什么这样落
11. [dex-query-contract.md](./dex-query-contract.md)
   - MCP `query_file` 查询合同、文件边界与破坏性迁移规则

## 按状态看

### 已结合当前实现校准

下面这些文档已经明确写入当前代码状态，而不只是设计预期：

- [application-layer.md](./application-layer.md)
- [adapters.md](./adapters.md)
- [stabilization.md](./stabilization.md)
- [build-and-delivery.md](./build-and-delivery.md)
- [mcp-boundary.md](./mcp-boundary.md)
- [migration.md](./migration.md)
- [checklist.md](./checklist.md)
- [completion.md](./completion.md)
- [module-decision.md](./module-decision.md)
- [module-rollout.md](./module-rollout.md)

这里的“已结合当前实现校准”只表示：

- 文档已经写入当前代码现状
- 不再停留在纯设计猜想

它不表示：

- 对应实现已经全部结束
- 任何边界都不再允许讨论

### 主干设计文档

下面这些文档定义 `v4` 的长期边界：

- [positioning.md](./positioning.md)
- [architecture.md](./architecture.md)
- [workspace-and-session.md](./workspace-and-session.md)

### 专题文档

下面这些文档按专题补充：

- [build-and-delivery.md](./build-and-delivery.md)
- [first-cut.md](./first-cut.md)
- [gui.md](./gui.md)
- [dex-query-contract.md](./dex-query-contract.md)

### 历史归档

如果只是要理解当前项目，不要先读这里。

- [../archive/v3/README.md](../archive/v3/README.md)
  - `v3` 历史设计稿归档入口，只用于追溯早期方案与演化过程

## 当前共识

基于目前仓库状态，当前可以直接按下面这组边界理解：

- `dexkit-binding` 是绑定层，不是上层产品结构中心
- `app-service` 是 `cli / mcp` 共用的应用层入口
- `app-service` 已经持有共享 runtime、组合根入口和上层默认装配入口
- `domain-core` 负责稳定模型、能力边界与底层实现
- `cli-app` 和 `mcp-app` 继续只承担各自入口适配职责
- `gui-app` 只作为未来兼容目标进入讨论范围

但当前还不能把边界写得过满：

- `cli-app / mcp-app` 仍直接依赖部分 `domain-core` 稳定模型、错误类型与能力约束
- 所以“入口已经只依赖 `app-service`”仍不是当前事实

## 当前结构理解

结合代码阅读时，先按下面两层理解：

- 根工程子项目
  - `app-service`
    - 共享应用层
  - `domain-core`
    - 稳定能力与实现基础
  - `cli-app`
    - CLI 适配层
  - `mcp-app`
    - MCP 适配层
- 独立组合构建
  - `dexkit-binding`
    - DexKit / native 绑定层

## 稳定维护期关注点

当前继续关注的不是模块名，而是：

- `workspace` 是否继续保留为统一执行上下文
- vendor / native 构建链是否继续维持当前主仓内维护方式
- 快速验证基线是否仍保持轻量

## 文档维护方式

`v4` 文档继续遵循下面几条：

- 每篇文档只回答一个主题
- 先写边界和原则，再写执行流与落地方式
- 还没收敛的结论明确标成“待讨论”
- README 只保留项目概览和上手入口，深入说明收口到 `.docs/v4/`
