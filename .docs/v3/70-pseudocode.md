# DexClub CLI V3 伪代码草案

## 目标

本草案只用于表达当前推荐的实现骨架：

- `cli`
  - `CliRequest`
  - `CommandAdapter`
  - `Renderer`
  - `OutputWriter`
- `core`
  - `WorkspaceService`
  - `DexAnalysisService`
  - `ResourceService`
  - `WorkspaceStore`
  - `WorkspaceRuntimeResolver`
  - executors

不再保留旧的 `Command + CommandOptions + WorkspaceHandle` 伪代码模型。

## 公共对象

```kotlin
enum class WorkspaceKind {
    Apk,
    Dex,
    Manifest,
    Arsc,
    Axml,
}

enum class InputType {
    File,
}

enum class WorkspaceState {
    Healthy,
    Degraded,
    Broken,
}

enum class CacheState {
    Present,
    Partial,
    Missing,
}

enum class WorkspaceIssueSeverity {
    Warning,
    Error,
}

data class CapabilitySet(
    val inspect: Boolean = true,
    val findClass: Boolean = false,
    val findMethod: Boolean = false,
    val findField: Boolean = false,
    val exportDex: Boolean = false,
    val exportSmali: Boolean = false,
    val exportJava: Boolean = false,
    val manifestDecode: Boolean = false,
    val resourceTableDecode: Boolean = false,
    val xmlDecode: Boolean = false,
    val resourceEntryList: Boolean = false,
)

data class InventoryCounts(
    val apkCount: Int,
    val dexCount: Int,
    val manifestCount: Int,
    val arscCount: Int,
    val binaryXmlCount: Int,
)

data class TargetHandle(
    val targetId: String,
    val inputType: InputType,
    val inputPath: String,
)

data class TargetSnapshotSummary(
    val kind: WorkspaceKind,
    val inventoryFingerprint: String,
    val contentFingerprint: String,
    val capabilities: CapabilitySet,
    val inventoryCounts: InventoryCounts,
)

data class WorkspaceContext(
    val workdir: String,
    val dexclubDir: String,
    val workspaceId: String,
    val activeTargetId: String,
    val activeTarget: TargetHandle,
    val snapshot: TargetSnapshotSummary,
)

data class WorkspaceIssue(
    val code: String,
    val severity: WorkspaceIssueSeverity,
    val message: String,
)

data class WorkspaceStatus(
    val workspaceId: String,
    val activeTargetId: String,
    val state: WorkspaceState,
    val issues: List<WorkspaceIssue>,
    val activeTarget: TargetHandle,
    val snapshot: TargetSnapshotSummary?,
    val cacheState: CacheState,
)

data class PageWindow(
    val offset: Int = 0,
    val limit: Int? = null,
)

data class SourceLocator(
    val sourcePath: String?,
    val sourceEntry: String?,
)

enum class MethodSmaliMode {
    Snippet,
    Class,
}
```

## CLI 请求模型

```kotlin
sealed interface QueryInput {
    data class Json(val text: String) : QueryInput
    data class File(val path: String) : QueryInput
}

sealed interface ResolveResTarget {
    data class ById(val id: String) : ResolveResTarget
    data class ByTypeName(
        val type: String,
        val name: String,
    ) : ResolveResTarget
}

sealed interface CliRequest {
    data class Init(val input: String) : CliRequest
    data class Status(val workdir: String?, val json: Boolean) : CliRequest
    data class Gc(val workdir: String?, val json: Boolean) : CliRequest
    data class Inspect(val workdir: String?, val json: Boolean) : CliRequest

    data class FindMethod(
        val workdir: String?,
        val query: QueryInput,
        val window: PageWindow,
        val json: Boolean,
    ) : CliRequest

    data class ExportMethodSmali(
        val workdir: String?,
        val method: String,
        val source: SourceLocator,
        val output: String,
        val autoUnicodeDecode: Boolean = true,
        val mode: MethodSmaliMode,
    ) : CliRequest

    data class ResolveRes(
        val workdir: String?,
        val target: ResolveResTarget,
        val json: Boolean,
    ) : CliRequest
}
```

## store 记录模型

```kotlin
data class WorkspaceRecord(
    val workspaceId: String,
    val activeTargetId: String,
    val createdAt: String,
    val updatedAt: String,
    val toolVersion: String,
)

data class TargetRecord(
    val targetId: String,
    val inputType: InputType,
    val inputPath: String,
    val createdAt: String,
    val updatedAt: String,
)

data class SnapshotRecord(
    val kind: WorkspaceKind,
    val inventoryFingerprint: String,
    val contentFingerprint: String,
    val capabilities: CapabilitySet,
    val counts: InventoryCounts,
    val generatedAt: String,
)
```

## WorkspaceStore

```kotlin
interface WorkspaceStore {
    fun exists(workdir: String): Boolean

    fun dexclubDir(workdir: String): String

    fun initialize(
        workdir: String,
        workspace: WorkspaceRecord,
        target: TargetRecord,
        snapshot: SnapshotRecord,
    )

    fun loadWorkspace(workdir: String): WorkspaceRecord

    fun saveWorkspace(workdir: String, workspace: WorkspaceRecord)

    fun loadTarget(workdir: String, targetId: String): TargetRecord

    fun saveTarget(workdir: String, target: TargetRecord)

    fun loadSnapshot(workdir: String, targetId: String): SnapshotRecord?

    fun saveSnapshot(workdir: String, targetId: String, snapshot: SnapshotRecord)

    fun clearTargetCache(workdir: String, targetId: String): GcResult
}
```

## WorkspaceRuntimeResolver

```kotlin
interface WorkspaceRuntimeResolver {
    fun open(workdir: String): WorkspaceContext

    fun loadStatus(ref: WorkspaceRef): WorkspaceStatus

    fun refreshSnapshot(workspace: WorkspaceContext): TargetSnapshotSummary
}
```

## Capability gate

```kotlin
enum class Operation {
    Inspect,
    FindClass,
    FindMethod,
    FindField,
    ExportDex,
    ExportSmali,
    ExportJava,
    ManifestDecode,
    ResourceTableDecode,
    XmlDecode,
    ResourceEntryList,
}

data class CapabilityError(
    val operation: Operation,
    val requiredCapability: String,
    val kind: String,
) : RuntimeException()

class CapabilityChecker {
    fun require(workspace: WorkspaceContext, operation: Operation) {
        val capabilities = workspace.snapshot.capabilities
        when (operation) {
            Operation.Inspect ->
                requireCapability(capabilities.inspect, operation, "inspect", workspace)
            Operation.FindClass ->
                requireCapability(capabilities.findClass, operation, "findClass", workspace)
            Operation.FindMethod ->
                requireCapability(capabilities.findMethod, operation, "findMethod", workspace)
            Operation.FindField ->
                requireCapability(capabilities.findField, operation, "findField", workspace)
            Operation.ExportDex ->
                requireCapability(capabilities.exportDex, operation, "exportDex", workspace)
            Operation.ExportSmali ->
                requireCapability(capabilities.exportSmali, operation, "exportSmali", workspace)
            Operation.ExportJava ->
                requireCapability(capabilities.exportJava, operation, "exportJava", workspace)
            Operation.ManifestDecode ->
                requireCapability(capabilities.manifestDecode, operation, "manifestDecode", workspace)
            Operation.ResourceTableDecode ->
                requireCapability(capabilities.resourceTableDecode, operation, "resourceTableDecode", workspace)
            Operation.XmlDecode ->
                requireCapability(capabilities.xmlDecode, operation, "xmlDecode", workspace)
            Operation.ResourceEntryList ->
                requireCapability(capabilities.resourceEntryList, operation, "resourceEntryList", workspace)
        }
    }

    private fun requireCapability(
        supported: Boolean,
        operation: Operation,
        requiredCapability: String,
        workspace: WorkspaceContext,
    ) {
        if (!supported) {
            throw CapabilityError(
                operation = operation,
                requiredCapability = requiredCapability,
                kind = workspace.snapshot.kind.name.lowercase(),
            )
        }
    }
}
```

## executors

```kotlin
interface DexSearchExecutor {
    fun findClasses(workspace: WorkspaceContext, request: FindClassesRequest): List<ClassHit>
    fun findMethods(workspace: WorkspaceContext, request: FindMethodsRequest): List<MethodHit>
    fun findFields(workspace: WorkspaceContext, request: FindFieldsRequest): List<FieldHit>
    fun findClassesUsingStrings(workspace: WorkspaceContext, request: FindClassesUsingStringsRequest): List<ClassHit>
    fun findMethodsUsingStrings(workspace: WorkspaceContext, request: FindMethodsUsingStringsRequest): List<MethodHit>
}

interface DexExportExecutor {
    fun exportClassDex(workspace: WorkspaceContext, request: ExportClassDexRequest): ExportResult
    fun exportClassSmali(workspace: WorkspaceContext, request: ExportClassSmaliRequest): ExportResult
    fun exportClassJava(workspace: WorkspaceContext, request: ExportClassJavaRequest): ExportResult
    fun exportMethodSmali(workspace: WorkspaceContext, request: ExportMethodSmaliRequest): ExportResult
}

interface ManifestExecutor {
    fun decodeManifest(workspace: WorkspaceContext): ManifestResult
}

interface ResourceTableExecutor {
    fun dumpResourceTable(workspace: WorkspaceContext): ResourceTableResult
    fun listResourceEntries(workspace: WorkspaceContext): List<ResourceEntry>
}

interface XmlExecutor {
    fun decodeXml(workspace: WorkspaceContext, request: DecodeXmlRequest): DecodedXmlResult
}

interface ResourceValueExecutor {
    fun resolveResourceValue(workspace: WorkspaceContext, request: ResolveResourceRequest): ResourceValue
    fun findResourceEntries(workspace: WorkspaceContext, request: FindResourcesRequest): List<ResourceEntryValueHit>
}
```

## services

```kotlin
interface WorkspaceService {
    fun initialize(input: String): WorkspaceContext
    fun open(ref: WorkspaceRef): WorkspaceContext
    fun loadStatus(ref: WorkspaceRef): WorkspaceStatus
    fun gc(workspace: WorkspaceContext): GcResult
    fun inspect(workspace: WorkspaceContext): InspectResult
}

interface DexAnalysisService {
    fun findMethods(workspace: WorkspaceContext, request: FindMethodsRequest): List<MethodHit>
    fun exportMethodSmali(workspace: WorkspaceContext, request: ExportMethodSmaliRequest): ExportResult
}

interface ResourceService {
    fun resolveResourceValue(workspace: WorkspaceContext, request: ResolveResourceRequest): ResourceValue
}
```

## 默认实现装配

```kotlin
data class Services(
    val workspace: WorkspaceService,
    val dex: DexAnalysisService,
    val resource: ResourceService,
)

fun createDefaultServices(): Services {
    val store = DefaultWorkspaceStore()
    val runtimeResolver = DefaultWorkspaceRuntimeResolver(store)

    val dexSearchExecutor = DefaultDexSearchExecutor()
    val dexExportExecutor = DefaultDexExportExecutor()
    val manifestExecutor = DefaultManifestExecutor()
    val resourceTableExecutor = DefaultResourceTableExecutor()
    val xmlExecutor = DefaultXmlExecutor()
    val resourceValueExecutor = DefaultResourceValueExecutor()

    val capabilityChecker = CapabilityChecker()
    val resultSorter = ResultSorter()
    val windowApplier = WindowApplier()

    val workspaceService = DefaultWorkspaceService(
        store = store,
        runtimeResolver = runtimeResolver,
    )
    val dexService = DefaultDexAnalysisService(
        capabilityChecker = capabilityChecker,
        searchExecutor = dexSearchExecutor,
        exportExecutor = dexExportExecutor,
        resultSorter = resultSorter,
        windowApplier = windowApplier,
    )
    val resourceService = DefaultResourceService(
        capabilityChecker = capabilityChecker,
        manifestExecutor = manifestExecutor,
        resourceTableExecutor = resourceTableExecutor,
        xmlExecutor = xmlExecutor,
        resourceValueExecutor = resourceValueExecutor,
        resultSorter = resultSorter,
        windowApplier = windowApplier,
    )

    return Services(
        workspace = workspaceService,
        dex = dexService,
        resource = resourceService,
    )
}
```

## CLI 适配

```kotlin
class DexSearchCommandAdapter(
    private val services: Services,
    private val queryTextLoader: QueryTextLoader,
    private val workdirResolver: WorkdirResolver,
) {
    fun findMethods(request: CliRequest.FindMethod): CommandResult {
        val workdir = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(WorkspaceRef(workdir))
        val queryText = queryTextLoader.load(request.query)
        val result = services.dex.findMethods(
            workspace = workspace,
            request = FindMethodsRequest(
                queryText = queryText,
                window = request.window,
            ),
        )
        return CommandResult.Payload(result, json = request.json)
    }
}
```

## 顶层执行流

```kotlin
fun runCli(argv: List<String>): Int {
    val services = createDefaultServices()
    val parser = CliParser()
    val dispatcher = CommandDispatcher(services)
    val renderer = Renderer()
    val outputWriter = OutputWriter()

    val request = parser.parse(argv)
    val commandResult = dispatcher.dispatch(request)
    val rendered = renderer.render(commandResult)
    outputWriter.write(rendered)
    return commandResult.exitCode
}
```

