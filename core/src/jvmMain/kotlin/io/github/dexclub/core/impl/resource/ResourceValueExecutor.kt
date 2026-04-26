package io.github.dexclub.core.impl.resource

import io.github.dexclub.core.api.resource.ResolveResourceRequest
import io.github.dexclub.core.api.resource.FindResourcesRequest
import io.github.dexclub.core.api.resource.ResourceEntryValueHit
import io.github.dexclub.core.api.resource.ResourceValue
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory

internal interface ResourceValueExecutor {
    fun resolveResourceValue(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ResolveResourceRequest,
    ): ResourceValue

    fun findResourceEntries(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: FindResourcesRequest,
    ): List<ResourceEntryValueHit>
}
