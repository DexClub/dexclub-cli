# Analyst 存储与缓存落地计划

## 目标

为 `skills/dexclub-cli-launcher/analyst` 落地一套可复用、可清理、可追踪的本地存储结构。

这套结构需要同时满足：

- release 下载缓存不污染仓库工作区
- analyst 分析产物落在工作目录，便于观察和清理
- 跨会话可复用同一份 APK / dex 的中间产物
- 同一会话下分析多个 APK / dex 时不会互相覆盖
- 后续扩展清理命令、回归排查和索引能力时不需要推翻重做
- `cli` 保持独立，skill 直接控制 `cli`，不反向把 skill 内部状态下沉到 `cli / core / dexkit`

## 存储分层

## 边界约束

这份计划只约束 `skills/dexclub-cli-launcher/analyst` 这一层的本地存储。

明确不做：

- 在 `cli/` 中引入 skill 专属缓存目录概念
- 在 `cli/` 中引入 skill 专属产物目录概念
- 在 `cli / core / dexkit` 中新增“管理 analyst 工作目录”的正式产品能力

这条线保持：

- `cli` 独立提供通用能力
- `skill` 直接调用和编排 `cli`
- skill 自己维护其工作目录、缓存和运行产物

### 1. release 缓存

继续保留用户级缓存目录：

```text
~/.cache/dexclub-cli/releases
```

这一层仍由 launcher 负责，当前不迁入工作目录。

### 2. analyst 工作目录产物

新增工作目录根：

```text
build/dexclub-cli/
```

工作目录根的解析规则：

- 默认解析到当前仓库根目录
- 不按进程 `cwd` 漂移
- 不按输入 APK / dex 所在目录漂移

优先级规则：

1. 如果调用方显式传入 `artifact_root`，它决定整次 run 的根目录
2. 如果单个脚本显式传入 `--output-dir`，它只覆盖该脚本自己的导出目录
3. 没有显式路径时，run 根目录默认使用仓库根下的 `build/dexclub-cli/`

原因：

- 当前 analyst 相关脚本主要以仓库内工具形态维护
- 仓库根下统一落盘，便于观察和清理
- 当前仓库已经默认忽略 `build/`，比单独维护 `.dexclub-cli/` 更不容易遗漏忽略规则
- 避免在仓库子目录执行时生成多个分散的工作目录
- `build/dexclub-cli/` 被 `./gradlew clean` 或手动清理 `build/` 一并删除，视为可接受行为，不承诺长期保留

第一阶段目录结构：

```text
build/dexclub-cli/
  cache/
    v1/
      inputs/
        apk/
          <input-hash>/
            input-meta.json
            extracted-dex/
        dex/
          <input-hash>/
            input-meta.json
  runs/
    v1/
      <run-id>/
        run-meta.json
        exports/
        results/
        resolved/
```

## 主键规则

### 输入缓存主键

- APK 缓存按 APK 文件内容 hash
- 单 dex 缓存按 dex 文件内容 hash
- 后续如果支持 dex set，再单独定义组合 hash 规则

输入缓存主键不使用：

- 文件名
- 原始路径
- 会话名

原因：

- 文件名不稳定
- 路径可能变化
- 会话不是可复用资产的正确主键

### 运行主键

- 每次 `analyze.py run` 继续使用现有 `run_id`
- `runs/v1/<run-id>/` 只表示一次执行实例
- 它通过 `run-meta.json` 指向本次命中的输入缓存

## 缓存边界

第一阶段只缓存“高复用中间产物”，不缓存最终分析结论。

缓存命中除了输入内容，还必须受存储版本约束。

第一阶段先固定使用：

- `build/dexclub-cli/cache/v1/`
- `build/dexclub-cli/runs/v1/`

只要出现以下任一变化，就允许直接切新版本目录，而不是兼容复用旧缓存：

- 缓存目录结构变化
- 缓存元数据结构变化
- 缓存命中规则变化
- 复用的中间产物种类变化

第一阶段不要求对旧版本目录做自动迁移。

### 缓存内容

- APK 解包后的 `classes*.dex`
- 后续如有必要，再增加 class 级导出结果复用

### 不直接缓存的内容

- 最终 summarize JSON 结果
- trace / search 的最终结果
- method 级别的最终结论

原因：

- 这些结果直接受任务参数、contract 和输出 schema 影响
- 先缓存中间产物更稳，失效规则更简单

## 元数据要求

### `input-meta.json`

至少记录：

- `input_kind`
- `original_path`
- `file_name`
- `size`
- `mtime`
- `sha256`
- `created_at`

### `run-meta.json`

至少记录：

- `run_id`
- `task_type`
- `input_paths`
- `input_cache_keys`
- `method_anchor`
- `language`
- `release_tag`
- `created_at`

## 并发与落盘规则

第一阶段必须遵守：

- 缓存目录写入采用临时目录 + 原子重命名
- 只有完整成功后才生成最终缓存目录
- 半成品目录不能作为缓存命中结果复用

如果同一输入被并发构建：

- 允许后写者发现缓存已存在后直接复用
- 不允许两个进程同时把半成品写进最终目录

## 清理策略

第一阶段先约定目录边界，不强制同时实现完整清理命令。

最小可用清理目标：

- 用户可手动删除 `build/dexclub-cli/runs/`
- 用户可手动删除 `build/dexclub-cli/cache/`
- 文档中需要明确这两类目录的职责区别
- 当前仓库根 `.gitignore` 已覆盖 `build/`

后续可单独增加：

- `clean-runs`
- `clean-cache`
- 按时间或大小清理

## 第一阶段实现范围

必须做：

- 把 analyst run 产物默认目录从 `/tmp/dexclub-analyst-runs` 切到工作目录下 `build/dexclub-cli/runs/v1`
- 为 APK / dex 输入增加工作目录下的输入缓存目录
- 为缓存目录补 `input-meta.json`
- 为 run 目录补 `run-meta.json`
- 保持现有分析 contract 不变

暂时不做：

- 最终分析结果缓存
- dex set 组合缓存
- APK 维度索引文件
- 会话维度索引文件
- 自动清理策略
- UI / CLI 级专用清理子命令

## 验证要求

第一阶段完成后，至少验证：

1. 同一 APK 连续执行两次时，第二次可复用输入缓存
2. 同一工作目录下分析不同 APK 时，缓存不会互相覆盖
3. 删除 `build/dexclub-cli/runs/` 不影响输入缓存复用
4. 删除 `build/dexclub-cli/cache/` 后可重新构建
5. 现有 `validate_v1_sample.sh` 与 Java exact summarize 发布态验证不回归
