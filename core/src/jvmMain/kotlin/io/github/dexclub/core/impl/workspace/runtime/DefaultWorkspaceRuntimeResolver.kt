package io.github.dexclub.core.impl.workspace.runtime

import io.github.dexclub.core.api.shared.CacheState
import io.github.dexclub.core.api.shared.InputType
import io.github.dexclub.core.api.shared.WorkspaceIssue
import io.github.dexclub.core.api.shared.WorkspaceIssueSeverity
import io.github.dexclub.core.api.shared.WorkspaceState
import io.github.dexclub.core.api.workspace.TargetHandle
import io.github.dexclub.core.api.workspace.TargetSnapshotSummary
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.api.workspace.WorkspaceRef
import io.github.dexclub.core.api.workspace.WorkspaceResolveError
import io.github.dexclub.core.api.workspace.WorkspaceResolveErrorReason
import io.github.dexclub.core.api.workspace.WorkspaceStatus
import io.github.dexclub.core.impl.workspace.model.SnapshotRecord
import io.github.dexclub.core.impl.workspace.model.TargetRecord
import io.github.dexclub.core.impl.workspace.model.toContext
import io.github.dexclub.core.impl.workspace.store.WorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

internal class DefaultWorkspaceRuntimeResolver(
    private val store: WorkspaceStore,
    private val inputResolver: WorkspaceInputResolver,
    private val inventoryScanner: InventoryScanner,
    private val snapshotBuilder: SnapshotBuilder,
    private val toolVersion: String = "dev",
    private val nowProvider: () -> Instant = { Instant.now() },
) : WorkspaceRuntimeResolver {
    override fun open(ref: WorkspaceRef): WorkspaceContext {
        val workdirPath = inputResolver.resolveRuntimeWorkdir(ref)
        val workdir = workdirPath.toString()
        ensureInitialized(workdir)
        val workspace = normalizeWorkspaceRecord(workdir, store.loadWorkspace(workdir))
        if (workspace.activeTargetId.isBlank()) {
            throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.MissingActiveTarget,
                workdir = workdir,
                message = "Workspace active target is missing: $workdir",
            )
        }
        val target = store.loadTarget(workdir, workspace.activeTargetId)
        val snapshot = refreshSnapshotRecord(workdirPath, target)
        return workspace.toContext(
            workdir = workdir,
            dexclubDir = store.dexclubDir(workdir),
            target = target,
            snapshot = snapshot,
        )
    }

    override fun loadStatus(ref: WorkspaceRef): WorkspaceStatus {
        val workdirPath = inputResolver.resolveRuntimeWorkdir(ref)
        val workdir = workdirPath.toString()
        ensureInitialized(workdir)
        val issues = mutableListOf<WorkspaceIssue>()

        val workspace = runCatching { store.loadWorkspace(workdir) }.getOrElse { error ->
            issues += issue("invalid_workspace_metadata", WorkspaceIssueSeverity.Error, error.message ?: "Workspace metadata is invalid")
            return status(
                workspaceId = "",
                activeTargetId = "",
                issues = issues,
                activeTarget = fallbackTargetHandle(null),
                snapshot = null,
                cacheState = CacheState.Missing,
            )
        }

        val target = runCatching { store.loadTarget(workdir, workspace.activeTargetId) }.getOrElse { error ->
            issues += issue("invalid_target_metadata", WorkspaceIssueSeverity.Error, error.message ?: "Target metadata is invalid")
            return status(
                workspaceId = workspace.workspaceId,
                activeTargetId = workspace.activeTargetId,
                issues = issues,
                activeTarget = fallbackTargetHandle(workspace.activeTargetId),
                snapshot = null,
                cacheState = detectCacheState(workdirPath, workspace.activeTargetId),
            )
        }

        val storedSnapshot = runCatching { store.loadSnapshot(workdir, target.targetId) }.getOrElse { error ->
            issues += issue("invalid_snapshot", WorkspaceIssueSeverity.Warning, error.message ?: "Snapshot is invalid")
            null
        }

        val currentSnapshot = runCatching {
            buildCurrentSnapshot(workdirPath, target)
        }.getOrElse { error ->
            when (error) {
                is WorkspaceResolveError -> {
                    val code = when (error.reason) {
                        WorkspaceResolveErrorReason.MissingBoundInput -> "missing_input"
                        WorkspaceResolveErrorReason.UnrecognizedMaterials -> "unrecognized_materials"
                        else -> "workspace_error"
                    }
                    issues += issue(code, WorkspaceIssueSeverity.Error, error.message ?: "Workspace binding is invalid")
                }

                else -> issues += issue("workspace_error", WorkspaceIssueSeverity.Error, error.message ?: "Workspace binding is invalid")
            }
            null
        }

        val cacheState = detectCacheState(workdirPath, target.targetId)
        if (cacheState != CacheState.Present) {
            val code = when (cacheState) {
                CacheState.Partial -> "partial_cache"
                CacheState.Missing -> "missing_cache"
                CacheState.Present -> error("unreachable")
            }
            issues += issue(code, WorkspaceIssueSeverity.Warning, "Workspace cache is $cacheState")
        }

        if (storedSnapshot == null && currentSnapshot != null) {
            issues += issue("missing_snapshot", WorkspaceIssueSeverity.Warning, "Workspace snapshot is missing")
        } else if (storedSnapshot != null && currentSnapshot != null && storedSnapshot.toSummary() != currentSnapshot.toSummary()) {
            issues += issue("stale_snapshot", WorkspaceIssueSeverity.Warning, "Workspace snapshot is out of date")
        }

        return status(
            workspaceId = workspace.workspaceId,
            activeTargetId = workspace.activeTargetId,
            issues = issues,
            activeTarget = target.toHandle(),
            snapshot = currentSnapshot?.toSummary() ?: storedSnapshot?.toSummary(),
            cacheState = cacheState,
        )
    }

    override fun refreshSnapshot(workspace: WorkspaceContext): TargetSnapshotSummary {
        val workdirPath = inputResolver.resolveRuntimeWorkdir(WorkspaceRef(workspace.workdir))
        val target = store.loadTarget(workspace.workdir, workspace.activeTargetId)
        val snapshot = refreshSnapshotRecord(workdirPath, target)
        return snapshot.toSummary()
    }

    private fun buildCurrentSnapshot(workdirPath: Path, target: TargetRecord): SnapshotRecord {
        val inventory = inventoryScanner.scan(workdirPath, target)
        if (inventory.isEmpty()) {
            throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.UnrecognizedMaterials,
                workdir = workdirPath.toString(),
                message = "No recognizable materials found for active target: ${target.inputPath}",
            )
        }
        return snapshotBuilder.build(workdirPath, target, inventory)
    }

    private fun normalizeWorkspaceRecord(workdir: String, workspace: io.github.dexclub.core.impl.workspace.model.WorkspaceRecord):
        io.github.dexclub.core.impl.workspace.model.WorkspaceRecord {
        if (workspace.toolVersion == toolVersion) {
            return workspace
        }
        val normalized = workspace.copy(
            toolVersion = toolVersion,
            updatedAt = nowProvider().toString(),
        )
        store.saveWorkspace(workdir, normalized)
        return normalized
    }

    private fun refreshSnapshotRecord(workdirPath: Path, target: TargetRecord): SnapshotRecord {
        val snapshot = buildCurrentSnapshot(workdirPath, target)
        store.saveSnapshot(workdirPath.toString(), target.targetId, snapshot)
        return snapshot
    }

    private fun ensureInitialized(workdir: String) {
        if (!store.exists(workdir)) {
            throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.NotInitialized,
                workdir = workdir,
                message = "Target workspace is not initialized: $workdir",
            )
        }
    }

    private fun detectCacheState(workdirPath: Path, targetId: String): CacheState {
        val cacheRoot = workdirPath.resolve(".dexclub/targets").resolve(targetId).resolve("cache")
        val decodedDir = cacheRoot.resolve("decoded")
        val indexesDir = cacheRoot.resolve("indexes")
        val decodedPresent = Files.isDirectory(decodedDir)
        val indexesPresent = Files.isDirectory(indexesDir)
        return when {
            decodedPresent && indexesPresent -> CacheState.Present
            decodedPresent || indexesPresent -> CacheState.Partial
            else -> CacheState.Missing
        }
    }

    private fun fallbackTargetHandle(targetId: String?): TargetHandle =
        TargetHandle(
            targetId = targetId ?: "",
            inputType = InputType.File,
            inputPath = "",
        )

    private fun issue(code: String, severity: WorkspaceIssueSeverity, message: String): WorkspaceIssue =
        WorkspaceIssue(
            code = code,
            severity = severity,
            message = message,
        )

    private fun status(
        workspaceId: String,
        activeTargetId: String,
        issues: List<WorkspaceIssue>,
        activeTarget: TargetHandle,
        snapshot: TargetSnapshotSummary?,
        cacheState: CacheState,
    ): WorkspaceStatus =
        WorkspaceStatus(
            workspaceId = workspaceId,
            activeTargetId = activeTargetId,
            state = when {
                issues.any { it.severity == WorkspaceIssueSeverity.Error } -> WorkspaceState.Broken
                issues.any { it.severity == WorkspaceIssueSeverity.Warning } -> WorkspaceState.Degraded
                else -> WorkspaceState.Healthy
            },
            issues = issues,
            activeTarget = activeTarget,
            snapshot = snapshot,
            cacheState = cacheState,
        )
}
