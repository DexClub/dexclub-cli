package io.github.dexclub.cli

import io.github.dexclub.core.api.shared.Services
import io.github.dexclub.core.api.shared.WorkspaceState
import io.github.dexclub.core.api.workspace.WorkspaceRef

internal class WorkspaceCommandAdapter(
    private val services: Services,
    private val workdirResolver: WorkdirResolver,
) {
    fun initialize(request: CliRequest.Init): CommandResult {
        val context = services.workspace.initialize(request.input)
        val status = services.workspace.loadStatus(WorkspaceRef(context.workdir))
        return CommandResult(
            payload = RenderPayload.Status(StatusView.from(context.workdir, status)),
            outputFormat = request.outputFormat,
            exitCode = exitCodeForStatus(status.state),
        )
    }

    fun switchTarget(request: CliRequest.Switch): CommandResult {
        val workspaceRef = workdirResolver.resolve(null)
        val switchedRef = services.workspace.switchTarget(workspaceRef, request.input)
        val status = services.workspace.loadStatus(switchedRef)
        return CommandResult(
            payload = RenderPayload.Status(StatusView.from(switchedRef.workdir, status)),
            outputFormat = request.outputFormat,
            exitCode = exitCodeForStatus(status.state),
        )
    }

    fun loadStatus(request: CliRequest.Status): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val status = services.workspace.loadStatus(workspaceRef)
        return CommandResult(
            payload = RenderPayload.Status(StatusView.from(workspaceRef.workdir, status)),
            outputFormat = request.outputFormat,
            exitCode = exitCodeForStatus(status.state),
        )
    }

    fun listTargets(request: CliRequest.Targets): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val targets = services.workspace.listTargets(workspaceRef)
        return CommandResult(
            payload = RenderPayload.Targets(targets.map(TargetSummaryView::from)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun gc(request: CliRequest.Gc): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(workspaceRef)
        val result = services.workspace.gc(workspace)
        return CommandResult(
            payload = RenderPayload.Gc(GcView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    private fun exitCodeForStatus(state: WorkspaceState): Int =
        when (state) {
            WorkspaceState.Healthy -> 0
            WorkspaceState.Degraded,
            WorkspaceState.Broken,
            -> 2
        }
}
