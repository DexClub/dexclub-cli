package io.github.dexclub.core.impl.workspace

import io.github.dexclub.core.api.workspace.GcResult
import io.github.dexclub.core.api.workspace.InspectResult
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.api.workspace.WorkspaceInitError
import io.github.dexclub.core.api.workspace.WorkspaceInitErrorReason
import io.github.dexclub.core.api.workspace.WorkspaceRef
import io.github.dexclub.core.api.workspace.WorkspaceService
import io.github.dexclub.core.api.workspace.WorkspaceStatus
import io.github.dexclub.core.impl.workspace.runtime.WorkspaceBootstrapper
import io.github.dexclub.core.impl.workspace.runtime.WorkspaceRuntimeResolver
import io.github.dexclub.core.impl.workspace.store.WorkspaceStore

internal class DefaultWorkspaceService(
    private val store: WorkspaceStore,
    private val bootstrapper: WorkspaceBootstrapper,
    private val runtimeResolver: WorkspaceRuntimeResolver,
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

    override fun open(ref: WorkspaceRef): WorkspaceContext = runtimeResolver.open(ref)

    override fun loadStatus(ref: WorkspaceRef): WorkspaceStatus = runtimeResolver.loadStatus(ref)

    override fun gc(workspace: WorkspaceContext): GcResult =
        store.clearTargetCache(workspace.workdir, workspace.activeTargetId)

    override fun inspect(workspace: WorkspaceContext): InspectResult =
        InspectResult(
            target = workspace.activeTarget,
            snapshot = workspace.snapshot,
            classCount = null,
        )
}
