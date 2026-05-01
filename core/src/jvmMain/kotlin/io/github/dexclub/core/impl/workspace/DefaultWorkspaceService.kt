package io.github.dexclub.core.impl.workspace

import io.github.dexclub.core.api.workspace.GcResult
import io.github.dexclub.core.api.workspace.InspectResult
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.api.workspace.WorkspaceInitError
import io.github.dexclub.core.api.workspace.WorkspaceInitErrorReason
import io.github.dexclub.core.api.workspace.WorkspaceRef
import io.github.dexclub.core.api.workspace.WorkspaceResolveError
import io.github.dexclub.core.api.workspace.WorkspaceResolveErrorReason
import io.github.dexclub.core.api.workspace.WorkspaceService
import io.github.dexclub.core.api.workspace.WorkspaceStatus
import io.github.dexclub.core.api.workspace.TargetSummary
import io.github.dexclub.core.impl.workspace.runtime.WorkspaceInputResolver
import io.github.dexclub.core.impl.workspace.runtime.WorkspaceBootstrapper
import io.github.dexclub.core.impl.workspace.runtime.WorkspaceRuntimeResolver
import io.github.dexclub.core.impl.workspace.store.WorkspaceStore
import java.nio.file.Path
import java.time.Instant

internal class DefaultWorkspaceService(
    private val store: WorkspaceStore,
    private val bootstrapper: WorkspaceBootstrapper,
    private val runtimeResolver: WorkspaceRuntimeResolver,
    private val inputResolver: WorkspaceInputResolver,
    private val nowProvider: () -> Instant = { Instant.now() },
) : WorkspaceService {
    override fun initialize(input: String): WorkspaceContext {
        val prepared = bootstrapper.prepare(input)
        try {
            store.initialize(
                workdir = prepared.ref.workdir,
                workspace = prepared.workspace,
                target = prepared.target,
                snapshot = prepared.snapshot,
            )
        } catch (cause: IllegalStateException) {
            throw WorkspaceInitError(
                reason = WorkspaceInitErrorReason.StateWriteFailed,
                path = prepared.ref.workdir,
                message = "Failed to initialize workspace state: ${prepared.ref.workdir}",
                cause = cause,
            )
        }
        return runtimeResolver.open(prepared.ref)
    }

    override fun switchTarget(ref: WorkspaceRef, input: String): WorkspaceRef {
        val workdirPath = inputResolver.resolveRuntimeWorkdir(ref)
        val workdir = workdirPath.toString()
        val workspace = store.loadWorkspace(workdir)
        val inputPath = normalizeSwitchInput(workdirPath, input)
        val target = store.findTargetByInputPath(workdir, inputPath)
            ?: throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.TargetNotFound,
                workdir = workdir,
                message = "Target has not been initialized in this workspace: $inputPath",
            )
        store.saveWorkspace(
            workdir = workdir,
            workspace = workspace.copy(
                activeTargetId = target.targetId,
                updatedAt = nowProvider().toString(),
            ),
        )
        return WorkspaceRef(workdir)
    }

    override fun open(ref: WorkspaceRef): WorkspaceContext = runtimeResolver.open(ref)

    override fun listTargets(ref: WorkspaceRef): List<TargetSummary> {
        val workdirPath = inputResolver.resolveRuntimeWorkdir(ref)
        val workdir = workdirPath.toString()
        val workspace = store.loadWorkspace(workdir)
        return store.listTargets(workdir).map { it.toSummary(workspace.activeTargetId) }
    }

    override fun loadStatus(ref: WorkspaceRef): WorkspaceStatus = runtimeResolver.loadStatus(ref)

    override fun gc(workspace: WorkspaceContext): GcResult =
        store.clearTargetCache(workspace.workdir, workspace.activeTargetId)

    override fun inspect(workspace: WorkspaceContext): InspectResult =
        InspectResult(
            target = workspace.activeTarget,
            snapshot = workspace.snapshot,
            classCount = null,
        )

    private fun normalizeSwitchInput(workdirPath: Path, rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.InvalidInputPath,
                workdir = workdirPath.toString(),
                message = "Switch input is empty",
            )
        }
        val candidatePath = workdirPath.resolve(trimmed).normalize()
        val relativePath = runCatching { workdirPath.relativize(candidatePath) }.getOrNull()
            ?: throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.InvalidInputPath,
                workdir = workdirPath.toString(),
                message = "Switch input must stay within the current workspace: $trimmed",
            )
        return try {
            inputResolver.normalizeRelativePath(relativePath)
        } catch (_: WorkspaceInitError) {
            throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.InvalidInputPath,
                workdir = workdirPath.toString(),
                message = "Switch input must stay within the current workspace: $trimmed",
            )
        }
    }
}
