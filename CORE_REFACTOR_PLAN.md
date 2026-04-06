# Core 模块重构方案

## 结论先行

`core` 当前更适合做“兼容式重构”，不适合直接整体重写。

推荐路线：

1. 先补最小测试保护
2. 先建立 `core` 自有模型与配置模型
3. 再拆 `DexEngine`
4. 最后迁移 `cli`
5. 在保留 KMP 结构的前提下继续收敛公共边界

这份文档的目标，不是描述一个理想结构，而是给出一条能在当前仓库里逐步落地的重构路径。

## 背景

当前 `core/` 已经承担了多类职责：

- 输入校验
- Dex 会话装载
- DexKit 搜索
- dex / smali / java 导出
- 运行时资源管理

对 `cli/` 暴露的入口则集中在单个 `DexEngine` 上。

这套实现目前可用，但边界尚未真正站稳。

## 目标

- 保持模块依赖方向不变：`cli -> core -> dexkit`
- 让 `core` 对上层暴露稳定、自有的数据模型与配置模型
- 将搜索、导出、输入分析、会话管理从 `DexEngine` 中拆开
- 在重构过程中保持 CLI 现有行为不变
- 在保留 KMP 结构的前提下，为后续非 JVM 消费场景预留稳定边界

## 非目标

- 本阶段不调整 `dexkit/` 的对外包名与发布模型
- 本阶段不改 vendored `DexKit` 上游实现
- 本阶段不主动增加新的 CLI 命令
- 本阶段不主动改变 CLI 输出格式
- 本阶段不为了“更整洁”而修改既有行为

## 当前问题

### 1. 公共 API 泄漏了第三方细节

`core` 当前直接向上暴露了 `DexKitBridge`、`ClassData`、`MethodData`。

这会带来三个问题：

- `cli` 无法只依赖 `core` 自己的语义模型
- `core` 对外边界会被 `dexkit` 一起拖着走
- 将来如果替换搜索后端，公共 API 也会被迫变化

### 2. `DexEngine` 职责过满

当前 `DexEngine` 同时负责：

- 输入路径清洗
- `DexSession` 初始化
- `DexKitRuntime` 初始化
- 搜索转调
- 导出转调
- 生命周期关闭

这使得任何一类能力调整都可能波及整条调用链。

### 3. 状态模型不清晰

实例初始化时已经传入了 `dexPaths`，但导出接口仍要求再次传入 `dexPath`。

这说明当前把这些概念混在了一起：

- 输入集合
- 已加载的会话
- 单次导出所针对的源 dex

如果后续支持 APK、多 dex、自动定位源 dex，复杂度还会继续扩散。

### 4. 缺少保护网

当前 `core` 和 `cli` 基本没有自己的测试。

没有保护网时，直接重写结构只能依赖构建和手工回归，不适合作为第一步。

## 重构原则

- 先稳住行为，再整理结构
- 先建立 `core` 自有语义，再谈第三方适配
- 公共 API 只暴露稳定语义，不暴露底层库原生类型
- 跨平台模型放 `commonMain`
- JVM 特有高级扩展放 `jvmMain`
- 不为了抽象而抽象，优先收口当前已经散落的硬编码

## 目标结构

建议把 `core` 调整为“稳定 API + 自有模型 + 内部适配实现”的结构。

推荐目录草案：

```text
core/
  src/commonMain/kotlin/io/github/dexclub/core/
    api/
      DexArchive.kt
      DexInspector.kt
      DexSearcher.kt
      DexExporter.kt
    model/
      DexArchiveInfo.kt
      DexClassHit.kt
      DexMethodHit.kt
      DexExportFormat.kt
      DexExportResult.kt
      DexInputRef.kt
    config/
      CoreRuntimeConfig.kt
      DexFormatConfig.kt
      DexKitRuntimeConfig.kt
      JavaDecompileConfig.kt
      SmaliRenderConfig.kt
    request/
      DexExportRequest.kt
      JavaExportRequest.kt
      SmaliExportRequest.kt
    facade/
      DexEngine.kt
  src/jvmMain/kotlin/io/github/dexclub/core/
    internal/input/
      DexInputInspector.kt
      DexInputNormalizer.kt
    internal/session/
      DexSession.kt
      DexSessionLoader.kt
    internal/search/
      DexKitSearchBackend.kt
      DexSearchService.kt
    internal/export/
      DexExportService.kt
      JadxDecompilerService.kt
      SmaliRenderService.kt
    internal/runtime/
      DexKitRuntime.kt
    facade/
      DexEngine.jvm.kt
```

这里真正重要的是职责边界：

- `api/`
  - 上层可见的能力接口
- `model/`
  - `core` 自己的结果模型
- `config/`
  - `core` 自己的稳定配置模型
- `request/`
  - 单次调用的请求对象
- `internal/`
  - 第三方适配与实现细节
- `facade/`
  - 组合入口，不再承载全部业务

## 对外模型

### 高层入口

保留 `DexEngine` 作为稳定入口，但弱化其职责，让它只做 facade。

示意：

```kotlin
interface DexArchive : AutoCloseable {
    fun inspect(): DexArchiveInfo
    fun searchClasses(keyword: String, ignoreCase: Boolean = true): List<DexClassHit>
    fun searchMethodsByString(keyword: String, ignoreCase: Boolean = true): List<DexMethodHit>
    suspend fun exportDex(request: DexExportRequest): DexExportResult
    suspend fun exportSmali(request: SmaliExportRequest): DexExportResult
    suspend fun exportJava(request: JavaExportRequest): DexExportResult
}

class DexEngine(
    inputs: List<String>,
    config: CoreRuntimeConfig = CoreRuntimeConfig(),
) : DexArchive
```

### 结果模型

建议新增：

- `DexArchiveInfo`
  - `kind: DexInputKind`
  - `inputs: List<DexInputRef>`
  - `dexCount: Int`
  - `classCount: Int?`
- `DexClassHit`
  - `name`
  - `descriptor`
  - `sourceDexPath`
- `DexMethodHit`
  - `className`
  - `name`
  - `descriptor`
  - `sourceDexPath`
- `DexExportResult`
  - `outputPath`
  - `format`
  - `className`

这些模型的目的只有一个：让 `core` 对上层表达自己的语义，而不是继续透传第三方结果类型。

### 请求模型

建议把单次导出请求明确表达出来：

```kotlin
data class DexExportRequest(
    val className: String,
    val sourceDexPath: String,
    val outputPath: String,
)

data class SmaliExportRequest(
    val className: String,
    val sourceDexPath: String,
    val outputPath: String,
    val config: SmaliRenderConfig? = null,
)

data class JavaExportRequest(
    val className: String,
    val sourceDexPath: String,
    val outputPath: String,
    val config: JavaDecompileConfig? = null,
)
```

这样可以明确区分：

- 引擎初始化时的输入集合
- 某次导出所针对的源 dex
- 某次导出的局部配置覆盖

## 配置策略

### 原则

`jadx`、`dexlib2`、`baksmali`、`DexKit` 都有配置点，但不要把它们的原生配置对象直接暴露到 `core` 公共 API。

统一原则：

- `cli`
  - 只解析少量稳定开关
- `core` 公共 API
  - 只暴露 `core` 自己定义的配置模型
- `jvmMain`
  - 负责把 `core` 配置映射为底层库原生对象

### 统一运行时配置

建议建立统一配置容器：

```kotlin
data class CoreRuntimeConfig(
    val dexFormat: DexFormatConfig = DexFormatConfig(),
    val dexKit: DexKitRuntimeConfig = DexKitRuntimeConfig(),
    val javaDecompile: JavaDecompileConfig = JavaDecompileConfig(),
    val smaliRender: SmaliRenderConfig = SmaliRenderConfig(),
)
```

配置优先级建议固定为：

```text
单次请求配置 > 引擎默认配置 > core 内部默认值
```

### `jadx`

当前 `exportSingleJavaSource` 里直接硬编码了 `JadxArgs`，这对 `core` 作为库是不够的。

建议抽成：

```kotlin
data class JavaDecompileConfig(
    val useDxInput: Boolean = true,
    val renameValid: Boolean = false,
    val renameCaseSensitive: Boolean = true,
    val showInconsistentCode: Boolean = false,
    val debugInfo: Boolean = false,
    val moveInnerClasses: Boolean = false,
    val inlineAnonymousClasses: Boolean = false,
)
```

说明：

- `cli`
  - 只开放少量必要开关
- `core` 作为库
  - 可设置引擎默认反编译配置
  - 也可在单次 Java 导出时局部覆盖
- `jvmMain`
  - 将 `JavaDecompileConfig` 映射到 `JadxArgs`

如果后续确实需要更深定制，可以只在 `jvmMain` 增加 JVM 专用扩展入口，但不要让 `JadxArgs` 进入 `commonMain`。

### `dexlib2` / `baksmali`

当前 `core` 里已经存在两类硬编码：

- dex 读写相关
  - `Opcodes.getDefault()`
- smali 渲染相关
  - `parameterRegisters`
  - `localsDirective`
  - `debugInfo`
  - `accessorComments`
  - `autoUnicodeDecode`

建议拆成：

```kotlin
data class DexFormatConfig(
    val opcodeApiLevel: Int? = null,
)

data class SmaliRenderConfig(
    val autoUnicodeDecode: Boolean = true,
    val parameterRegisters: Boolean = true,
    val localsDirective: Boolean = true,
    val debugInfo: Boolean = true,
    val accessorComments: Boolean = true,
)
```

说明：

- `DexFormatConfig`
  - 影响 `DexSessionLoader`、`DexPool` 等需要 `Opcodes` 的路径
- `SmaliRenderConfig`
  - 影响 smali 导出

当前阶段不需要把 `dexlib2` 更细的内部选项全部抬出来，先把已经硬编码在仓库里的部分收口即可。

### `DexKit`

当前至少存在这些可配置点：

- 工作线程数 `setThreadNum`
- 是否预热全量缓存 `initFullCache`
- 输入来源策略
  - apk 路径
  - dex bytes
  - classloader + `useMemoryDexFile`

建议公共层先只收口运行时策略：

```kotlin
data class DexKitRuntimeConfig(
    val threadCount: Int? = null,
    val initFullCache: Boolean = false,
)
```

说明：

- `threadCount`
  - 进入公共配置是合理的，因为它表达的是运行时策略
- `initFullCache`
  - 进入公共配置也是合理的，因为它表达的是性能/内存取舍
- `ClassLoader` 与 `useMemoryDexFile`
  - 属于 JVM 特有高级入口，不应直接进入 `commonMain`

如果后续确实需要 `ClassLoader` 初始化路径，应只在 `jvmMain` 提供扩展入口。

## 迁移阶段

### 阶段 1：补保护网

目标：在不改行为的前提下，建立最小回归保护。

建议覆盖：

- `DexEngine.isDex`
- `inspect`
- `searchClassesByName`
- `searchMethodsByString`
- `exportSingleDex`
- `exportSingleSmali`
- `exportSingleJavaSource`

最低验证：

```bash
./gradlew :core:compileKotlinJvm :cli:compileKotlin
```

如果补了测试，再加：

```bash
./gradlew :core:jvmTest
```

### 阶段 2：引入自有模型与配置模型

目标：先建立稳定边界，但不立刻破坏旧接口。

做法：

- 新增 `core/model`
- 新增 `core/config`
- 新增 `core/request`
- 新增 `DexSearcher`、`DexExporter`、`DexInspector` 接口
- 新增内部映射层，把 `dexkit` 结果映射为 `core/model`
- 把 `jadx`、`dexlib2`、`DexKit` 的当前硬编码迁移到 `CoreRuntimeConfig`
- 旧 `DexEngine` 继续保留，对外行为不变

阶段完成标准：

- `core` 已经拥有自己的结果模型
- `core` 已经拥有自己的配置模型
- `cli` 还可以继续使用旧接口

### 阶段 3：拆 `DexEngine`

目标：把当前大而全的实现拆成组合式服务。

建议拆分为：

- `DexArchiveInspector`
  - 输入识别、dex 数量、class 数量
- `DexSearchService`
  - 搜索能力
- `DexExportService`
  - 导出能力
- `JadxDecompilerService`
  - Java 反编译
- `SmaliRenderService`
  - smali 渲染
- `DexKitSearchBackend`
  - 与 `dexkit` 交互
- `DexSession`
  - dexlib2 视图与类索引

此阶段结束后，`DexEngine` 只负责组合这些服务，并处理 `close()`。

### 阶段 4：迁移 CLI 到新 API

目标：让 `cli` 只依赖 `core` 自有模型和配置。

做法：

- `cli` 停止直接消费 `ClassData`、`MethodData`
- `cli` 停止依赖 `DexKitBridge`
- `cli` 使用 `DexArchiveInfo`、`DexClassHit`、`DexMethodHit`、`DexExportResult`
- `cli` 只负责命令参数解析与输出格式化

这一步完成后，`core` 与 `dexkit` 的公共边界才算真正建立。

### 阶段 5：删除旧接口

前提：

- `cli` 已完全迁移
- `core` 的测试足够覆盖当前 CLI 使用路径

可以删除的内容包括：

- 旧的透传型返回值
- 不再需要的 facade 兼容方法
- 多余的 `expect/actual` 占位接口

## 首批实际改动

如果现在开始落地，第一批建议只做下面这些：

1. 新增 `core/model`
2. 新增 `core/config`
3. 新增 `core/request`
4. 新增 `DexSearchService` 与结果映射逻辑
5. 把 `jadx`、`dexlib2`、`DexKit` 的默认配置从实现里提出来
6. 让 `DexEngine` 新增基于 `core/model` 的方法
7. 保留旧方法，先不删
8. 为搜索和导出补 JVM 测试

这批做完后的直接收益：

- `core` 开始拥有自己的语义模型
- Java 导出、smali 导出、DexKit 搜索开始拥有稳定配置入口
- `DexEngine` 开始从“直接干活”转向“转调服务”
- `cli` 可以分批迁移，不需要一次性改完

## 保留 KMP

`core` 必须继续保留当前 KMP 结构，这一项不再作为待评估事项。

后续约束：

- 保留 `commonMain` / `jvmMain` 的现有结构
- 继续把稳定的 model、config、request、facade 边界放在 `commonMain`
- 继续把第三方适配与平台实现细节放在 `jvmMain`
- 不为了保留 KMP 而把 `DexKitBridge`、`JadxArgs`、`BaksmaliOptions` 这类第三方原生类型暴露到 `commonMain`

## 风险与注意事项

- 不要直接改 vendored `DexKit` 来配合 `core` 重构
- 不要在没有测试的情况下同时改 `cli` 输出和 `core` 结构
- 不要把 `JadxArgs`、`BaksmaliOptions`、`DexKitBridge` 这类第三方原生类型直接公开到 `commonMain`
- 不要把 `ClassLoader` 相关初始化策略抬到跨平台 API
- 不要为了“接口统一”把 dexlib2、jadx、DexKit 全部抽成过度泛化的超级接口
- `DexSessionLoader` 当前会吞掉非法 dex 并打印 `stderr`，后续应改成明确返回错误或聚合校验结果，但这一步要在兼容迁移里做，不能悄悄改 CLI 行为

## 最低验证要求

### 阶段 1-2

```bash
./gradlew :core:compileKotlinJvm :cli:compileKotlin
```

### 阶段 3-4

```bash
./gradlew :core:compileKotlinJvm :cli:compileKotlin :cli:fatJar
```

### 涉及 `dexkit` 或 Android 构建链时

```bash
./gradlew :dexkit:compileKotlinJvm
./gradlew :dexkit:assembleAndroidMain
```
