package io.github.dexclub.core.impl.resource

import io.github.dexclub.core.api.resource.ResourceEntry
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory

internal interface ResourceEntryListExecutor {
    fun listResourceEntries(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
    ): List<ResourceEntry>
}
