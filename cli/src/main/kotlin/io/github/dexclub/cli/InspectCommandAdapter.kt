package io.github.dexclub.cli

import io.github.dexclub.core.api.dex.InspectMethodRequest
import io.github.dexclub.core.api.shared.Services

internal class InspectCommandAdapter(
    private val services: Services,
    private val workdirResolver: WorkdirResolver,
) {
    fun inspect(request: CliRequest.Inspect): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(workspaceRef)
        val result = services.workspace.inspect(workspace)
        return CommandResult(
            payload = RenderPayload.Inspect(InspectView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun inspectMethod(request: CliRequest.InspectMethod): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(workspaceRef)
        val result = services.dex.inspectMethod(
            workspace = workspace,
            request = InspectMethodRequest(
                descriptor = request.descriptor,
                includes = request.includes,
            ),
        )
        return CommandResult(
            payload = RenderPayload.MethodDetail(MethodDetailView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }
}
