package io.github.dexclub.core.api.workspace

import io.github.dexclub.core.api.shared.CacheState
import io.github.dexclub.core.api.shared.CapabilitySet
import io.github.dexclub.core.api.shared.InputType
import io.github.dexclub.core.api.shared.InventoryCounts
import io.github.dexclub.core.api.shared.WorkspaceIssue
import io.github.dexclub.core.api.shared.WorkspaceKind
import io.github.dexclub.core.api.shared.WorkspaceState

data class WorkspaceRef(
    val workdir: String,
)

data class TargetHandle(
    val targetId: String,
    val inputType: InputType,
    val inputPath: String,
)

data class TargetSnapshotSummary(
    val kind: WorkspaceKind,
    val inventoryFingerprint: String,
    val contentFingerprint: String,
    val capabilities: CapabilitySet,
    val inventoryCounts: InventoryCounts,
)

data class WorkspaceContext(
    val workdir: String,
    val dexclubDir: String,
    val workspaceId: String,
    val activeTargetId: String,
    val activeTarget: TargetHandle,
    val snapshot: TargetSnapshotSummary,
)

data class WorkspaceStatus(
    val workspaceId: String,
    val activeTargetId: String,
    val state: WorkspaceState,
    val issues: List<WorkspaceIssue>,
    val activeTarget: TargetHandle,
    val snapshot: TargetSnapshotSummary?,
    val cacheState: CacheState,
)
