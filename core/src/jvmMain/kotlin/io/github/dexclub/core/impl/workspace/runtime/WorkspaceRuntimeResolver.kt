package io.github.dexclub.core.impl.workspace.runtime

import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.api.workspace.WorkspaceRef
import io.github.dexclub.core.api.workspace.WorkspaceStatus
import io.github.dexclub.core.api.workspace.TargetSnapshotSummary

internal interface WorkspaceRuntimeResolver {
    fun open(ref: WorkspaceRef): WorkspaceContext

    fun loadStatus(ref: WorkspaceRef): WorkspaceStatus

    fun refreshSnapshot(workspace: WorkspaceContext): TargetSnapshotSummary
}
