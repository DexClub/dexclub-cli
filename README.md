# dexclub-cli

`dexclub-cli` 是一个面向 dex / apk 检索、解析与导出的 Kotlin 多模块项目。

它包含三层核心能力：

- `cli`
  - 命令行入口
- `core`
  - 稳定的业务能力边界
- `dexkit`
  - 面向 KMP 的 DexKit 薄封装

当前主要能力：

- 检查 apk / dex 输入
- 按 JSON 条件查找类、方法、字段
- 导出单类 dex、smali、Java
- 以 `workspace` 模式管理 `apk|dex|dexs` 的工程化静态分析状态

## 快速开始

### 环境要求

- JDK 21
- Android SDK
- Android NDK `28.2.13676358`
- `cmake`
- `ninja`

桌面端 DexKit 运行前提请直接参考上游文档：

- <https://luckypray.org/DexKit/zh-cn/guide/run-on-desktop.html>

### 初始化仓库

首次拉取后先初始化 submodule：

```bash
git submodule update --init --recursive
```

如果本地还没有发布 `libcxx`，先执行：

```bash
cd dexkit/vendor/libcxx-prefab
./gradlew :cxx:publishToMavenLocal
```

## CLI 使用

fat jar 入口：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar <command> [args]
```

常用帮助命令：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar --help
java -jar cli/build/libs/dexclub-cli-all.jar help find-method
java -jar cli/build/libs/dexclub-cli-all.jar help workspace
java -jar cli/build/libs/dexclub-cli-all.jar help workspace inspect
java -jar cli/build/libs/dexclub-cli-all.jar --version
```

### inspect

检查单个 apk：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar inspect \
  --input /path/to/app.apk
```

检查多个 dex：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar inspect \
  --input /path/to/classes.dex \
  --input /path/to/classes2.dex
```

输出 JSON：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar inspect \
  --input /path/to/app.apk \
  --output-format json
```

规则：

- 单输入支持 `apk` 或 `dex`
- 多输入仅支持多个 `dex`
- `--output-format` 支持 `text` 与 `json`

### find-class / find-method / find-field

按 JSON 查询类：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar find-class \
  --input /path/to/classes.dex \
  --query-json '{"matcher":{"className":{"value":"SampleSearchTarget","matchType":"Contains","ignoreCase":true}}}' \
  --output-format json \
  --limit 20
```

按字符串查找方法：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar find-method \
  --input /path/to/classes.dex \
  --query-json '{"matcher":{"usingStrings":[{"value":"dexclub-needle-string","matchType":"Equals"}]}}'
```

规则：

- `--query-file` 与 `--query-json` 二选一
- `--output-format` 支持 `text` 与 `json`
- `--limit` 可选

### export-dex / export-smali / export-java

导出单类 dex：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-dex \
  --input /path/to/classes.dex \
  --class fixture.samples.SampleSearchTarget \
  --output /tmp/SampleSearchTarget.dex
```

导出 smali：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-smali \
  --input /path/to/classes.dex \
  --class fixture.samples.SampleSearchTarget \
  --output /tmp/SampleSearchTarget.smali
```

导出 Java：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-java \
  --input /path/to/classes.dex \
  --class fixture.samples.SampleSearchTarget \
  --output /tmp/SampleSearchTarget.java
```

规则：

- 这组导出命令当前仅支持单个 `dex` 输入
- 必须显式传入 `--class` 与 `--output`

### workspace

`workspace` 是显式的工程化静态分析模式，状态固定写入：

```text
<workspace>/.dexclub-cli/
```

支持三类输入：

- `apk`
- `dex`
- `dexs`

其中：

- `apk` workspace 支持 `manifest` / `res`
- `dex` / `dexs` workspace 不支持 `manifest` / `res`，会显式失败
- 顶层无状态命令不会读写 `.dexclub-cli/`

初始化 APK workspace：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace init \
  --workspace /tmp/app-ws \
  --input /path/to/app.apk
```

初始化单 dex workspace：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace init \
  --workspace /tmp/dex-ws \
  --input /path/to/classes.dex
```

初始化 dexs workspace：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace init \
  --workspace /tmp/dexs-ws \
  --input /path/to/classes.dex \
  --input /path/to/classes2.dex \
  --type dexs
```

查看 workspace 身份与状态：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace status \
  --workspace /tmp/app-ws
```

查看当前 workspace 绑定输入的分析摘要：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace inspect \
  --workspace /tmp/app-ws
```

查看 workspace 分析摘要的 JSON：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace inspect \
  --workspace /tmp/app-ws \
  --output-format json
```

查看当前 workspace 能力矩阵：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace capabilities \
  --workspace /tmp/app-ws
```

在 workspace 上查找方法：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace find-method \
  --workspace /tmp/app-ws \
  --query-json '{"matcher":{"usingStrings":[{"value":"dexclub-needle-string","matchType":"Equals"}]}}'
```

在 workspace 上导出 smali：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace export-smali \
  --workspace /tmp/dexs-ws \
  --class fixture.samples.SampleSearchTarget \
  --output /tmp/SampleSearchTarget.smali
```

读取 APK workspace 的 manifest：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace manifest \
  --workspace /tmp/app-ws
```

列出 APK workspace 的资源入口：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar workspace res \
  --workspace /tmp/app-ws
```

当前约束：

- `workspace status` 只展示 workspace 身份与状态摘要
- `workspace inspect` 展示绑定输入的分析摘要
- `workspace export-*` 在 `dexs` 上会先解析唯一 `source dex`；若命中多个 dex，则显式失败
- `workspace cache/*` 与 `workspace runs/*` 当前未进入主 CLI 产品面

## 构建

编译 `dexkit`：

```bash
./gradlew :dexkit:compileKotlinJvm
./gradlew :dexkit:assembleAndroidMain
```

编译 `core` 和 `cli`：

```bash
./gradlew :core:compileKotlinJvm :cli:compileKotlin
```

打包 fat jar：

```bash
./gradlew :cli:fatJar
```

生成带启动脚本的分发目录与 zip：

```bash
./gradlew :cli:installShadowDist :cli:shadowDistZip
./gradlew :cli:installAndroidArm64ShadowDist :cli:androidArm64ShadowDistZip
./gradlew :cli:verifyAndroidArm64ShadowDist
```

## 分发产物

zip 解压后的默认桌面目录结构：

```text
bin/
├── cli
└── cli.bat
lib/
├── dexclub-cli-all.jar
├── libgcc_s_seh-1.dll            # Windows
├── libwinpthread-1.dll           # Windows
├── libstdc++-6.dll               # Windows
└── zlib1.dll                     # Windows
```

`android-arm64` 分发会额外携带显式 native 目录：

```text
bin/
├── cli
└── cli.bat
lib/
├── dexclub-cli-all.jar
└── library/
    └── libdexkit.so              # android-arm64 / bionic
```

说明：

- `bin/cli` 用于 Linux / macOS
- `bin/cli.bat` 用于 Windows
- Windows 分发包会带上桌面 DexKit 运行所需 sidecar，并在启动脚本中把 `%APP_HOME%\lib` 前置到 `PATH`
- `android-arm64` 是给 native Termux / Android `bionic` 运行时使用的 JVM CLI 分发，不是 Android app，也不是 `androidMain` 运行线
- ARM64 Unix 现在按 runtime ABI 区分：
  - `glibc + arm64 -> linux-arm64`
  - `bionic + arm64 -> android-arm64`
  - `musl` / `unknown` -> 停止并报告，不回退
- 使用者仍需自行准备 Java 21 运行环境

## 仓库结构

- `cli/`
  - 命令行入口，依赖 `:core`
- `core/`
  - 自有 facade / model / config / request 边界
- `dexkit/`
  - KMP DexKit 包装层
- `dexkit/vendor/DexKit`
  - vendored 上游 DexKit
- `dexkit/vendor/libcxx-prefab`
  - Android 构建链依赖的本地 `libcxx` prefab
- `skills/`
  - 本地 skill，包括 `dexclub-cli-launcher`

## GitHub Actions

仓库内提供：

- `.github/workflows/build-cli.yml`

用途：

- 按 `OS + 架构` 构建 CLI 分发包
- tag 构建成功后上传 GitHub Release

当前矩阵：

- `linux-x64`
- `linux-arm64`
- `android-arm64`
- `windows-x64`
- `windows-arm64`
- `macos-x64`
- `macos-arm64`

每个平台上传：

- `dexclub-cli-<os>-<arch>.zip`
- `dexclub-cli-<os>-<arch>.sha256`

## Skill

仓库内提供本地 skill：

- `skills/dexclub-cli-launcher/`

详细说明见：

- `skills/dexclub-cli-launcher/README.md`
- `skills/dexclub-cli-launcher/SKILL.md`

## 开发说明

当前设计约束：

- `cli` 只负责用户交互和命令组织
- `core` 提供稳定的公共能力边界
- `dexkit` 负责承接上游 DexKit
- 导出相关能力当前依赖：
  - `dexlib2`
  - `baksmali`
  - `jadx`

JVM 侧 DexKit native 相关配置：

- native 缓存目录：
  - JVM property: `dexclub.dexkit.native.cache.dir`
  - 环境变量: `DEXCLUB_DEXKIT_NATIVE_CACHE_DIR`
- 显式 native 目录：
  - JVM property: `dexclub.dexkit.native.library.dir`
  - 环境变量: `DEXCLUB_DEXKIT_NATIVE_LIBRARY_DIR`
