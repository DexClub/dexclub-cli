package io.github.dexclub.cli

import io.github.dexclub.core.api.resource.DecodeXmlRequest
import io.github.dexclub.core.api.resource.ResolveResourceRequest
import io.github.dexclub.core.api.shared.Services

internal class ResourceCommandAdapter(
    private val services: Services,
    private val queryTextLoader: QueryTextLoader,
    private val workdirResolver: WorkdirResolver,
) {
    fun manifest(request: CliRequest.Manifest): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(workspaceRef)
        val result = services.resource.decodeManifest(workspace)
        return CommandResult(
            payload = RenderPayload.Manifest(ManifestView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun resTable(request: CliRequest.ResTable): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(workspaceRef)
        val result = services.resource.dumpResourceTable(workspace)
        return CommandResult(
            payload = RenderPayload.ResourceTable(ResourceTableView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun decodeXml(request: CliRequest.DecodeXml): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(workspaceRef)
        val result = services.resource.decodeXml(
            workspace,
            DecodeXmlRequest(path = request.path),
        )
        return CommandResult(
            payload = RenderPayload.DecodedXml(DecodedXmlView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun listRes(request: CliRequest.ListRes): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(workspaceRef)
        val result = services.resource.listResourceEntries(workspace)
        return CommandResult(
            payload = RenderPayload.ResourceEntries(result.map(ResourceEntryView::from)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun resolveRes(request: CliRequest.ResolveRes): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(workspaceRef)
        val result = services.resource.resolveResourceValue(
            workspace,
            ResolveResourceRequest(
                resourceId = request.resourceId,
                type = request.type,
                name = request.name,
            ),
        )
        return CommandResult(
            payload = RenderPayload.ResourceValue(ResourceValueView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun findRes(request: CliRequest.FindRes): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(workspaceRef)
        val queryText = queryTextLoader.load(request.query, CliUsages.findRes)
        val result = services.resource.findResourceEntries(
            workspace,
            io.github.dexclub.core.api.resource.FindResourcesRequest(
                queryText = queryText,
                window = request.window,
            ),
        )
        return CommandResult(
            payload = RenderPayload.ResourceValueHits(result.map(ResourceEntryValueHitView::from)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }
}
