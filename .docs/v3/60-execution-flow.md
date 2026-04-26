# DexClub CLI V3 执行流

## 目标

V3 的执行流必须围绕：

- `init` 初始化工作区
- 其它命令打开已初始化工作区并执行

展开。

## 总体调用链

建议整体调用链收敛为：

```text
argv
-> CliParser
-> CliRequest
-> CommandDispatcher
-> CommandAdapter
-> core capability service
-> Renderer
-> OutputWriter
```

其中：

- `cli`
  - 负责命令解析、命令适配、渲染与输出
- `core`
  - 负责工作区、运行态与稳定能力接口

## init 总体流程

1. 解析显式 `input`
2. 推导 `workdir`
3. 扫描当前 binding 物料
4. 若未发现任何可识别物料则直接失败
5. 推导 kind / capabilities / snapshot
6. 写入 `.dexclub`
7. 打开工作区并返回 `WorkspaceContext`
8. 渲染初始化结果

其中：

- `input` 必须显式传入
- 不支持 `cli init` 省略参数后默认使用 `cwd`
- 若要初始化当前目录，必须显式传入 `.`

## `status` 总体流程

1. 解析 `[workdir]`
2. 若省略则使用 `cwd`
3. 只读加载工作区状态
4. 渲染结果
5. 输出到 stdout / stderr

约束：

- `status` 不调用 `open()`
- `status` 不刷新 snapshot
- `status` 不回写 `.dexclub`
- `status` 可以返回 `healthy / degraded / broken`

## 其它业务命令总体流程

1. 解析 `[workdir]`
2. 若省略则使用 `cwd`
3. 打开工作区，得到 `WorkspaceContext`
4. 将 `CliRequest` 适配为 `core` request
5. 调用 capability service
6. 渲染结果
7. 输出到 stdout / stderr

## CLI 层职责

CLI 层只负责：

- 命令选择
- 参数解析
- query file 读取
- workdir 省略规则
- help / usage
- 渲染输出
- 错误呈现
- 退出码

CLI 层不负责：

- `.dexclub` 持久化读写
- capability 推导
- inventory 扫描细节
- 直接操作 DexEngine / JADX / resource parser backend

## CommandAdapter 职责

`CommandAdapter` 是 CLI 与 `core` 之间唯一允许同时理解两边语义的薄层。

允许：

1. 接收 `CliRequest`
2. 做 CLI 专属输入归一化
3. 打开工作区
4. 构造 `core` request
5. 调用 capability service
6. 把结果交给 renderer

禁止：

1. 直接操作 `.dexclub` 文件
2. 直接碰 DexEngine / JADX / resource parser backend
3. 直接做 text/json 渲染
4. 直接决定最终终端错误模板
5. 自己装配默认实现
6. 复制 capability / 排序 / 分页等核心规则

## WorkspaceRuntimeResolver 职责

`WorkspaceRuntimeResolver` 负责：

- 打开工作区
- 只读加载工作区状态
- 恢复 active target
- 扫描当前 binding 物料
- 推导 capability / kind / snapshot
- 生成 `WorkspaceContext`
- 生成 `WorkspaceStatus`

关键约束：

- `open()` 返回可执行的 `WorkspaceContext`
- `open()` 允许刷新并回写 `snapshot`
- `loadStatus()` 不依赖 `open()` 的成功
- `loadStatus()` 只读，不修复、不刷新
- `refreshSnapshot()` 是显式运行态刷新入口

## 运行期总伪代码

```kotlin
fun runCli(argv: List<String>): Int {
    val services = createDefaultServices()
    val parser = CliParser()
    val dispatcher = CommandDispatcher(
        workspace = WorkspaceCommandAdapter(services),
        inspect = InspectCommandAdapter(services),
        dexSearch = DexSearchCommandAdapter(services, QueryTextLoader(), WorkdirResolver()),
        export = ExportCommandAdapter(services, WorkdirResolver()),
        resource = ResourceCommandAdapter(services, QueryTextLoader(), WorkdirResolver()),
    )
    val renderer = Renderer()
    val outputWriter = OutputWriter()

    val request = parser.parse(argv)
    val commandResult = dispatcher.dispatch(request)
    val rendered = renderer.render(commandResult)
    outputWriter.write(rendered)
    return commandResult.exitCode
}
```

## 错误分层

建议错误分层如下：

- `CliUsageError`
  - 参数错误、未知命令、缺参
- `WorkspaceInitError`
  - init 输入非法、扫描失败、状态写入失败
- `WorkspaceResolveError`
  - workdir 非法、`.dexclub` 缺失、metadata 损坏
- `CapabilityError`
  - 当前工作区不支持该能力
- `ExecutionError`
  - 底层执行失败

## 退出码

建议首版统一使用：

- `0`
  - 成功
- `1`
  - CLI 用法错误
- `2`
  - 工作区错误、能力错误与执行失败

对 `status` 额外约束：

- `healthy -> 0`
- `degraded -> 2`
- `broken -> 2`

## 执行流约束

1. 只有 `init` 可以创建 `.dexclub`
2. `init` 必须显式传入 `input`
3. 非 `init` 命令不得修改当前 binding
4. `cli` 不直接拼接状态目录路径
5. metadata 读写必须经过 `WorkspaceStore`

