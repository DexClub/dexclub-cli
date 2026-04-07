package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.IntRangeSerializer
import kotlinx.serialization.Serializable

@Serializable
data class InterfacesMatcher(
    val interfaces: MutableList<ClassMatcher> = mutableListOf(),
    var matchType: MatchType = MatchType.Contains,
    @Serializable(with = IntRangeSerializer::class)
    var count: IntRange? = null,
)
