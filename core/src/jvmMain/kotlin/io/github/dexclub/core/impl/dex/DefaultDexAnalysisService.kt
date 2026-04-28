package io.github.dexclub.core.impl.dex

import io.github.dexclub.core.api.dex.ClassHit
import io.github.dexclub.core.api.dex.DexAnalysisService
import io.github.dexclub.core.api.dex.ExportClassDexRequest
import io.github.dexclub.core.api.dex.ExportClassJavaRequest
import io.github.dexclub.core.api.dex.ExportClassSmaliRequest
import io.github.dexclub.core.api.dex.ExportMethodSmaliRequest
import io.github.dexclub.core.api.dex.ExportResult
import io.github.dexclub.core.api.dex.FieldHit
import io.github.dexclub.core.api.dex.FieldUsageType
import io.github.dexclub.core.api.dex.FindClassesRequest
import io.github.dexclub.core.api.dex.FindClassesUsingStringsRequest
import io.github.dexclub.core.api.dex.FindFieldsRequest
import io.github.dexclub.core.api.dex.InspectMethodRequest
import io.github.dexclub.core.api.dex.MethodDetail
import io.github.dexclub.core.api.dex.MethodFieldUsage
import io.github.dexclub.core.api.dex.FindMethodsRequest
import io.github.dexclub.core.api.dex.FindMethodsUsingStringsRequest
import io.github.dexclub.core.api.dex.MethodHit
import io.github.dexclub.core.api.shared.Operation
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.api.workspace.WorkspaceResolveError
import io.github.dexclub.core.api.workspace.WorkspaceResolveErrorReason
import io.github.dexclub.core.impl.workspace.store.WorkspaceStore

internal class DefaultDexAnalysisService(
    private val store: WorkspaceStore,
    private val capabilityChecker: CapabilityChecker,
    private val queryParser: DexQueryParser,
    private val searchExecutor: DexSearchExecutor,
    private val exportExecutor: DexExportExecutor,
) : DexAnalysisService {
    override fun findClasses(workspace: WorkspaceContext, request: FindClassesRequest): List<ClassHit> {
        capabilityChecker.require(workspace, Operation.FindClass)
        val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
            ?: throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                workdir = workspace.workdir,
                message = "Active target snapshot is missing: ${workspace.activeTargetId}",
            )
        val query = queryParser.parseFindClass(request.queryText)
        val hits = searchExecutor.findClasses(
            workspace = workspace,
            inventory = snapshot.inventory,
            query = query,
        )
        return applyWindow(sortClassHits(hits), request.window.offset, request.window.limit)
    }

    override fun findMethods(workspace: WorkspaceContext, request: FindMethodsRequest): List<MethodHit> =
        run {
            capabilityChecker.require(workspace, Operation.FindMethod)
            val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
                ?: throw WorkspaceResolveError(
                    reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                    workdir = workspace.workdir,
                    message = "Active target snapshot is missing: ${workspace.activeTargetId}",
                )
            val query = queryParser.parseFindMethod(request.queryText)
            val hits = searchExecutor.findMethods(
                workspace = workspace,
                inventory = snapshot.inventory,
                query = query,
            )
            applyWindow(sortMethodHits(hits), request.window.offset, request.window.limit)
        }

    override fun findFields(workspace: WorkspaceContext, request: FindFieldsRequest): List<FieldHit> =
        run {
            capabilityChecker.require(workspace, Operation.FindField)
            val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
                ?: throw WorkspaceResolveError(
                    reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                    workdir = workspace.workdir,
                    message = "Active target snapshot is missing: ${workspace.activeTargetId}",
                )
            val query = queryParser.parseFindField(request.queryText)
            val hits = searchExecutor.findFields(
                workspace = workspace,
                inventory = snapshot.inventory,
                query = query,
            )
            applyWindow(sortFieldHits(hits), request.window.offset, request.window.limit)
        }

    override fun findClassesUsingStrings(
        workspace: WorkspaceContext,
        request: FindClassesUsingStringsRequest,
    ): List<ClassHit> =
        run {
            capabilityChecker.require(workspace, Operation.FindClass)
            val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
                ?: throw WorkspaceResolveError(
                    reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                    workdir = workspace.workdir,
                    message = "Active target snapshot is missing: ${workspace.activeTargetId}",
                )
            val query = queryParser.parseFindClassUsingStrings(request.queryText)
            val hits = searchExecutor.findClassesUsingStrings(
                workspace = workspace,
                inventory = snapshot.inventory,
                query = query,
            )
            applyWindow(sortClassHits(hits), request.window.offset, request.window.limit)
        }

    override fun findMethodsUsingStrings(
        workspace: WorkspaceContext,
        request: FindMethodsUsingStringsRequest,
    ): List<MethodHit> =
        run {
            capabilityChecker.require(workspace, Operation.FindMethod)
            val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
                ?: throw WorkspaceResolveError(
                    reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                    workdir = workspace.workdir,
                    message = "Active target snapshot is missing: ${workspace.activeTargetId}",
                )
            val query = queryParser.parseFindMethodUsingStrings(request.queryText)
            val hits = searchExecutor.findMethodsUsingStrings(
                workspace = workspace,
                inventory = snapshot.inventory,
                query = query,
            )
            applyWindow(sortMethodHits(hits), request.window.offset, request.window.limit)
        }

    override fun inspectMethod(
        workspace: WorkspaceContext,
        request: InspectMethodRequest,
    ): MethodDetail =
        run {
            capabilityChecker.require(workspace, Operation.FindMethod)
            val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
                ?: throw WorkspaceResolveError(
                    reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                    workdir = workspace.workdir,
                    message = "Active target snapshot is missing: ${workspace.activeTargetId}",
                )
            sortMethodDetail(
                searchExecutor.inspectMethod(
                    workspace = workspace,
                    inventory = snapshot.inventory,
                    request = request,
                ),
            )
        }

    override fun exportClassDex(workspace: WorkspaceContext, request: ExportClassDexRequest): ExportResult {
        capabilityChecker.require(workspace, Operation.ExportDex)
        val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
            ?: throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                workdir = workspace.workdir,
                message = "Active target snapshot is missing: ${workspace.activeTargetId}",
            )
        return exportExecutor.exportClassDex(
            workspace = workspace,
            inventory = snapshot.inventory,
            request = request,
        )
    }

    override fun exportClassSmali(workspace: WorkspaceContext, request: ExportClassSmaliRequest): ExportResult {
        capabilityChecker.require(workspace, Operation.ExportSmali)
        val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
            ?: throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                workdir = workspace.workdir,
                message = "Active target snapshot is missing: ${workspace.activeTargetId}",
            )
        return exportExecutor.exportClassSmali(
            workspace = workspace,
            inventory = snapshot.inventory,
            request = request,
        )
    }

    override fun exportClassJava(workspace: WorkspaceContext, request: ExportClassJavaRequest): ExportResult =
        run {
            capabilityChecker.require(workspace, Operation.ExportJava)
            val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
                ?: throw WorkspaceResolveError(
                    reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                    workdir = workspace.workdir,
                    message = "Active target snapshot is missing: ${workspace.activeTargetId}",
                )
            exportExecutor.exportClassJava(
                workspace = workspace,
                inventory = snapshot.inventory,
                request = request,
            )
        }

    override fun exportMethodSmali(
        workspace: WorkspaceContext,
        request: ExportMethodSmaliRequest,
    ): ExportResult {
        capabilityChecker.require(workspace, Operation.ExportSmali)
        val snapshot = store.loadSnapshot(workspace.workdir, workspace.activeTargetId)
            ?: throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.InvalidSnapshot,
                workdir = workspace.workdir,
                message = "Active target snapshot is missing: ${workspace.activeTargetId}",
            )
        return exportExecutor.exportMethodSmali(
            workspace = workspace,
            inventory = snapshot.inventory,
            request = request,
        )
    }

    private fun sortClassHits(hits: List<ClassHit>): List<ClassHit> =
        hits.sortedWith(
            compareBy<ClassHit>({ it.className }, { it.sourcePath.orEmpty() }, { it.sourceEntry.orEmpty() }),
        )

    private fun sortMethodHits(hits: List<MethodHit>): List<MethodHit> =
        hits.sortedWith(
            compareBy<MethodHit>(
                { it.className },
                { it.methodName },
                { it.descriptor },
                { it.sourcePath.orEmpty() },
                { it.sourceEntry.orEmpty() },
            ),
        )

    private fun sortFieldHits(hits: List<FieldHit>): List<FieldHit> =
        hits.sortedWith(
            compareBy<FieldHit>(
                { it.className },
                { it.fieldName },
                { it.descriptor },
                { it.sourcePath.orEmpty() },
                { it.sourceEntry.orEmpty() },
            ),
        )

    private fun sortMethodDetail(detail: MethodDetail): MethodDetail =
        MethodDetail(
            method = detail.method,
            usingFields = detail.usingFields?.sortedWith(
                compareBy<MethodFieldUsage>(
                    { it.usingType != FieldUsageType.Read },
                    { it.field.className },
                    { it.field.fieldName },
                    { it.field.descriptor },
                    { it.field.sourcePath.orEmpty() },
                    { it.field.sourceEntry.orEmpty() },
                ),
            ),
            callers = detail.callers?.let(::sortMethodHits),
            invokes = detail.invokes?.let(::sortMethodHits),
        )

    private fun <T> applyWindow(hits: List<T>, offset: Int, limit: Int?): List<T> {
        require(offset >= 0) { "offset must be non-negative" }
        require(limit == null || limit > 0) { "limit must be positive when specified" }
        if (offset >= hits.size) return emptyList()
        val toIndex = if (limit == null) hits.size else minOf(hits.size, offset + limit)
        return hits.subList(offset, toIndex)
    }
}
