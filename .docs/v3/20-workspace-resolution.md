# DexClub CLI V3 工作区解析

## 目标

V3 的工作区解析只解决一件事：

`把一个 workdir 解析成可执行的 WorkspaceContext`

工作区解析不负责 CLI 参数解析，也不负责渲染输出。

## 两类入口

### init 入口

`init` 接收一个显式 `input`：

- 文件

并据此创建工作区。

### 运行期入口

除 `init` 外，其余命令接收：

- 显式 `workdir`
- 或隐式 `cwd`

并据此打开已存在的工作区。

## init 解析规则

### 输入约束

`init` 的 `input` 只能是一个文件。

不允许：

- 多输入
- 重复输入
- 空输入
- 省略输入并默认回退到 `cwd`

### workdir 推导

规则如下：

- `workdir = input.parent`

### 状态根

状态根固定为：

```text
<workdir>/.dexclub/
```

### 输入校验

`init` 首版只接受单个文件输入。

规则：

- `input` 必须存在
- `input` 必须是常规文件
- `workdir = input.parent`

### 目录输入处理

若 `input` 是目录，则 `init` 必须直接失败。

推荐错误语义：

- `Initialization failed: directory input is not supported: <path>`
- `Please provide a target file such as an apk, dex, AndroidManifest.xml, resources.arsc, or a binary xml file`

## 运行期 workdir 解析规则

### workdir 显式传入

若命令带了 `[workdir]`，则：

- 该目录必须存在
- 该目录下必须直接存在 `.dexclub`

否则直接失败。

### workdir 省略

若命令省略 `[workdir]`，则：

- 隐式使用当前进程的 `cwd`
- `cwd` 必须直接存在 `.dexclub`

否则直接失败。

### 明确不做

运行期解析明确不做以下行为：

- 不向上查找祖先目录
- 不自动初始化 `.dexclub`
- 不自动重绑定到别的输入

## 打开工作区的流程

建议流程如下：

1. 确定 `workdir`
2. 校验 `<workdir>/.dexclub` 存在
3. 读取 `.dexclub/workspace.json`
4. 读取 `activeTargetId`
5. 读取 `targets/<activeTargetId>/target.json`
6. 根据 `activeTargetId + target.json.inputPath` 还原当前 binding
7. 根据当前 binding 重新扫描 inventory
8. 重新推导 kind / capabilities / snapshot
9. 必要时回写 `snapshot.json`
10. 返回 `WorkspaceContext`

这个流程只适用于：

- `inspect`
- `gc`
- dex / resource 业务命令

不适用于 `status`。

`status` 应走只读状态解析路径：

1. 确定 `workdir`
2. 校验 `<workdir>/.dexclub` 存在
3. 读取 `.dexclub/workspace.json`
4. 读取 `activeTargetId`
5. 尝试读取 `targets/<activeTargetId>/target.json`
6. 尝试读取 `targets/<activeTargetId>/snapshot.json`
7. 尝试校验 binding 指向的输入是否仍存在
8. 生成 `WorkspaceStatus`

约束：

- `status` 不要求工作区可成功 `open()`
- `status` 不刷新 snapshot
- `status` 不修复 metadata
- `status` 不回写 `.dexclub`

## 当前 binding 的意义

当前 binding 的来源应明确为：

- `.dexclub/workspace.json` 中的 `activeTargetId`
- `.dexclub/targets/<target-id>/target.json` 中的 `inputPath`

V3 首版要求一个工作区只有一个当前绑定目标。

因此打开工作区后，不需要让用户额外选择 target。

## 失败条件

以下情况必须显式失败：

- `workdir` 不存在
- `.dexclub` 不存在
- `workspace.json` 不存在
- metadata 损坏
- `activeTargetId` 不存在
- `target.json` 缺失
- binding 指向的输入不存在

## 返回契约

工作区解析后的结果必须满足：

- 有唯一 `workdir`
- 有唯一 `dexclubDir`
- 有唯一 `workspaceId`
- 有唯一 `activeTargetId`
- 有唯一 `activeTarget`
- 有当前有效的 `snapshot`

命令执行层不应再自行回头解析 `.dexclub` 文件树。

