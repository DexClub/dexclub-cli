# DexClub CLI Workspace V2 实施计划

## 目标

把 [workspace-v2-design.md](/D:/Code/My/Github/dexclub-cli/.docs/v2/workspace-v2-design.md) 收敛成一条可执行实施路径，并明确：

- 哪些问题需要先在现有分支止血
- 哪些改动属于 v2 主体重构
- 每一阶段的代码范围、验证范围和完成标志

## 总体策略

采用“两段式推进”：

1. `Phase 0`
   - 先修当前会挡路的底层问题
   - 但不继续扩 v1 产品面
2. `Phase 1+`
   - 按 v2 设计稿推进破坏性重构

核心原则：

- 修通病，不继续建设 v1 旧命令面
- 先让输出契约稳定，再做命令和输入模型重构
- 每一阶段都要有明确可验证结果

## 阶段划分

## Phase 0: 先止血，不扩 v1

### 目标

修掉当前已经暴露、且无论 v1 还是 v2 都必须解决的基础问题：

1. `workspace inspect --output-format json` 失败
2. stdout 被 DexKit/JADX 日志污染

### 范围

允许修改：

- `cli/src/main/kotlin/io/github/dexclub/cli/Main.kt`
- `core/src/jvmMain/kotlin/io/github/dexclub/core/workspace/WorkspaceManager.kt`
- 必要的 `core` 输出模型
- 相关 CLI / core 测试

不做：

- `--workspace` 到位置参数的重构
- 新增 `bundle`
- 扩展新输入类型
- `workspace res` 拆命令

### 实施项

#### 0.1 修复 `inspect` JSON 输出

现状问题：

- `workspace inspect --output-format json` 当前直接失败
- 根因是 `DexArchiveInfo` 不能直接走当前 JSON 序列化路径

建议做法：

- 不直接把 `DexArchiveInfo` 暴露给 CLI JSON 输出
- 引入稳定的 CLI 输出 DTO 或显式 JSON 映射
- 顶层 `inspect` 与 `workspace inspect` 尽量复用同一输出映射

完成标志：

- `inspect --output-format json`
- `workspace inspect --output-format json`

两条路径都能稳定产出 JSON。

#### 0.2 收口第三方日志

现状问题：

- DexKit 日志污染 `stdout`
- JADX DEBUG/WARN 日志污染 `stdout`

建议做法：

- 机器可读输出时，保证 `stdout` 只包含结果
- 运行日志转 `stderr` 或默认静默
- CLI 输出层不要把第三方日志和结果流混在一起

完成标志：

- `--output-format json` 时 `stdout` 可直接被 `jq` / `ConvertFrom-Json` 消费
- `--output-file` 时 `stdout` 不额外打印机器可读结果

### 测试

至少补：

- `workspace inspect --output-format json`
- 顶层 `inspect --output-format json`
- `workspace manifest --output-format json` 不被日志污染
- `workspace res --output-format json` 不被日志污染
- `find/export` 在机器可读输出模式下 stdout 干净

### 退出条件

以下条件全部满足才进入 Phase 1：

- JSON 输出稳定
- stdout/stderr 契约稳定
- 不新增 v1 产品面

## Phase 1: CLI 签名破坏性重构

### 目标

把 `workspace` 子命令统一改为：

```bash
dexclub-cli workspace <subcommand> <dir> [options]
```

并彻底删除 `--workspace` 语法。

### 范围

重点文件：

- `cli/src/main/kotlin/io/github/dexclub/cli/Main.kt`
- `README.md`
- CLI 测试

### 实施项

#### 1.1 参数解析重排

- `workspace init <dir> --input ...`
- `workspace status <dir>`
- `workspace inspect <dir>`
- 其余 workspace 子命令全部统一

#### 1.2 help/usage 重写

- 根命令帮助
- `workspace` 帮助
- 各子命令帮助

#### 1.3 删除旧解析

- 删除 `--workspace` 参数读取
- 删除相关错误文案和测试

### 测试

- 所有 workspace 子命令位置参数解析通过
- `--workspace` 显式失败
- 帮助文本与新签名一致

### 退出条件

- 所有 workspace 子命令只接受 `<dir>`
- 文档与帮助已同步

## Phase 2: 输入类型与能力模型重构

### 目标

把当前 `apk|dex|dexs` 输入模型升级为 v2 设计稿中的：

- `apk`
- `dex`
- `dexs`
- `bundle`
- `manifest`
- `arsc`
- `axml`
- `resdir`

同时把能力矩阵从：

- `manifest`
- `res`

升级为显式资源子能力。

### 范围

重点文件：

- `core/src/commonMain/kotlin/io/github/dexclub/core/workspace/WorkspaceModels.kt`
- `core/src/jvmMain/kotlin/io/github/dexclub/core/workspace/WorkspaceManager.kt`
- `cli/src/main/kotlin/io/github/dexclub/cli/Main.kt`
- 相关测试

### 实施项

#### 2.1 扩展输入类型枚举

- 扩展 `WorkspaceInputKind`
- 升级 metadata schema/layout 版本

#### 2.2 重构 capability 字段

从 v1：

- `manifest`
- `res`

变为 v2：

- `manifestDecode`
- `resourceTableDecode`
- `xmlDecode`
- `resourceEntryList`

保留 dex 侧能力字段不变。

#### 2.3 重写 capability gate

- 所有不支持能力必须显式失败
- 错误中必须包含当前输入类型
- `bundle` 能力按实际物料动态开启

### 测试

- 所有输入类型 capability 输出正确
- gate 文案正确
- 旧 `res` 语义不再残留

### 退出条件

- v2 capability matrix 已落地到代码
- `bundle` 仍可暂时未完全实现细节，但模型必须稳定

## Phase 3: 输入识别与 `bundle` 落地

### 目标

把目录输入的自动识别规则落实到代码，并正式支持 `bundle`。

### 实施项

#### 3.1 auto infer 规则

目录输入按以下顺序判定：

1. 只有 dex 文件 -> `dexs`
2. 只有标准 `res/` 目录形态 -> `resdir`
3. 存在两类及以上核心物料 -> `bundle`
4. 只有一种核心物料但不满足纯输入约束 -> `bundle`
5. 无法稳定判断 -> 要求显式 `--type`

#### 3.2 `bundle` 物料扫描

核心物料：

- `classes*.dex`
- `AndroidManifest.xml`
- `resources.arsc`
- `res/`
- 可识别 AXML

#### 3.3 `bundle` 动态能力

- 有 dex -> 开启 dex 查询/导出
- 有 manifest -> 开启 manifest decode
- 有 arsc -> 开启资源表解码
- 有 xml -> 开启 xml decode
- 有 `res/` -> 开启资源枚举

### 测试

- `dex + arsc`
- `dex + axml`
- `arsc + axml`
- `dex + manifest + arsc + res/`
- 歧义目录必须要求显式 `--type`

### 退出条件

- `bundle` 识别稳定
- 动态 capability 可验证

## Phase 4: 资源命令面拆分

### 目标

把当前语义过宽的 `workspace res` 拆成显式命令。

### 命令面

新增：

- `workspace res-table <dir>`
- `workspace decode-xml <dir>`
- `workspace list-res <dir>`

保留：

- `workspace manifest <dir>`

顶层无状态命令同步新增：

- `manifest`
- `res-table`
- `decode-xml`
- `list-res`

### 范围

- CLI 命令定义
- help 文案
- README
- 测试

### 测试

- 新命令能被正确路由
- 输入类型不支持时显式失败
- `workspace res` 已移除

### 退出条件

- 资源分析命令语义清晰
- 不再保留含糊的 `res`

## Phase 5: 输入实现补齐

### 目标

逐个补齐新输入类型的实际实现，而不是只停留在命令面。

优先顺序建议：

1. `manifest`
2. `arsc`
3. `axml`
4. `resdir`
5. `bundle` 的组合路径补齐

### 原则

- 产品面不暴露“实现走 JADX 还是独立解析器”
- 允许不同输入类型走不同底层实现
- 不因为 `jadx` 的输入边界而扭曲 CLI 设计

### 测试

- 单文件 `AndroidManifest.xml`
- 单文件 `resources.arsc`
- 单文件二进制 XML
- 纯 `res/` 目录
- 混合 `bundle` 目录

## 文档策略

文档更新分两层：

1. `Phase 1` 后先更新 CLI 签名
2. `Phase 4/5` 后再补齐资源输入与命令说明

避免文档领先代码太多。

## 提交策略

建议至少拆成以下提交层级：

1. `Phase 0` 止血
2. `Phase 1` workspace CLI 破坏性签名重构
3. `Phase 2-3` 输入模型与 `bundle`
4. `Phase 4-5` 资源命令面与实现补齐

不要把所有 v2 改动压成一个超大提交。

## 风险与决策

### 风险 1: 过早同时重构命令面和实现层

应对：

- 先做 `Phase 0`
- 再按阶段推进

### 风险 2: `inspect` 摘要结构跨输入类型不一致

应对：

- 接受这一点
- 用稳定 JSON DTO 保证机器可读，而不是追求字段完全同构

### 风险 3: 第三方库日志干扰结果流

应对：

- 作为 Phase 0 强制解决项
- 后续阶段不再回头补救

## 当前推荐执行顺序

1. 完成 `Phase 0`
2. 完成 `Phase 1`
3. 完成 `Phase 2`
4. 完成 `Phase 3`
5. 完成 `Phase 4`
6. 完成 `Phase 5`

## 结论

这份计划的核心不是“尽快把 v2 全做完”，而是：

- 先修会挡路的底层通病
- 再做 v2 的破坏性重构
- 每一步都保持可验证、可提交、可回滚
