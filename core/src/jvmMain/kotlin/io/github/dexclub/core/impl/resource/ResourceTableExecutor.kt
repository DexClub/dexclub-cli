package io.github.dexclub.core.impl.resource

import io.github.dexclub.core.api.resource.ResourceTableResult
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory

internal interface ResourceTableExecutor {
    fun dumpResourceTable(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
    ): ResourceTableResult
}
