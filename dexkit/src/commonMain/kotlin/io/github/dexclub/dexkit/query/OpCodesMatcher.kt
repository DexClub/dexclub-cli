package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.IntRangeSerializer
import kotlinx.serialization.Serializable

@Serializable
data class OpCodesMatcher(
    val opCodes: MutableList<Int> = mutableListOf(),
    val opNames: MutableList<String> = mutableListOf(),
    var matchType: OpCodeMatchType = OpCodeMatchType.Contains,
    @Serializable(with = IntRangeSerializer::class)
    var size: IntRange? = null,
)
