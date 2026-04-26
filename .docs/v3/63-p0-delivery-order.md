# DexClub CLI V3 P0 实现顺序

## 目标

本文件只定义：

`P0 应按什么顺序推进，避免实现过程中无序扩散`

## 总原则

1. 先打通基础骨架，再补能力
2. 先打通工作区，再打通 dex，再打通资源
3. 先做稳定链路，再做增强功能
4. 不跳步做边缘能力

## P0 顺序

### 1. 模块骨架

先建立：

- `core`
- `cli`

并确认：

- 依赖方向清晰
- 仓库收口后只保留一套 `core / cli` 模块承接新能力
- 包名与类名不带 `v3`

### 2. `core` 基础对象与装配入口

先落地：

- `WorkspaceRef`
- `WorkspaceContext`
- `TargetHandle`
- `WorkspaceStatus`
- `TargetSnapshotSummary`
- `Services`
- `createDefaultServices()`

这一步只做骨架，不求完整能力。

### 3. `WorkspaceStore` 与 `WorkspaceRuntimeResolver`

先打通：

- `.dexclub` 初始化
- `workspace / target / snapshot` 读写
- 工作区打开
- snapshot 刷新
- status 读取
- gc

完成后应能支撑：

- `init`
- `status`
- `gc`

### 4. `WorkspaceService`

基于上一步打通：

- `initialize`
- `open`
- `loadStatus`
- `gc`
- `inspect`

完成后，CLI 最小可运行闭环应成立。

### 5. `cli` 基础链路

建立：

- `CliParser`
- `CommandDispatcher`
- `WorkspaceCommandAdapter`
- `InspectCommandAdapter`
- `Renderer`
- `OutputWriter`

完成后至少打通：

- `init`
- `status`
- `gc`
- `inspect`

### 6. dex 查询能力

实现：

- `DexSearchExecutor`
- `CapabilityChecker`
- `DefaultDexAnalysisService`
- `DexSearchCommandAdapter`

按顺序打通：

1. `find-class`
2. `find-method`
3. `find-field`
4. `find-class-using-strings`
5. `find-method-using-strings`

### 7. dex 导出能力

实现：

- `DexExportExecutor`
- `ExportCommandAdapter`

按顺序打通：

1. `export-class-smali`
2. `export-method-smali`
3. `export-class-dex`
4. `export-class-java`

### 8. 资源能力

实现：

- `ManifestExecutor`
- `ResourceTableExecutor`
- `XmlExecutor`
- `ResourceValueExecutor`
- `DefaultResourceService`
- `ResourceCommandAdapter`

按顺序打通：

1. `manifest`
2. `res-table`
3. `decode-xml`
4. `list-res`
5. `resolve-res`
6. `find-res`

## 暂不进入 P0 的内容

以下内容不应阻塞 P0：

- 方法结构图 / 方法流程图
- AST 风格导出
- deep 调用图
- GUI 消费者数据
- `consumers/` 下扩展协议
- 插件化/扩展机制

## 顺序约束

### 不允许跳过的基础项

以下基础项未完成时，不应继续上层能力：

- `Services + createDefaultServices()`
- `WorkspaceStore`
- `WorkspaceRuntimeResolver`
- `CapabilityChecker`
- `Renderer / OutputWriter`

### 不建议的跳步

禁止优先做：

- `export-class-java`
- `find-res`
- 高级资源值语义

而跳过：

- `status`
- `inspect`
- `find-method`
- `export-class-smali`

## 完成定义

每完成一个阶段，至少要满足：

1. 对应模块可编译
2. 对应命令链路可跑通
3. 未破坏既定输出契约
4. 未引入跨层依赖污染

