package io.github.dexclub.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.github.dexclub.core.api.dex.ClassHit
import io.github.dexclub.core.api.dex.DexAnalysisService
import io.github.dexclub.core.api.dex.ExportClassDexRequest
import io.github.dexclub.core.api.dex.ExportClassJavaRequest
import io.github.dexclub.core.api.dex.ExportClassSmaliRequest
import io.github.dexclub.core.api.dex.ExportMethodDexRequest
import io.github.dexclub.core.api.dex.ExportMethodJavaRequest
import io.github.dexclub.core.api.dex.ExportMethodSmaliRequest
import io.github.dexclub.core.api.dex.ExportResult
import io.github.dexclub.core.api.dex.FieldHit
import io.github.dexclub.core.api.dex.FindClassesRequest
import io.github.dexclub.core.api.dex.FindFieldsRequest
import io.github.dexclub.core.api.dex.FindMethodsRequest
import io.github.dexclub.core.api.dex.InspectMethodRequest
import io.github.dexclub.core.api.dex.MethodDetail
import io.github.dexclub.core.api.dex.MethodHit
import io.github.dexclub.core.api.shared.CacheState
import io.github.dexclub.core.api.shared.CapabilitySet
import io.github.dexclub.core.api.shared.InputType
import io.github.dexclub.core.api.shared.InventoryCounts
import io.github.dexclub.core.api.shared.Services
import io.github.dexclub.core.app.session.TargetSessionService
import io.github.dexclub.core.api.shared.WorkspaceIssue
import io.github.dexclub.core.api.shared.WorkspaceKind
import io.github.dexclub.core.api.shared.WorkspaceState
import io.github.dexclub.core.api.resource.DecodeXmlRequest
import io.github.dexclub.core.api.resource.DecodedXmlResult
import io.github.dexclub.core.api.resource.FindResourcesRequest
import io.github.dexclub.core.api.resource.InspectManifestRequest
import io.github.dexclub.core.api.resource.ManifestApplicationInfo
import io.github.dexclub.core.api.resource.ManifestComponentInfo
import io.github.dexclub.core.api.resource.ManifestInspectionResult
import io.github.dexclub.core.api.resource.ManifestIntentFilter
import io.github.dexclub.core.api.resource.ManifestMetaData
import io.github.dexclub.core.api.resource.ManifestResult
import io.github.dexclub.core.api.resource.ManifestUsesSdk
import io.github.dexclub.core.api.resource.ResourceEntry
import io.github.dexclub.core.api.resource.ResourceEntryValueHit
import io.github.dexclub.core.api.resource.ResourceService
import io.github.dexclub.core.api.resource.ResourceTableResult
import io.github.dexclub.core.api.resource.ResourceValue
import io.github.dexclub.core.api.resource.ResolveResourceRequest
import io.github.dexclub.core.api.workspace.GcResult
import io.github.dexclub.core.api.workspace.InspectResult
import io.github.dexclub.core.api.workspace.TargetHandle
import io.github.dexclub.core.api.workspace.TargetSnapshotSummary
import io.github.dexclub.core.api.workspace.TargetSummary
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.api.workspace.WorkspaceRef
import io.github.dexclub.core.api.workspace.WorkspaceService
import kotlinx.serialization.json.JsonObject
import io.github.dexclub.core.api.workspace.WorkspaceStatus

fun fakeWorkspaceContext(): WorkspaceContext =
    mcpAppTestDir("workspace").let { workdir ->
        WorkspaceContext(
            workdir = workdir.toString(),
            dexclubDir = mcpAppTestDir("workspace", ".dexclub").toString(),
        workspaceId = "ws-1",
        activeTargetId = "target-1",
        activeTarget = TargetHandle(
            targetId = "target-1",
            inputType = InputType.File,
            inputPath = "sample.apk",
        ),
        snapshot = TargetSnapshotSummary(
            kind = WorkspaceKind.Apk,
            inventoryFingerprint = "inv-1",
            contentFingerprint = "content-1",
            capabilities = CapabilitySet(
                inspect = true,
                findClass = true,
                findMethod = true,
                exportSmali = true,
            ),
            inventoryCounts = InventoryCounts(
                apkCount = 1,
                dexCount = 2,
                manifestCount = 1,
                arscCount = 1,
                binaryXmlCount = 3,
            ),
        ),
        )
    }

internal fun createTestApp(
    workspace: WorkspaceContext = fakeWorkspaceContext(),
    workspaceService: WorkspaceService = FakeWorkspaceService(workspace),
    dexService: FakeDexAnalysisService = FakeDexAnalysisService(),
    resourceService: FakeResourceService = FakeResourceService(),
    sessionStore: TargetSessionService = TargetSessionService(),
    queryFilePolicy: McpQueryFilePolicy = McpQueryFilePolicy(loopback = true, allowedRoots = emptyList()),
): McpApp =
    McpApp(
        services = Services(
            workspace = workspaceService,
            dex = dexService,
            resource = resourceService,
        ),
        sessionStore = sessionStore,
        queryFilePolicy = queryFilePolicy,
    )

internal fun callToolRequest(
    name: String,
    arguments: JsonObject,
): CallToolRequest =
    CallToolRequest(
        CallToolRequestParams(
            name = name,
            arguments = arguments,
        ),
    )

class FakeWorkspaceService(
    private val workspace: WorkspaceContext,
) : WorkspaceService {
    var initializedInput: String? = null
    var openedRef: WorkspaceRef? = null
    var refreshedWorkspace: WorkspaceContext? = null
    val refreshedWorkspaces = mutableListOf<WorkspaceContext>()

    override fun initialize(input: String): WorkspaceContext {
        initializedInput = input
        return workspace
    }

    override fun switchTarget(ref: WorkspaceRef, input: String): WorkspaceRef = ref

    override fun open(ref: WorkspaceRef): WorkspaceContext {
        openedRef = ref
        return workspace
    }

    override fun listTargets(ref: WorkspaceRef): List<TargetSummary> = emptyList()

    override fun loadStatus(ref: WorkspaceRef): WorkspaceStatus =
        WorkspaceStatus(
            workspaceId = workspace.workspaceId,
            activeTargetId = workspace.activeTargetId,
            state = WorkspaceState.Healthy,
            issues = emptyList<WorkspaceIssue>(),
            activeTarget = workspace.activeTarget,
            snapshot = workspace.snapshot,
            cacheState = CacheState.Present,
        )

    override fun gc(workspace: WorkspaceContext): GcResult =
        GcResult(
            workdir = workspace.workdir,
            targetId = workspace.activeTargetId,
            deletedFiles = 0,
            deletedBytes = 0,
        )

    override fun refresh(workspace: WorkspaceContext): InspectResult =
        InspectResult(
            target = (refreshedWorkspace ?: workspace).activeTarget,
            snapshot = (refreshedWorkspace ?: workspace).snapshot,
            classCount = null,
        ).also {
            refreshedWorkspaces += workspace
        }

    override fun inspect(workspace: WorkspaceContext): InspectResult =
        InspectResult(
            target = workspace.activeTarget,
            snapshot = workspace.snapshot,
            classCount = null,
        )
}

class FakeDexAnalysisService(
    private val detail: MethodDetail = MethodDetail(
        method = MethodHit(
            className = "Lsample/Test;",
            methodName = "foo",
            descriptor = "Lsample/Test;->foo()V",
        ),
    ),
    private val findClassesResponse: List<ClassHit> = emptyList(),
    private val findMethodsResponse: List<MethodHit> = emptyList(),
    private val findFieldsResponse: List<FieldHit> = emptyList(),
) : DexAnalysisService {
    val releasedDexContexts = mutableListOf<WorkspaceContext>()
    var lastWorkspace: WorkspaceContext? = null
    var lastInspectRequest: InspectMethodRequest? = null
    var lastFindMethodsRequest: FindMethodsRequest? = null
    var lastFindClassesRequest: FindClassesRequest? = null
    var lastFindFieldsRequest: FindFieldsRequest? = null
    var lastExportClassSmaliRequest: ExportClassSmaliRequest? = null
    var lastExportClassJavaRequest: ExportClassJavaRequest? = null
    var lastExportMethodSmaliRequest: ExportMethodSmaliRequest? = null
    var lastExportMethodJavaRequest: ExportMethodJavaRequest? = null

    override fun releaseDexContext(workspace: WorkspaceContext) {
        releasedDexContexts += workspace
    }

    override fun findClasses(workspace: WorkspaceContext, request: FindClassesRequest): List<ClassHit> {
        lastWorkspace = workspace
        lastFindClassesRequest = request
        return findClassesResponse
    }

    override fun findMethods(workspace: WorkspaceContext, request: FindMethodsRequest): List<MethodHit> {
        lastWorkspace = workspace
        lastFindMethodsRequest = request
        return findMethodsResponse
    }

    override fun findFields(workspace: WorkspaceContext, request: FindFieldsRequest): List<FieldHit> {
        lastWorkspace = workspace
        lastFindFieldsRequest = request
        return findFieldsResponse
    }

    override fun inspectMethod(workspace: WorkspaceContext, request: InspectMethodRequest): MethodDetail {
        lastWorkspace = workspace
        lastInspectRequest = request
        return detail
    }

    override fun exportClassDex(workspace: WorkspaceContext, request: ExportClassDexRequest): ExportResult =
        ExportResult(request.outputPath)

    override fun exportClassSmali(workspace: WorkspaceContext, request: ExportClassSmaliRequest): ExportResult {
        lastWorkspace = workspace
        lastExportClassSmaliRequest = request
        java.io.File(request.outputPath).writeText("class-smali:${request.className}")
        return ExportResult(request.outputPath)
    }

    override fun exportClassJava(workspace: WorkspaceContext, request: ExportClassJavaRequest): ExportResult {
        lastWorkspace = workspace
        lastExportClassJavaRequest = request
        java.io.File(request.outputPath).writeText("class-java:${request.className}")
        return ExportResult(request.outputPath)
    }

    override fun exportMethodSmali(workspace: WorkspaceContext, request: ExportMethodSmaliRequest): ExportResult {
        lastWorkspace = workspace
        lastExportMethodSmaliRequest = request
        java.io.File(request.outputPath).writeText(
            "method-smali:${request.methodSignature}:${request.mode.name.lowercase()}",
        )
        return ExportResult(request.outputPath)
    }

    override fun exportMethodDex(workspace: WorkspaceContext, request: ExportMethodDexRequest): ExportResult =
        ExportResult(request.outputPath)

    override fun exportMethodJava(workspace: WorkspaceContext, request: ExportMethodJavaRequest): ExportResult {
        lastWorkspace = workspace
        lastExportMethodJavaRequest = request
        java.io.File(request.outputPath).writeText("method-java:${request.methodSignature}")
        return ExportResult(request.outputPath)
    }
}

class FakeResourceService(
    private val resourceEntries: List<ResourceEntry> = emptyList(),
    private val resourceValueHits: List<ResourceEntryValueHit> = emptyList(),
    private val resourceValue: ResourceValue? = null,
) : ResourceService {
    var lastWorkspace: WorkspaceContext? = null
    var lastInspectManifestRequest: InspectManifestRequest? = null
    var lastDecodeXmlRequest: DecodeXmlRequest? = null
    var lastResolveResourceRequest: ResolveResourceRequest? = null
    var lastFindResourcesRequest: FindResourcesRequest? = null

    override fun decodeManifest(workspace: WorkspaceContext): ManifestResult {
        lastWorkspace = workspace
        return ManifestResult(
            sourcePath = "sample.apk",
            sourceEntry = "AndroidManifest.xml",
            text = "<manifest package=\"fixture.sample\"/>",
        )
    }

    override fun inspectManifest(
        workspace: WorkspaceContext,
        request: InspectManifestRequest,
    ): ManifestInspectionResult {
        lastWorkspace = workspace
        lastInspectManifestRequest = request
        return ManifestInspectionResult(
            sourcePath = "sample.apk",
            sourceEntry = "AndroidManifest.xml",
            packageName = "fixture.sample",
            versionName = "1.0",
            usesSdk = ManifestUsesSdk(minSdkVersion = "21", targetSdkVersion = "34"),
            application = ManifestApplicationInfo(
                name = "fixture.sample.App",
                metaData = listOf(ManifestMetaData(name = "feature", value = "enabled")),
            ),
            activities = listOf(
                ManifestComponentInfo(
                    name = "fixture.sample.MainActivity",
                    exported = true,
                    intentFilters = listOf(
                        ManifestIntentFilter(actions = listOf("android.intent.action.MAIN")),
                    ),
                ),
            ),
            text = "<manifest package=\"fixture.sample\"/>".takeIf { request.includeText },
        )
    }

    override fun dumpResourceTable(workspace: WorkspaceContext): ResourceTableResult =
        ResourceTableResult(packageCount = 0, typeCount = 0, entryCount = 0)

    override fun decodeXml(workspace: WorkspaceContext, request: DecodeXmlRequest): DecodedXmlResult {
        lastWorkspace = workspace
        lastDecodeXmlRequest = request
        return DecodedXmlResult(
            sourcePath = "sample.apk",
            sourceEntry = request.path,
            text = "<LinearLayout/>",
        )
    }

    override fun listResourceEntries(workspace: WorkspaceContext): List<ResourceEntry> =
        resourceEntries.also {
            lastWorkspace = workspace
        }

    override fun getResourceValue(workspace: WorkspaceContext, request: ResolveResourceRequest): ResourceValue =
        (resourceValue ?: ResourceValue(
            resourceId = request.resourceId ?: "0x7f010001",
            type = request.type ?: "string",
            name = request.name ?: "fixture_name",
            variants = listOf(
                io.github.dexclub.core.api.resource.ResourceValueVariant(
                    configuration = io.github.dexclub.core.api.resource.ResourceConfiguration("", true),
                    value = io.github.dexclub.core.api.resource.ResourceTypedValue(
                        valueType = "STRING",
                        rawData = 0,
                        rawDataHex = "0x00000000",
                        decodedValue = "Fixture Name",
                    ),
                ),
            ),
        )).also {
            lastWorkspace = workspace
            lastResolveResourceRequest = request
        }

    override fun findResourceValues(
        workspace: WorkspaceContext,
        request: FindResourcesRequest,
    ): List<ResourceEntryValueHit> =
        resourceValueHits.also {
            lastWorkspace = workspace
            lastFindResourcesRequest = request
        }
}
