# Dex Query Contract

## 当前状态

本文档描述当前已落地的 Dex 查询入口合同。MCP 的递归查询已从直接暴露
`query: object` 迁移为 `query_file: string`，这是一次不兼容的输入合同变更。

CLI 和 MCP 仍共用 `domain-core` 中的 `FindClassQuery`、`FindMethodQuery`、`FindFieldQuery`
三个 root DTO，并复用 `dexkit-binding` 的完整递归 `Matcher*` 结构。公共合同不包含
`searchInClasses`、`searchInMethods`、`searchInFields`。

## CLI 合同

CLI 继续通过 `--query-json` 或 `--query-file` 传入裸 query JSON，当前行为不在本次 MCP
迁移中改变。CLI 的 `--query-file` 与 MCP 的 `query_file` 暂时是两个文件合同；未来统一
入口格式需要单独设计。

## MCP 工具

MCP 提供 `find_classes`、`find_methods`、`find_fields`。三个工具支持 `offset`、`limit`、
`brief`、`fields`；`limit` 默认 50，最大 200；class 和 method 在使用 `session_id` 时可
投影 handle，field 不提供 handle。

所有 MCP 业务工具还要求两个版本字段：

```json
{"version":"<skill version>","mcp_contract_version":1}
```

调用前必须先调用 `validate_skill_compatibility`，传入 skill 自身的两个版本，并且只在
返回 `compatible=true` 时继续。服务端仍会在每个业务调用中重新校验两个字段。

## `query_file` 参数

查询入口统一为：

```json
{
  "session_id": "target-session-id",
  "query_file": "D:/analysis/.dexclub/queries/find-login.query.json",
  "version": "<skill version>",
  "mcp_contract_version": 1,
  "brief": true
}
```

`query_file` 必须是 MCP server 可见的绝对路径。Windows 参数优先使用 `/`；相对路径不会
按 Agent 当前目录或 MCP 进程目录猜测。

处理顺序固定为：

```text
版本与合同版本校验
  -> query/query_json/query_file 合同检查
  -> 路径与文件边界检查
  -> 文件 JSON 与 envelope 解析
  -> target/session context lease
  -> 查询执行
```

版本、参数合同或文件预检失败时，不会打开 session、解析 workdir、读取目标文件或执行查询
handler。

## 文件格式

MCP 文件使用 `dexclub-query` envelope：

```json
{
  "format": "dexclub-query",
  "formatVersion": 1,
  "kind": "find_methods",
  "query": {
    "searchPackages": ["com.example"],
    "matcher": {"usingStrings": [{"value": "Login", "matchType": "Contains"}]}
  }
}
```

字段约束：

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `format` | string | 固定为 `dexclub-query` |
| `formatVersion` | integer | 当前固定为 `1` |
| `kind` | string | 必须与当前工具名一致 |
| `query` | object | 对应完整的 `Find*Query` DTO |

文件根、envelope 和 `query` 都必须是 JSON object。未知 envelope 字段、错误类型和错误
enum 均拒绝。`kind` 由当前工具固定传给 loader，不能根据文件内容动态路由到其他查询工具。
文件使用 UTF-8，可带 UTF-8 BOM；大小上限为 1 MiB。服务端从一次打开的句柄读取最多
“上限 + 1”字节，再使用同一快照解析。

## 字符串语义

文件只进行一次 JSON 解析。内部 string 永远保持 string，不会因为内容“看起来像 JSON”
而递归解析。例如搜索 Windows 路径使用普通 JSON 转义 `C:\\temp\\foo`；搜索 JSON 文本
也只编码为普通 JSON string。

## 文件位置与生命周期

skill 默认在当前分析目录创建 `<analysis-root>/.dexclub/queries/`。查询文件默认作为分析
证据保留；一次性探测可以使用由 skill 自己创建的临时目录，但不得删除用户维护的文件。
分页期间保持文件内容不变。查询内容变化时创建新文件或显式替换文件，并从 `offset=0`
重新开始。MCP 不修改或删除调用方文件，也不跨请求锁定文件快照。

## 路径安全

loopback MCP（例如 `127.0.0.1` 或 `localhost`）允许读取当前用户权限范围内、满足普通
文件和大小限制的绝对路径。

non-loopback、容器或共享服务必须配置 `DEXCLUB_MCP_QUERY_ROOTS`。服务端对根目录和请求
文件执行 `toRealPath`，并检查最终文件位于 allowlist 内，防止 `..`、符号链接或 Windows
junction 绕过。未配置 allowlist 的 non-loopback 服务必须拒绝 `query_file`。

错误响应和日志不得包含完整 query 内容或目标业务字符串。

## 已删除的 MCP 输入

当前 MCP 不再接受 `query: object` 或 `query_json: string`。只传旧字段，或将旧字段与
`query_file` 混用，均返回 `query_contract_removed`，不能静默忽略旧字段。新版唯一合法
入口就是 `query_file`。

## 错误模型

实现至少区分：`missing_argument`、`invalid_argument`、`version_mismatch`、
`query_contract_removed`、`invalid_query_path`、`query_file_not_found`、
`query_path_not_allowed`、`query_file_too_large`、`invalid_query_encoding`、
`invalid_query_json`、`invalid_query_document`、`query_kind_mismatch` 和 `invalid_query`。

## 迁移结论

这是一次破坏性 MCP 重构，不提供旧参数兼容窗口：

```text
旧版：query: object
新版：query_file: absolute-path string
```

用户必须同时升级 MCP 与配套 skill，并重启 Agent/MCP 会话以重新加载 tool schema。完整
递归查询能力仍由原有 DTO 保留，没有通过限制 matcher 深度换取 schema 兼容性。

本合同已由 MCP schema、query loader、skill 合同测试、HTTP smoke test 和实际 Agent 会话
共同验证。此前的根目录实施方案记录不再作为当前规范来源。
