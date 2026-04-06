package io.github.dexclub.core.model

data class DexArchiveInfo(
    val kind: DexInputKind,
    val inputs: List<DexInputRef>,
    val dexCount: Int,
    val classCount: Int?,
)
