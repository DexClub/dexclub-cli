# dexclub-cli

`dexclub-cli` 是一个用于 dex/apk 检索、解析与导出的 Kotlin 多模块工程，包含 CLI、核心能力层，以及一层面向 KMP 的 DexKit 薄封装。导出相关能力使用 `dexlib2` / `baksmali` 和 `jadx`。

## 模块

- `cli/`
  - 命令行入口，依赖 `:core`
- `core/`
  - 面向业务的能力层，当前主要提供 JVM 实现
- `dexkit/`
  - KMP DexKit 包装层，保留 `io.github.dexclub.dexkit` 这一层 API
- `dexkit/vendor/DexKit`
  - vendored DexKit 上游仓库，通过 composite build 接入
- `dexkit/vendor/libcxx-prefab`
  - 本地维护的 `libcxx` prefab 仓库，用于 Android 构建链

## 当前结构说明

- `core` 必须继续保留 KMP 结构，稳定的 facade / model / config / request 边界位于 `commonMain`，JVM 实现细节位于 `jvmMain`。
- `core` 当前对外以自有 API 为主，公共边界围绕 `DexEngine` 及其自有 model/request 组织。
- `dexkit` 负责提供 `io.github.dexclub.dexkit` 这一层 KMP API。
- Android 侧通过上游 `DexKit` 的 `dexkit-android` 产物提供底层实现。
- JVM 侧通过 included build 使用本地 `vendor/DexKit`，并在 `jvmProcessResources` 时拷贝 native 库。
- 根工程启用了 `mavenLocal()`，用于解析当前 Android 构建所需的 `dev.rikka.ndk.thirdparty:libcxx:1.3.0`。

## 技术工具链

- `DexKit`
  - 用于类、方法、字段检索，以及 `find-class`、`find-method`、`find-field`
  - 仓库内通过 `dexkit/` 这一层 KMP API 封装上游能力
- `dexlib2` / `baksmali`
  - 用于 JVM 侧 dex 读取、单类 dex 导出与 smali 生成
  - `inspect`、`export-dex`、`export-smali` 使用这组工具链
- `jadx`
  - 用于将导出的单类 dex 反编译为 Java 源码
  - `export-java` 先导出临时单类 dex，再调用 `jadx` 生成 `.java` 文件

## Core 公共 API

当前 `core` 的稳定入口是 `io.github.dexclub.core.DexEngine`。

主要公开能力：

- `inspect()`
  - 返回 `DexArchiveInfo`
- `searchClassHitsByName()`
  - 返回 `List<DexClassHit>`
- `searchMethodHitsByString()`
  - 返回 `List<DexMethodHit>`
- `exportDex()`
  - 接收 `DexExportRequest`，返回 `DexExportResult`
- `exportSmali()`
  - 接收 `SmaliExportRequest`，返回 `DexExportResult`
- `exportJava()`
  - 接收 `JavaExportRequest`，返回 `DexExportResult`

相关公共模型位于：

- `core/src/commonMain/kotlin/io/github/dexclub/core/model/`
- `core/src/commonMain/kotlin/io/github/dexclub/core/config/`
- `core/src/commonMain/kotlin/io/github/dexclub/core/request/`

说明：

- `cli` 通过这组自有 API 访问 `core`
- `core` 公共边界保持在自有 model/request 与 `DexEngine` 这一层
- 第三方适配与平台实现细节继续留在 `jvmMain`

## CLI 命令结构

CLI 入口支持以下帮助与版本命令：

- `dexclub-cli --help`
- `dexclub-cli help <命令>`
- `dexclub-cli <命令> --help`
- `dexclub-cli --version`

当前命令按职责分为三组：

- 检查命令
  - `inspect`
  - 用于检查 `apk` 或 `dex` 输入，并输出基本统计信息
- 搜索命令
  - `search-class`
  - `search-string`
  - 用于按类名关键词或字符串常量搜索目标
- 高级查询命令
  - `find-class`
  - `find-method`
  - `find-field`
  - 用于按 JSON 查询条件查找类、方法或字段
- 导出命令
  - `export-dex`
  - `export-smali`
  - `export-java`
  - 用于按类名导出单类 dex、smali 或 Java 源码

输入与参数规则：

- `inspect`、`search-class`、`search-string`
  - 支持重复传入 `--input`
  - 单输入支持 `apk` 或 `dex`
  - 多输入仅支持多个 `dex` 文件，不支持混合传入 `apk`
- `search-class`、`search-string`
  - 必须传入 `--keyword`
  - `--limit` 可选，默认值为 `100`
- `find-class`、`find-method`、`find-field`
  - 必须二选一传入 `--query-file` 或 `--query-json`
  - `--output-format` 可选，支持 `text` 与 `json`，默认值为 `json`
  - `--output-file` 可选；未指定时输出到终端
  - `--limit` 可选；未指定时输出全部结果
- `export-dex`、`export-smali`、`export-java`
  - 仅支持单个 `dex` 输入
  - 必须显式传入 `--class` 与 `--output`
- `export-smali`
  - 额外支持 `--auto-unicode-decode true|false`
  - 默认值为 `true`

## CLI 使用案例

以下示例默认使用 fat jar 入口：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar <命令> [参数]
```

查看帮助：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar --help
java -jar cli/build/libs/dexclub-cli-all.jar help search-string
```

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

按类名搜索：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar search-class \
  --input /path/to/classes.dex \
  --keyword SampleSearchTarget \
  --limit 20
```

按字符串常量搜索：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar search-string \
  --input /path/to/classes.dex \
  --keyword dexclub-needle-string
```

按 JSON 文件查找类：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar find-class \
  --input /path/to/classes.dex \
  --query-file /path/to/find-class.json \
  --output-format json
```

按 JSON 字符串查找方法并写入文件：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar find-method \
  --input /path/to/classes.dex \
  --query-json '{"matcher":{"nameMatcher":{"value":"exposeNeedle","matchType":"Equals"},"declaredClassMatcher":{"classNameMatcher":{"value":"fixture.samples.SampleSearchTarget","matchType":"Equals"}}}}' \
  --output-file /tmp/find-method.json
```

导出目标类为单类 dex：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-dex \
  --input /path/to/classes.dex \
  --class fixture.samples.SampleSearchTarget \
  --output /tmp/SampleSearchTarget.dex
```

导出目标类为 smali：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-smali \
  --input /path/to/classes.dex \
  --class fixture.samples.SampleSearchTarget \
  --output /tmp/SampleSearchTarget.smali
```

导出目标类为 Java 源码：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-java \
  --input /path/to/classes.dex \
  --class fixture.samples.SampleSearchTarget \
  --output /tmp/SampleSearchTarget.java
```

## 环境

- JDK 21
- Android SDK
- Android NDK 28.2.13676358
- `cmake`
- `ninja`

## 初始化

首次拉取后，先初始化 submodule：

```bash
git submodule update --init --recursive
```

如果本地仓库里还没有 `libcxx`，先发布一次：

```bash
cd dexkit/vendor/libcxx-prefab
./gradlew :cxx:publishToMavenLocal
```

## 常用构建命令

编译 `dexkit`：

```bash
./gradlew :dexkit:compileKotlinJvm
./gradlew :dexkit:assembleAndroidMain
```

编译 `core` 和 `cli`：

```bash
./gradlew :core:compileKotlinJvm :cli:compileKotlin
```

打包 CLI fat jar：

```bash
./gradlew :cli:fatJar
```

打包带启动脚本的 CLI 分发目录与 zip：

```bash
./gradlew :cli:shadowDistZip :cli:installShadowDist
```

## GitHub Actions

仓库内提供了 `.github/workflows/build-cli.yml` workflow，用于按 `OS + 架构` 构建 CLI 分发包。

当前矩阵包含：

- `linux-x64`
- `linux-arm64`
- `windows-x64`
- `windows-arm64`
- `macos-x64`
- `macos-arm64`

workflow 构建的每个 artifact 都会包含：

- `dexclub-cli-<os>-<arch>.zip`
- `dexclub-cli-<os>-<arch>.sha256`

zip 内部目录结构为：

```text
cli-shadow/
├── bin/
│   ├── cli
│   └── cli.bat
└── lib/
    └── dexclub-cli-all.jar
```

说明：

- `bin/cli` 适用于 Linux / macOS 等 POSIX 环境
- `bin/cli.bat` 适用于 Windows
- 使用者仍需自行准备 Java 21 运行环境
- 当前桌面 native 库由宿主机构建，不能在单一 runner 上交叉产出所有架构；因此 workflow 使用多 job matrix 分别构建各目标平台
- 如果当前仓库所在账号或计划无法调度某些 ARM GitHub-hosted runner，需要把对应 job 的 `runs-on` 改成可用的 self-hosted runner 标签

## 说明

- 当前 `dexkit` 薄封装适合仓库内联编使用。
- 如果要把 `dexkit` 单独发布到 Maven 仓库，还需要进一步整理发布模型和上游依赖坐标。
- `cli` 当前已经建立在 `core` 自有 model/request API 之上；如果后续有仓库外调用方仍依赖旧透传接口，需要单独评估迁移说明，而不是再把旧接口加回公共边界。

## Android 侧环境调整（如果无法直接构建）

仓库作者当前维护环境下，`dexkit/vendor/DexKit` 需要以下本地配置才能跑通 Android 构建。如果换设备、换 NDK，通常也是改这几处。

需要调整的文件：

- `dexkit/vendor/DexKit/settings.gradle`
  - 确保仓库列表里包含 `mavenLocal()`，让上游能解析本地发布的 `libcxx`
- `dexkit/vendor/DexKit/dexkit-android/build.gradle`
  - `compileSdk`
  - `ndkVersion`
  - `dev.rikka.ndk.thirdparty:libcxx` 的版本
  - 是否保留 `lintPublish(project(":lint-rules"))`

可以按下面这种方式调整：

```groovy
// dexkit/vendor/DexKit/settings.gradle
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

// dexkit/vendor/DexKit/dexkit-android/build.gradle
android {
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    externalNativeBuild {
        cmake {
            path file("CMakeLists.txt")
            // 由当前环境自行解析 SDK CMake 版本
        }
    }
}

dependencies {
    implementation("dev.rikka.ndk.thirdparty:libcxx:1.3.0")

    // 如果当前环境无法稳定访问 Google Maven，可先关闭
    // lintPublish(project(":lint-rules"))
}
```

如果换到别的环境，优先检查这几项是否一致：

- 本地是否已经执行过 `:cxx:publishToMavenLocal`
- `ndkVersion` 是否对应当前机器可用的 NDK
- `libcxx` 坐标是否与本地发布版本一致
- `lint-rules` 依赖是否能正常从 Google Maven 拉取
