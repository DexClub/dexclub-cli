package io.github.dexclub.core.api.workspace

data class GcResult(
    val workdir: String,
    val targetId: String,
    val deletedFiles: Int,
    val deletedBytes: Long,
)

data class InspectResult(
    val target: TargetHandle,
    val snapshot: TargetSnapshotSummary,
    val classCount: Int? = null,
)
