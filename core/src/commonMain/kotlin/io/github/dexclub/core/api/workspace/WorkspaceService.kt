package io.github.dexclub.core.api.workspace

interface WorkspaceService {
    fun initialize(input: String): WorkspaceContext

    fun switchTarget(ref: WorkspaceRef, input: String): WorkspaceRef

    fun open(ref: WorkspaceRef): WorkspaceContext

    fun listTargets(ref: WorkspaceRef): List<TargetSummary>

    fun loadStatus(ref: WorkspaceRef): WorkspaceStatus

    fun gc(workspace: WorkspaceContext): GcResult

    fun inspect(workspace: WorkspaceContext): InspectResult
}
