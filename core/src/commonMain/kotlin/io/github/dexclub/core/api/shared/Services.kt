package io.github.dexclub.core.api.shared

import io.github.dexclub.core.api.dex.DexAnalysisService
import io.github.dexclub.core.api.resource.ResourceService
import io.github.dexclub.core.api.workspace.WorkspaceService

data class Services(
    val workspace: WorkspaceService,
    val dex: DexAnalysisService,
    val resource: ResourceService,
)

expect fun createDefaultServices(): Services
