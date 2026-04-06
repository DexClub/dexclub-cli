# dexclub-cli

`dexclub-cli` 是一个基于 DexKit 的 Kotlin 多模块工程，当前包含 CLI、核心能力层，以及一层面向 KMP 的 DexKit 薄封装。

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
- `core` 当前对外以自有 API 为主，不再通过公共边界透传 `DexKitBridge`、`ClassData`、`MethodData`。
- `dexkit` 负责提供 `io.github.dexclub.dexkit` 这一层 KMP API。
- Android 侧通过上游 `DexKit` 的 `dexkit-android` 产物提供底层实现。
- JVM 侧通过 included build 使用本地 `vendor/DexKit`，并在 `jvmProcessResources` 时拷贝 native 库。
- 根工程启用了 `mavenLocal()`，用于解析当前 Android 构建所需的 `dev.rikka.ndk.thirdparty:libcxx:1.3.0`。

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

- `cli` 已迁移到这组自有 API
- `core` 公共边界不再直接暴露 `DexKitBridge`、`ClassData`、`MethodData`
- 第三方适配与平台实现细节继续留在 `jvmMain`

## CLI 输入约束

- `inspect`、`search-class`、`search-string`
  - 支持重复传入 `--input`
  - 单输入时支持 `apk` 或 `dex`
  - 多输入时当前仅支持多个 `dex`，不支持混合传入 `apk`
- `extract-class-dex`、`render-smali`、`decompile-java`
  - 当前仍只支持单个 `dex` 输入
  - 导出请求必须显式指定来源 dex

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
            // 不再锁死某个 SDK CMake 版本，让当前环境自行解析
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
