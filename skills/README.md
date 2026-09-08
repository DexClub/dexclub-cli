# Skills

`skills/` 目录保存本仓库维护的 Codex skill 源码。

## 当前包含

- `dexclub-analysis`
  通过 `mcp__dexclub__` 分析 APK、Dex、manifest、resources、classes 与 methods，适合黑盒 Android 逆向、功能定位、实现链路追踪与竞品分析。

## 运行前提

当前 skill 依赖：

- Codex skill 机制
- skill 执行时当前环境已提供 `mcp__dexclub__`

如果 dexclub MCP 不可用，skill 应直接停止，并提示用户先连接或启动 dexclub MCP server，而不是静默回退到 shell 或 CLI。

## 同步到运行时

仓库中的 `skills/` 是带版本占位符的模板源码，不应直接安装。先由 Gradle 生成绑定当前构建
版本的副本：

```powershell
.\gradlew.bat :mcp-app:generateVersionedDexClubSkill
```

如果要在本机实际触发 skill，再把生成目录同步到：

```text
$CODEX_HOME/skills/
```

Windows 上常见位置类似：

```text
C:\Users\<user>\.codex\skills\
```

例如：

```powershell
Copy-Item -Recurse -Force .\mcp-app\build\generated\skills\dexclub-analysis C:\Users\<user>\.codex\skills\
```

如果运行时副本和仓库内源码不一致，真实行为应以 `$CODEX_HOME/skills` 中的副本为准。

本仓库中的 `skills/` 模板源码是权威维护来源；发行包和本地运行时必须使用 Gradle 生成的
版本化副本。在未同步前，当前机器上的实际运行行为仍以 `$CODEX_HOME/skills` 中的副本为准。

## 最小验证

确认下面三件事：

1. `dexclub` MCP 已连接
2. 当前会话能看到 `mcp__dexclub__`
3. `dexclub-analysis` 已从生成目录同步到 `$CODEX_HOME/skills`
4. 第一个 DexClub 调用是 `validate_skill_compatibility`，并使用 skill metadata 返回 `compatible=true`

然后可在一个无上下文新会话里显式要求：

```text
请使用 $dexclub-analysis 分析某个 APK 功能入口。
```

如果 skill 与 MCP 都生效，Codex 应优先：

- 使用 `mcp__dexclub__`
- 第一个 DexClub 调用必须是 `validate_skill_compatibility`；`get_server_info` 只用于预检成功后的诊断
- 每个后续业务 MCP 调用都传入 skill metadata 中的 `version` 和 `mcp_contract_version`
- 先 `open_target_session`
- `open_target_session.input` 使用绝对路径，不要传相对路径
- 首轮优先 `brief=true`，只有确有必要再显式 `fields`
- 根据字符串、类、方法、字段、manifest 或 resource 线索选择对应入口
- `find_classes`、`find_methods`、`find_fields` 使用 `.dexclub/queries/*.query.json` 中的 `query_file`
- 不使用已删除的 using-strings 工具、旧简化参数或 `searchIn*`
- 先 `inspect_method` 后 `export_*`

## 相关文件

- [dexclub-analysis/SKILL.md](dexclub-analysis/SKILL.md)
- [dexclub-analysis/agents/openai.yaml](dexclub-analysis/agents/openai.yaml)
- [dexclub-analysis/references/find-queries.md](dexclub-analysis/references/find-queries.md)
- [dexclub-analysis/references/find-query-fields.md](dexclub-analysis/references/find-query-fields.md)
