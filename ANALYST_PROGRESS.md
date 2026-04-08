# Analyst 进度文档

## 目的

这份文档用于记录 `skills/dexclub-cli-launcher/analyst` 这一条线的实际落地进度。

它和 [ANALYST_PLANNER_PLAN.md](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/ANALYST_PLANNER_PLAN.md) 的分工不同：

- `ANALYST_PLANNER_PLAN.md`
  - 记录设计目标、边界和版本 1 方案
- `ANALYST_PROGRESS.md`
  - 记录当前真实实现状态、阶段节奏、下一步入口和流转规则

下次会话如果没有上下文，先读这份文档，再决定是否需要回看设计文档或具体代码。

## 当前快照

- 当前最新相关提交
  - `8f41ae6` `Support analyst exact Java summarize`
  - `7cc459f` `Fix export-java in packaged CLI`
  - `dcc2030` `Add structured analyst method summaries`
  - `3af1500` `Add analyst large-method summary compression`
  - `7007464` `Support descriptor-aware analyst anchors`
  - `a51c692` `Add APK summarize resolution to analyst`
- 当前已稳定的 analyst v1 能力
  - `plan` / `run` 入口已经落地
  - `search_methods_by_string`
  - `search_methods_by_number`
  - `trace_callers`
  - `trace_callees`
  - `summarize_method_logic`
  - APK 输入下自动解析目标 dex 后再 summarize
  - overloaded 方法在 relaxed anchor 下返回 `ambiguous`
  - descriptor-aware 的 `trace_*`
  - descriptor-aware 的 `summarize_method_logic`
    - `smali` 已稳定
    - `java` 已在包含 `A-09` 修复的 launcher 构建上完成端到端验证
  - `smali` summarize 的结构化摘要
    - 输出：`structured_summary`
    - 当前包含：`basic_blocks / call_clusters / constant_clusters`
    - 当前包含：`focus_snippets`
  - 大方法 `smali` summarize 的结构化压缩
    - 固定阈值：`line_count >= 120`
    - 压缩输出：`large_method_analysis`
    - 当前按 `method_calls / strings / numbers / field_accesses / branch_hotspots` 分组，并补充行簇聚合
- 当前验证脚本
  - [validate_v1_sample.sh](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/validate_v1_sample.sh)
- 最近一次通过验证的命令
  - `python3 ./skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py --input-dex /tmp/.../classes.dex --class androidx.compose.foundation.ImageKt --method Image --method-descriptor 'Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V' --language java --mode summary --format json`
  - `python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py run --task-type summarize_method_logic --input-json '{"input":["/data/data/com.termux/files/home/AndroidProjects/shadcn/app/build/outputs/apk/debug/app-debug.apk"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image","descriptor":"Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/ImageBitmap;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V"},"language":"java"}'`
- 最近一次通过验证的结果目录
  - `/tmp/a06-targeted-verify.vmj8xb1_/results`

## 当前边界

- `summarize_method_logic` 的 descriptor-aware 精确切片当前支持：
  - `smali`
  - `java`，但要求 launcher 构建已经包含 `A-09` 的 `export-java` 修复
- 仓库当前代码里的 `export-java` 修复已经落地
  - 本地 fat jar 已验证：`com.shadcn.ui.compose.MainActivity` 与 `androidx.compose.foundation.ImageKt` 可成功导出 Java
  - 当前还要区分“包含 `A-09` 修复的 launcher 构建”和“默认已发布 release”
  - 默认已发布 release 是否已对齐，不能由当前本地覆盖验证直接推出
- 当前会话的 launcher 端到端验证使用了临时本地覆盖
  - 已将本地构建的 `cli/build/libs/dexclub-cli-all.jar` 覆盖到 launcher 缓存产物
  - 缓存路径：`/root/.cache/dexclub-cli/releases/v0.0.1/dexclub-cli-linux-arm64/cli-shadow/lib/dexclub-cli-all.jar`
  - 原始缓存已备份为：`dexclub-cli-all.jar.bak-20260408-a09`
  - 这只是当前维护阶段的临时验证手段，不等同于远程 release 已更新
- `structured_summary` 当前只支持 `smali`
  - `java` summarize 不报错，但会返回 `kind=none` 和 `supported=false`
- `focus_snippets` 当前是受控预览
  - 只抽高信号 block / cluster
  - 会做截断，不保证覆盖每个 block
- `large_method_analysis` 当前只对 `smali` 大方法启用；`java` 和非大方法会保留 `is_large_method=false`
- 当前仍然是 analyst 层编排，不是主仓库 `cli / core / dexkit` 的正式产品化能力扩展
- 当前工作区存在无关状态，继续开发时不要混入
  - `dexkit/vendor/DexKit` 子模块改动
  - 未跟踪的 `ANALYST_PLANNER_PLAN.md`

## 状态定义

只允许使用以下状态：

- `待开始`
  - 已确认需要做，但还没开工
- `进行中`
  - 已开始实现或验证，尚未收口
- `阻塞`
  - 已开工，但被外部条件、设计冲突或未决问题卡住
- `已完成`
  - 代码、验证、必要文档都已经收口
- `已取消`
  - 明确决定不做，或改成由新任务替代

## 状态流转约束

状态不能随意跳。后续维护这份文档时，必须遵守下面的约束。

### 允许的流转

- `待开始 -> 进行中`
- `进行中 -> 阻塞`
- `进行中 -> 已完成`
- `进行中 -> 已取消`
- `阻塞 -> 进行中`
- `阻塞 -> 已取消`

### 不允许的流转

- 不允许 `待开始 -> 已完成`
  - 除非该项被判定为纯记录性条目，并在备注中明确写出“未开发，直接关闭”的原因
- 不允许 `已完成 -> 进行中`
  - 如果完成后出现新需求或回归，必须新建一个任务，不要回退旧任务状态
- 不允许 `已取消 -> 进行中`
  - 如果方向恢复，必须新建一个任务
- 不允许同时把多个互相依赖的主任务都标成 `进行中`
  - analyst 这条线默认一次只保留一个主推进项为 `进行中`

### 从 `进行中` 流转到 `已完成` 的必要条件

一个任务只有在以下条件都满足时，才能标成 `已完成`：

- 目标代码已经落地
- 直接影响路径已经做了最小可用验证
- 如果输出 contract 或使用方式发生变化，相关 README 或样例已经同步
- 如果做了提交，文档里要记录对应提交号

### 从 `进行中` 流转到 `阻塞` 的必要条件

- 必须写清楚阻塞原因
- 必须写清楚恢复条件
- 必须写清楚当前停在什么文件或什么判断点

## 任务清单

| ID | 任务 | 状态 | 备注 |
| --- | --- | --- | --- |
| A-01 | analyst v1 planner/runner 基础落地 | 已完成 | 提交 `b85be18` |
| A-02 | v1 样例验证与真实输出样例文档 | 已完成 | 提交 `d14413d` |
| A-03 | APK 输入下自动定位目标 dex 后 summarize | 已完成 | 提交 `a51c692` |
| A-04 | descriptor-aware 的 trace/summarize 最小闭环 | 已完成 | 提交 `7007464` |
| A-05 | 大方法 smali 的二级拆解与上下文压缩 | 已完成 | 本次会话完成。新增 `large_method_analysis`，阈值 `120`，并补了样例验证 |
| A-06 | descriptor-aware + `java` summarize | 进行中 | `A-09` 已在仓库内修复，planner 的 `language=java` 硬拒绝已移除，Java exported-code 已支持 descriptor-aware 精确切片；本次已补基于包含 `A-09` 修复的 launcher 构建的 `export_and_scan.py` / `analyze.py run` 端到端验证，并同步了验证脚本与文档；当前剩余问题仅为默认 published release 对齐口径 |
| A-07 | 更强的 summary 结构化输出 | 已完成 | 本次会话完成。新增 `structured_summary`，包含 `basic_blocks / call_clusters / constant_clusters` |
| A-08 | 基于结构化摘要的局部片段提取 | 已完成 | 本次会话完成。新增 `focus_snippets`，按高信号 block / cluster 回抽原始 smali 片段 |
| A-09 | `export-java` 导出失败定位与修复 | 已完成 | 本次会话完成。根因包括 fat jar 中缺失 Jadx `dex-input` service 声明，以及 Java 导出时在 decompiler 生命周期外读取 `JavaClass.code` |

## 最近一次状态流转

- `A-05`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 目标代码已落地
    - `validate_v1_sample.sh` 已补充大方法压缩断言
    - `README` 与样例文档已同步 `large_method_analysis`
- `A-07`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 目标代码已落地
    - `validate_v1_sample.sh` 已补充 `structured_summary` 断言
    - `README`、workflow 与样例文档已同步新 contract
- `A-08`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 目标代码已落地
    - `validate_v1_sample.sh` 已补充 `focus_snippets` 断言
    - `README`、workflow 与样例文档已同步新 contract
- `A-06`
  - `待开始 -> 进行中`
  - `进行中 -> 阻塞`
  - 阻塞原因
    - 已确认 analyst 层在 [`planner.py`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/planner.py) 中会提前拒绝 `descriptor-aware + language=java`
    - 但进一步实测发现，当前发布版 `export-java` 对样例 APK 中的 `com.shadcn.ui.compose.MainActivity` 与 `androidx.compose.foundation.ImageKt` 都会直接失败，尚未进入 analyst 的 Java 精确切片阶段
  - 恢复条件
    - 先定位并修复 `export-java` 的失败原因，或至少确认一个稳定可复现且可导出的 Java 样例链路
  - 当前停点
    - analyst 观察停在 [`export_and_scan.py`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py) 调用 launcher 后的 subprocess 失败
    - contract 判断停在 [`planner.py`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/planner.py) 对 `language=java` 的硬拒绝
- `A-09`
  - `待开始 -> 进行中`
  - `进行中 -> 已完成`
  - 完成依据
    - 已定位 `export-java` 失败根因
    - 已修复 [`JadxDecompilerService.kt`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/core/src/jvmMain/kotlin/io/github/dexclub/core/export/JadxDecompilerService.kt) 的导出实现
    - 已补 [`jadx.api.plugins.JadxPlugin`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/cli/src/main/resources/META-INF/services/jadx.api.plugins.JadxPlugin) service 声明，保证 fat jar 可加载 `dex-input`
    - 本地 fat jar 已验证 `MainActivity` / `ImageKt` Java 导出成功
- `A-06`
  - `阻塞 -> 进行中`
  - 恢复原因
    - `A-09` 已在仓库内完成，Java 导出链路不再是当前代码的前置失败点
    - [`planner.py`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/planner.py) 已移除 `descriptor-aware + language=java` 的硬拒绝
    - [`code_analysis.py`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/code_analysis.py) 已支持 Java exported-code 的 descriptor-aware 精确切片
  - 当前停点
    - 基于包含 `A-09` 修复的 launcher 构建的 `export_and_scan.py` / `analyze.py run` 端到端验证已补完
    - 当前只剩默认 published release 的对齐判断与最终口径维护

## 下一步推荐入口

下一个对话优先继续 `A-06`，确认默认 published release 是否已经对齐 `A-09` 修复，并收口 Java exact summarize 的发布态口径。

原因：

- 当前 `smali` summarize 路径已经有：
  - `structured_summary`
  - `large_method_analysis`
  - `focus_snippets`
- `A-09` 的仓库内修复已经完成
- `A-06` 的 planner、Java exported-code 精确切片、以及 fixed-launcher-build 端到端验证都已经完成
- 当前剩余问题已经收缩到“如何对齐默认 launcher 发布态与 analyst 文档/验证口径”

## A-06 历史阻塞记录

这部分保留当时把 `A-06` 从 `进行中` 打回 `阻塞` 的依据，供后续回看。

当前确认到的事实：

1. `descriptor-aware + language=java` 当前确实先被 analyst 层拦住
   - 重点看 [`planner.py`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/planner.py)
2. 但把这层前置拒绝拿掉，并不能自然得到稳定能力
   - 实测通过 [`export_and_scan.py`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py) 调发布版 `export-java`
   - `com.shadcn.ui.compose.MainActivity`
   - `androidx.compose.foundation.ImageKt`
   - 都在 CLI 层直接失败，没有进入 analyst 的 Java 方法精确切片
3. 所以 `A-06` 目前不应继续按“先做 Java overload 精确定位”推进
   - 更合理的顺序是先修 `A-09`
   - `A-09` 收口后，再回到 analyst 层决定 exact-anchor 的 Java contract

本次用于确认阻塞的命令包括：

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py plan \
  --task-type summarize_method_logic \
  --input-json '{"input":["/path/to/classes.dex"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image","descriptor":"Landroidx/compose/foundation/ImageKt;->Image(... )V"},"language":"java"}'
```

```bash
python3 ./skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py \
  --input-dex /path/to/classes.dex \
  --class androidx.compose.foundation.ImageKt \
  --language java \
  --mode summary \
  --format json
```

## A-09 完成记录

本次完成内容：

1. 定位了 `export-java` 在 fat jar / release 形态下的真实失败根因
   - Jadx `dex-input` 插件类已被打入 jar
   - 但 `META-INF/services/jadx.api.plugins.JadxPlugin` 没有包含 `DexInputPlugin`
   - 导致运行版 CLI 只加载 `kotlin-metadata`，实际解析 dex 时 `Loaded classes: 0`
2. 修复了 Java 导出实现中的生命周期错误
   - 之前在 `JadxDecompiler.use {}` 作用域外访问 `JavaClass.code`
   - 会触发 `codeCache` 为空的空指针
3. 补了运行版 service 声明
   - 新增 [`jadx.api.plugins.JadxPlugin`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/cli/src/main/resources/META-INF/services/jadx.api.plugins.JadxPlugin)
   - 明确声明 `jadx.plugins.input.dex.DexInputPlugin`
   - 明确声明 `jadx.plugins.kotlin.metadata.KotlinMetadataPlugin`
4. 给 Java 反编译保留了保守回退
   - 当 `useDxInput=true` 且加载结果为空时，会再试一次 `useDxInput=false`

本次直接验证：

1. `./gradlew --no-daemon -Dkotlin.incremental=false :cli:fatJar`
2. `./gradlew --no-daemon -Dkotlin.incremental=false :core:jvmTest --tests 'io.github.dexclub.core.DexEngineJvmTest.export should write dex smali and java outputs'`
3. 本地 fat jar 手工验证
   - `com.shadcn.ui.compose.MainActivity`
   - `androidx.compose.foundation.ImageKt`
   - 都已成功导出 `.java`

## A-06 恢复记录

本次恢复内容：

1. 已移除 planner 对 `descriptor-aware + language=java` 的硬拒绝
2. 已给 [`code_analysis.py`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/code_analysis.py) 增加 Java exported-code 的 descriptor-aware 精确切片
   - 当前按 descriptor 的参数/返回类型匹配 Java 方法声明
   - 已验证可以在 `ImageKt.java` 中准确切到 `ImageVector` 这一组重载，而不是只命中第一个 `Image(...)`

本次直接验证：

1. planner 验证
   - `python3 ./skills/dexclub-cli-launcher/analyst/scripts/analyze.py plan --task-type summarize_method_logic --input-json '{"input":["/tmp/.../classes.dex"],"method_anchor":{"class_name":"androidx.compose.foundation.ImageKt","method_name":"Image","descriptor":"Landroidx/compose/foundation/ImageKt;->Image(Landroidx/compose/ui/graphics/vector/ImageVector;Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/ui/Alignment;Landroidx/compose/ui/layout/ContentScale;FLandroidx/compose/ui/graphics/ColorFilter;Landroidx/compose/runtime/Composer;II)V"},"language":"java"}'`
   - 结果：已返回 `export_and_scan` 计划，不再 `unsupported`
2. Java 精确切片验证
   - 基于本地 fat jar 导出的 `ImageKt.java`
   - `descriptor=...ImageVector...`
   - 结果：`startLine=250`
   - 并命中 `VectorPainterKt.rememberVectorPainter`
   - 说明当前已切到目标重载

本次继续补的端到端验证：

1. 直接 `export_and_scan.py` 验证
   - 输入：`androidx.compose.foundation.ImageKt`
   - descriptor：`...ImageBitmap...`
   - language：`java`
   - 结果：基于当前本地覆盖后的 launcher 构建，已返回 `kind=java`
   - 并保留：
     - `scope.methodDescriptor`
     - `methodCallCount`
     - `branchLineCount`
     - `exportPath`
2. `analyze.py run` 验证
   - 输入：样例 APK
   - descriptor：同上
   - language：`java`
   - 结果：已返回 `status=ok`
   - planner 已走：
     - `resolve_apk_dex`
     - `export_and_scan`
   - 最终 `step_results[1].result.kind=java`
   - 最终 `scope.method_descriptor` 与输入 descriptor 一致
3. 验证脚本与文档同步
   - [`validate_v1_sample.sh`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/validate_v1_sample.sh) 已把旧的 `unsupported_exact_java_summarize` 断言改为真实通过断言
   - [`README.md`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/README.md) 已补“fixed launcher build 与 published release 分开表述”的口径
   - [`analyze-v1-examples.md`](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/references/analyze-v1-examples.md) 已补 Java exact summarize 的 `analyze.py run` 样例
   - 本次尝试整跑 `validate_v1_sample.sh` 时，进程在旧的 APK summarize 用例上被系统杀掉，因此当前只把 A-06 直接影响的 Java exact 路径做了定向通过验证

当前尚未收口的点：

1. analyst launcher 默认仍指向已发布 release
2. 当前 Java exact summarize 的“通过”结论，仍然只适用于：
   - 仓库内代码
   - 或包含 `A-09` 修复的 launcher 构建
3. 默认 published release 是否已具备同样能力，仍需单独确认
4. 因此 `A-06` 继续保持 `进行中`，直到默认发布态对齐口径彻底明确

## A-05 完成记录

本次完成内容：

1. 定义了“大方法”阈值
   - 当前使用固定规则：`smali` 且 `line_count >= 120`
2. 给 summarize 结果增加 `large_method_analysis`
   - 按 `method_calls / strings / numbers / field_accesses / branch_hotspots` 分组
   - 每组补 `top_items`
   - 每组补基于行号邻近度的 `clusters`
3. 保留原始导出代码产物
   - 仍然通过 `export_path` 指向完整文件
   - 没有移除原有统计字段

本次刻意没做：

1. CFG
2. 完整语义块推断
3. 更细粒度的局部代码片段抽取

## 下次会话快速开始

下次会话如果没有任何历史上下文，按下面顺序开始：

1. 先读本文件
   - [ANALYST_PROGRESS.md](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/ANALYST_PROGRESS.md)
2. 再读设计文档的相关边界
   - [ANALYST_PLANNER_PLAN.md](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/ANALYST_PLANNER_PLAN.md)
3. 确认工作区无关状态，不要误处理
   - `git status --short`
4. 确认最近相关提交
   - `git log --oneline -n 6`
5. 继续进入 `A-06`
   - 重点看：
     - [ANALYST_PROGRESS.md](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/ANALYST_PROGRESS.md)
     - [code_analysis.py](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/code_analysis.py)
     - [export_and_scan.py](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/export_and_scan.py)
     - [planner.py](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/planner.py)
     - [runner.py](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/runner.py)
     - [JadxDecompilerService.kt](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/core/src/jvmMain/kotlin/io/github/dexclub/core/export/JadxDecompilerService.kt)
     - [validate_v1_sample.sh](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/scripts/validate_v1_sample.sh)
     - [README.md](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/README.md)
     - [analyze-v1-examples.md](/data/data/com.termux/files/home/AndroidProjects/dexclub-cli/skills/dexclub-cli-launcher/analyst/references/analyze-v1-examples.md)
   - 先确认默认 published release 是否已经包含 `A-09` 修复
   - 如果没有，继续维持“fixed launcher build”和“published release”两套口径

## 下次会话可直接使用的提示词

如果下次会话要最快恢复，可以直接用这句：

```text
先阅读仓库根目录的 ANALYST_PROGRESS.md，按其中“下次会话快速开始”执行，继续推进 A-06，优先确认默认 launcher 发布态是否已经对齐 A-09 修复，并区分 fixed launcher build 与 published release 的 Java exact summarize 口径。
```

## 文档维护规则

后续每次继续这条线时，优先更新这份文档，而不是把状态散落在多个地方。

至少同步以下内容：

- 当前主推进任务是哪一个
- 哪个任务从什么状态流转到了什么状态
- 新增提交号
- 新增验证命令或验证结果
- 如果下一步优先级变化，要同步更新“下一步推荐入口”
