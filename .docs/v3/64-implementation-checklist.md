# DexClub CLI V3 实现检查清单

## 目标

本文件用于在每一轮实现完成后自查：

`这次实现有没有跑偏，有没有破坏当前设计边界`

## 架构自查

### 1. 分层是否仍然成立

确认：

- `cli` 只做解析、适配、渲染、输出
- `core` 只做工作区与能力
- `cli` 未直接依赖 `core.impl.*`
- `core` 未接受 `CliRequest`

### 2. 是否重新长出总管类

检查是否新增类似：

- `*Manager`
- `*Coordinator`
- `*Facade`

若新增类同时承担了：

- store
- runtime
- search/export/resource 执行
- CLI 适配

则应视为设计退化。

### 3. 是否绕过 `WorkspaceStore`

确认：

- 没有在 service 中直接读写 `.dexclub`
- 没有在 adapter 中直接操作 `workspace.json / target.json / snapshot.json`
- cache 清理统一走 `WorkspaceStore`

### 4. 是否复制了业务规则

确认以下规则仍集中存在：

- capability gate 在 service 层
- 排序在 service 层
- `PageWindow` 应用在 service 层
- query file 读取只在 `cli`

若发现同一规则在多个层重复实现，应回收。

## API 自查

### 5. 公共对象是否仍然干净

确认公共 API 只暴露稳定对象：

- `WorkspaceRef`
- `WorkspaceContext`
- `TargetHandle`
- `WorkspaceStatus`
- `TargetSnapshotSummary`
- request / result 模型

不得暴露：

- `WorkspaceDto`
- `TargetDto`
- `SnapshotDto`
- cache DTO

### 6. 包名与类名是否带版本号

确认：

- 模块名可以带 `-v3`
- 包名与类名不带 `v3 / V3`

### 7. CLI 语义是否泄漏进 `core`

检查是否在 `core` 中出现：

- `--json`
- `--query-file`
- `--query-json`
- `help`
- `usage`
- `stdout`
- `stderr`

若出现，应视为边界破坏。

## 命令契约自查

### 8. 输出契约是否被破坏

确认：

- `stdout` 只放结果
- `stderr` 只放错误/提示
- text 输出仍保持固定顺序和表头
- JSON 仍直接输出业务对象/数组
- `export-*` 成功仍固定输出 `output=<absolute-path>`

### 9. workdir 规则是否被破坏

确认：

- `init` 仍必须显式传 `input`
- 非 `init` 命令省略 `workdir` 时仍只使用 `cwd`
- 仍不向上查找
- 仍不自动初始化

### 10. query 规则是否被破坏

确认：

- `--query-json` / `--query-file` 仍然二选一
- `--query-file` 仍按 UTF-8 读取
- `offset` / `limit` 校验未漂移

## 工作区自查

### 11. `.dexclub` 结构是否仍然受控

确认：

- 只由 `WorkspaceStore` 维护受管结构
- 未擅自新增新的状态根层级
- 未把临时文件塞进受管目录

### 12. `consumers/` 边界是否被破坏

确认：

- `core` 不读取 `consumers/`
- `core` 不清理 `consumers/`
- 上层扩展数据只落在 `consumers/<consumer-id>/`

## 验证自查

### 13. 是否做了最小可用验证

至少确认本轮直接影响到的模块或链路：

- 可以编译
- 对应命令链路可跑通
- 输出契约符合文档

### 14. 是否更新了文档

若本轮改动涉及：

- 模型边界
- 命令契约
- 输出契约
- 存储结构
- 模块依赖

则应同步更新 `.docs/v3` 中对应文档。

## 通过标准

一轮实现只有在以下条件全部满足时，才应视为可继续推进：

1. 没有新增跨层依赖污染
2. 没有新增总管类或万能 facade
3. 没有绕过 `WorkspaceStore`
4. 没有把 CLI 语义带进 `core`
5. 没有破坏输出与命令契约
6. 做了最小可用验证

