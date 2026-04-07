package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.IntRangeSerializer
import kotlinx.serialization.Serializable

@Serializable
data class ParametersMatcher(
    val params: MutableList<ParameterMatcher?> = mutableListOf(),
    @Serializable(with = IntRangeSerializer::class)
    var count: IntRange? = null,
)
