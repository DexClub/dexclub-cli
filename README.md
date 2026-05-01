# dexclub-cli

`dexclub-cli` 是一个面向 `dex / apk / Android 资源` 的 Kotlin 多模块项目，当前仓库包含：

- `cli`
  命令行入口，负责参数解析、命令分发、结果渲染与退出码
- `core`
  稳定能力边界，负责 workspace、dex 查询/导出、resource 解析
- `dexkit`
  面向 KMP 的 DexKit 包装层

## 当前能力

- 初始化并管理 `.dexclub` 工作区
- 在同一工作区内列出并切换 active target
- 输出工作区状态、摘要与可重建缓存清理结果
- 按 JSON 条件查找类、方法、字段
- 按字符串批量条件查找类和方法
- 导出单类 dex、smali、Java，以及单方法 dex、smali、Java
- 解析 `AndroidManifest.xml`
- 读取 `resources.arsc`
- 解码二进制 XML
- 列出、解析、搜索资源条目

## 仓库结构

- `cli/`
  当前命令行模块
- `core/`
  当前能力库模块
- `dexkit/`
  KMP DexKit 包装层
- `dexkit/vendor/DexKit/`
  vendored 上游 DexKit
- `dexkit/vendor/libcxx-prefab/`
  Android 构建链依赖的本地 `libcxx` prefab 仓库
- `.docs/v3/`
  当前命令面、对象模型、执行流与存储结构设计文档

## 环境要求

开发和本地构建当前默认要求：

- JDK 21
- Android SDK
- Android NDK `28.2.13676358`
- `cmake`
- `ninja`

说明：

- Android SDK / NDK 主要服务于仓库构建链、DexKit native 构建和测试样本生成
- `core` 的资源命令运行时不要求额外安装 Android SDK
- 桌面端 DexKit 运行前提可参考上游文档：
  <https://luckypray.org/DexKit/zh-cn/guide/run-on-desktop.html>

## 初始化仓库

首次拉取后先初始化 submodule：

```bash
git submodule update --init --recursive
```

如果本地还没有发布 `libcxx`，先执行：

```bash
cd dexkit/vendor/libcxx-prefab
./gradlew :cxx:publishToMavenLocal
```

## 构建与验证

编译 `dexkit`：

```bash
./gradlew :dexkit:compileKotlinJvm
./gradlew :dexkit:assembleAndroidMain
```

编译 `core` 和 `cli`：

```bash
./gradlew :core:compileKotlinJvm :cli:compileKotlin
```

运行模块测试：

```bash
./gradlew :core:jvmTest
./gradlew :cli:test
```

打包 fat jar：

```bash
./gradlew :cli:fatJar
```

生成带启动脚本的分发目录与 zip：

```bash
./gradlew :cli:installShadowDist :cli:shadowDistZip
```

Windows PowerShell 下推荐优先使用：

```text
cli/build/install/cli-shadow/bin/cli.ps1
```

fat jar 默认输出为：

```text
cli/build/libs/dexclub-cli-all.jar
```

## CLI 使用

fat jar 入口：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar <command> [args]
```

查看 help：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar
java -jar cli/build/libs/dexclub-cli-all.jar --version
java -jar cli/build/libs/dexclub-cli-all.jar --help
java -jar cli/build/libs/dexclub-cli-all.jar help
java -jar cli/build/libs/dexclub-cli-all.jar help find-method
java -jar cli/build/libs/dexclub-cli-all.jar find-method --help
```

Windows PowerShell 若要通过分发脚本传递复杂 `--query-json`，优先使用：

```powershell
& .\cli\build\install\cli-shadow\bin\cli.ps1 find-class E:\path\to\workdir --query-json '{"matcher":{"className":{"value":"Sample","matchType":"Contains","ignoreCase":true}}}'
```

说明：

- `cli.ps1` 会比 `cli.bat` 更稳定地保留 PowerShell 参数边界
- 对复杂 JSON 查询，仍可优先使用 `--query-file` 或 `java -jar`

当前命令面遵循两条规则：

- `init` 必须显式传入单文件输入路径
- 其余命令统一在已初始化工作区上执行，`[workdir]` 可省略；省略时默认使用当前目录

当前工作区允许存在多个已初始化 target，但任意时刻只有一个 active target：

- `init <input>`：为该输入创建或刷新 target，并将其设为当前 active target
- `switch <input>`：只在当前工作区内切换到已经初始化过的 target，不创建新 target
- `targets`：列出当前工作区已有 target，并标出当前 active target

### 工作区命令

初始化工作区：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar init /path/to/app.apk
java -jar cli/build/libs/dexclub-cli-all.jar init /path/to/classes.dex
java -jar cli/build/libs/dexclub-cli-all.jar init /path/to/AndroidManifest.xml
```

查看状态、摘要和清理缓存：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar status /path/to/workdir
java -jar cli/build/libs/dexclub-cli-all.jar inspect /path/to/workdir --json
java -jar cli/build/libs/dexclub-cli-all.jar gc /path/to/workdir
```

切换 active target 与列出当前工作区 target：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar switch a.apk
java -jar cli/build/libs/dexclub-cli-all.jar targets /path/to/workdir
java -jar cli/build/libs/dexclub-cli-all.jar targets /path/to/workdir --json
```

说明：

- `switch` 的输入按当前工作区解析，推荐使用工作区内已绑定输入的相对路径
- `switch` 不会创建新 target；若该输入尚未初始化，需先执行 `init`

受管工作区固定写入：

```text
<workdir>/.dexclub/
```

### Dex 查询命令

当前支持：

- `find-class`
- `find-method`
- `find-field`
- `find-class-using-strings`
- `find-method-using-strings`
- `inspect-method`

示例：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar find-class /path/to/workdir \
  --query-json '{"matcher":{"className":{"value":"Sample","matchType":"Contains","ignoreCase":true}}}' \
  --json
```

```bash
java -jar cli/build/libs/dexclub-cli-all.jar find-method-using-strings /path/to/workdir \
  --query-file /path/to/query.json
```

```bash
java -jar cli/build/libs/dexclub-cli-all.jar inspect-method /path/to/workdir \
  --descriptor 'Lcom/example/Sample;->name()Ljava/lang/String;' \
  --include using-fields,callers,invokes \
  --json
```

规则：

- `--query-json` 与 `--query-file` 必须且只能传一个
- `--query-file` 按 UTF-8 读取
- `--offset` 默认 `0`
- `--limit` 默认 `all`
- `inspect-method` 是详情命令，不接受查询 JSON
- `inspect-method` 的 `--descriptor` 必须在当前工作区内唯一命中

### Dex 导出命令

当前支持：

- `export-class-dex`
- `export-class-smali`
- `export-class-java`
- `export-method-smali`
- `export-method-dex`
- `export-method-java`

示例：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-class-smali /path/to/workdir \
  --class Lcom/example/Sample; \
  --output /tmp/Sample.smali
```

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-method-smali /path/to/workdir \
  --method 'Lcom/example/Sample;->name()Ljava/lang/String;' \
  --output /tmp/Sample_method.smali
```

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-method-smali /path/to/workdir \
  --method 'Lcom/example/Sample;->name()Ljava/lang/String;' \
  --mode class \
  --output /tmp/Sample_method.class.smali
```

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-method-dex /path/to/workdir \
  --method 'Lcom/example/Sample;->name()Ljava/lang/String;' \
  --output /tmp/Sample_method.dex
```

```bash
java -jar cli/build/libs/dexclub-cli-all.jar export-method-java /path/to/workdir \
  --method 'Lcom/example/Sample;->name()Ljava/lang/String;' \
  --output /tmp/Sample_method.java
```

规则：

- `export-method-smali` 的 `--method` 使用完整 smali 方法签名
- `--mode` 默认 `snippet`
- `snippet` 只导出目标方法块
- `class` 导出方法级最小类壳，只保留类头和目标方法
- `class` 模式当前不保留字段，也不保留其他方法
- `export-method-dex` 导出方法级最小单类 dex，只保留类头和目标方法
- `export-method-java` 基于方法级最小单类 dex 反编译 Java，只保留类头和目标方法

### 资源命令

当前支持：

- `manifest`
- `res-table`
- `decode-xml`
- `list-res`
- `resolve-res`
- `find-res`

其中：

- `list-res` 当前仅支持 APK 工作区
- `resolve-res` / `find-res` 支持 APK 与 `resources.arsc` 工作区

示例：

```bash
java -jar cli/build/libs/dexclub-cli-all.jar manifest /path/to/workdir
java -jar cli/build/libs/dexclub-cli-all.jar res-table /path/to/workdir --json
java -jar cli/build/libs/dexclub-cli-all.jar decode-xml /path/to/workdir --path res/layout/activity_main.xml
java -jar cli/build/libs/dexclub-cli-all.jar resolve-res /path/to/workdir --id 0x7f0a0001
```

## 设计文档

完整命令契约、对象模型与执行流请直接参考：

- [.docs/v3/40-cli-surface.md](.docs/v3/40-cli-surface.md)
- [.docs/v3/10-domain-model.md](.docs/v3/10-domain-model.md)
- [.docs/v3/60-execution-flow.md](.docs/v3/60-execution-flow.md)
- [.docs/v3/50-storage-layout.md](.docs/v3/50-storage-layout.md)
- [.docs/v3/51-state-file-schemas.md](.docs/v3/51-state-file-schemas.md)

如果要继续推进当前实现顺序，请参考：

- [.docs/v3/63-p0-delivery-order.md](.docs/v3/63-p0-delivery-order.md)
- [.docs/v3/64-implementation-checklist.md](.docs/v3/64-implementation-checklist.md)

## 开发说明

当前依赖边界：

- `cli` 只负责用户交互和命令组织
- `core` 负责公开稳定能力边界
- `dexkit` 负责承接上游 DexKit

当前实现依赖：

- Dex 导出相关：
  - `dexlib2`
  - `baksmali`
  - `jadx`
- 资源解析相关：
  - `ARSCLib`

其中：

- `jadx` 主要用于 Java 导出
- `manifest / binary xml / resource table` 不以 `jadx` 作为主解析路径

JVM 侧 DexKit native 相关配置：

- 显式 native 文件路径：
  - JVM property: `dexclub.dexkit.native.library.path`
  - 环境变量: `DEXCLUB_DEXKIT_NATIVE_LIBRARY_PATH`
- 显式 native 目录：
  - JVM property: `dexclub.dexkit.native.library.dir`
  - 环境变量: `DEXCLUB_DEXKIT_NATIVE_LIBRARY_DIR`

当前 JVM/CLI 侧不再从 `jar` 内解压 DexKit native，也不再使用独立的 native 缓存目录。
调用方需要显式提供 native 文件，或确保其位于 `java.library.path` 可见范围内。

## CI

仓库内当前提供：

- [.github/workflows/build-cli.yml](.github/workflows/build-cli.yml)

用途是按平台构建 CLI 分发产物，并在 tag 构建时上传发布附件。
