package io.github.dexclub.core.api.workspace

interface WorkspaceService {
    fun initialize(input: String): WorkspaceContext

    fun open(ref: WorkspaceRef): WorkspaceContext

    fun loadStatus(ref: WorkspaceRef): WorkspaceStatus

    fun gc(workspace: WorkspaceContext): GcResult

    fun inspect(workspace: WorkspaceContext): InspectResult
}
