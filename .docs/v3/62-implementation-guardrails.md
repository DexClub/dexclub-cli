# DexClub CLI V3 实现红线

## 目标

本文件只回答一件事：

`实现过程中哪些事绝对不能做，哪些边界绝对不能破`

它不重复命令面与对象模型细节，只给实现红线。

## 总体原则

1. `core` 是能力库，不是 CLI 层
2. `cli` 是命令应用层，不是业务能力层
3. 新实现必须优先保持边界清晰，而不是优先追求“先跑起来”
4. 若某个实现捷径会污染边界，则默认不允许

## 硬性红线

### 1. `cli` 不得依赖 `core.impl.*`

允许：

- `core.api.*`
- `createDefaultServices()`
- `Services`

禁止：

- 直接引用 `DefaultWorkspaceService`
- 直接引用 `DefaultDexAnalysisService`
- 直接引用 `DefaultResourceService`
- 直接引用 `WorkspaceStore`
- 直接引用任何 executor

### 2. `core` 不得接受 CLI 语义输入

禁止在 `core` 公共 API 中出现：

- `CliRequest`
- `--query-json`
- `--query-file`
- `--json`
- `help`
- `usage`
- `stdout`
- `stderr`

`core` 只能接受业务 request。

### 3. `core` 不得输出终端文案

禁止：

- 在 `core` 中拼接最终英文错误文案
- 在 `core` 中拼接 text 输出
- 在 `core` 中决定 JSON 渲染格式

`core` 只返回结构化结果或抛结构化领域错误。

### 4. `.dexclub` 读写必须统一经过 `WorkspaceStore`

禁止：

- 在 service 中直接读写 `workspace.json`
- 在 runtime resolver 外直接扫描 `.dexclub` 树结构
- 在 adapter 中直接拼接 `.dexclub/targets/...`
- 在 executor 中直接碰 `.dexclub`

### 5. 不得重新引入总管类

禁止新增类似：

- `WorkspaceManager`
- `CoreManager`
- `AnalysisManager`
- `RuntimeManager`
- `ServiceManager`

如果一个类同时负责：

- store
- runtime
- dex 执行
- resource 执行
- CLI 适配

则说明设计已经跑偏。

### 6. 不得让 DTO 穿透为公共 API

禁止将以下对象直接暴露给 `cli` 或其它调用方：

- `WorkspaceDto`
- `TargetDto`
- `SnapshotDto`
- cache 文件 DTO

公共 API 只能暴露：

- `WorkspaceContext`
- `WorkspaceStatus`
- `TargetHandle`
- `TargetSnapshotSummary`
- capability service 的 request / result

### 7. 不得绕过 `consumers/`

若上层调用方需要写入自己的附加数据，只能落到：

```text
.dexclub/consumers/<consumer-id>/
```

禁止：

- 写入 `targets/`
- 写入 `workspace.json`
- 写入 `snapshot.json`
- 写入 `cache/`

### 8. 不得提前做“未来框架”

禁止为了“以后可能有用”而提前引入：

- 插件机制
- 扩展注册表
- 通用事件总线
- 跨入口统一 facade 大框架
- 复杂依赖注入框架

当前阶段只允许做支撑 P0 的最小骨架。

## 强约束实现规则

### 1. `WorkspaceContext` 是统一执行上下文

所有 capability service 与 executor 都应以：

- `WorkspaceContext`

作为统一运行上下文输入。

### 2. capability gate 统一放在 service 层

禁止：

- adapter 自己复制 capability 判断
- executor 自己定义 capability gate

统一由：

- `CapabilityChecker`

完成。

### 3. 排序和分页统一在 service 层

禁止：

- adapter 自己分页
- executor 自己偷偷排序

统一由 capability service 层完成稳定排序和 `PageWindow` 应用。

### 4. query file 读取留在 `cli`

禁止：

- `core` 读取 `--query-file`
- `core` 感知 query 来源是 inline 还是 file

`core` 只能看到 `queryText`。

## 若必须偏离

若实现中必须偏离以上红线，必须同时满足：

1. 先在文档中明确记录偏离原因
2. 明确影响范围
3. 明确是临时方案还是长期方案
4. 明确后续回收条件

否则默认视为不允许。

