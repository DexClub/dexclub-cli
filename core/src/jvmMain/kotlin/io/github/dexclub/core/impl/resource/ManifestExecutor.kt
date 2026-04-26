package io.github.dexclub.core.impl.resource

import io.github.dexclub.core.api.resource.ManifestResult
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory

internal interface ManifestExecutor {
    fun decodeManifest(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
    ): ManifestResult
}
