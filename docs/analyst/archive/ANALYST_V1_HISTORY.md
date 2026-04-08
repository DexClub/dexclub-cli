# Analyst V1 历史记录

## 用途

这份文档保留 `V1` 落地过程中的阶段性历史记录。

它不作为当前入口文档。当前状态优先看：

- [ANALYST_PROGRESS.md](../ANALYST_PROGRESS.md)
- [ANALYST_ROADMAP.md](../ANALYST_ROADMAP.md)

## A-06 历史阻塞记录

`A-06` 曾因 `descriptor-aware + language=java` 路径尚未打通而阻塞。

当时的关键判断是：

- planner 会提前拒绝 `descriptor-aware + language=java`
- 即使绕开 planner，发布态 `export-java` 也会直接失败
- 因此不应继续在 analyst 层强推 Java exact summarize，而应先修 `A-09`

## A-09 完成记录

`A-09` 的核心完成点包括：

- 定位 fat jar / release 形态下 `export-java` 的真实失败根因
- 修复 Java 导出实现中的 decompiler 生命周期错误
- 补齐 `jadx.api.plugins.JadxPlugin` service 声明
- 保留 Java 反编译的保守回退路径

当时的直接验证包括：

- `:cli:fatJar`
- `:core:jvmTest --tests 'io.github.dexclub.core.DexEngineJvmTest.export should write dex smali and java outputs'`
- 本地 fat jar 手工验证 `MainActivity` / `ImageKt` Java 导出成功

## A-06 恢复与完成记录

`A-09` 修完后，`A-06` 才重新恢复推进。

恢复后的关键完成点包括：

- 移除 planner 对 `descriptor-aware + language=java` 的硬拒绝
- 给 `code_analysis.py` 增加 Java exported-code 的 descriptor-aware 精确切片
- 补齐 `export_and_scan.py` 与 `analyze.py run` 的 Java exact summarize 定向验证
- 最终基于 published release `v0.0.1` 完成空缓存下载与 Java exact summarize 两级验证

## A-05 完成记录

`A-05` 的关键完成点包括：

- 定义大方法阈值：`smali` 且 `line_count >= 120`
- 给 summarize 结果增加 `large_method_analysis`
- 保留原始导出代码产物，不移除既有统计字段

当时刻意没做：

- CFG
- 完整语义块推断
- 更细粒度的局部代码片段抽取
