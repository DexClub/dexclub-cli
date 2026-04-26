package io.github.dexclub.core.api.resource

import io.github.dexclub.core.api.workspace.WorkspaceContext

interface ResourceService {
    fun decodeManifest(workspace: WorkspaceContext): ManifestResult

    fun dumpResourceTable(workspace: WorkspaceContext): ResourceTableResult

    fun decodeXml(workspace: WorkspaceContext, request: DecodeXmlRequest): DecodedXmlResult

    fun listResourceEntries(workspace: WorkspaceContext): List<ResourceEntry>

    fun resolveResourceValue(workspace: WorkspaceContext, request: ResolveResourceRequest): ResourceValue

    fun findResourceEntries(
        workspace: WorkspaceContext,
        request: FindResourcesRequest,
    ): List<ResourceEntryValueHit>
}
