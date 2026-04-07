package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class TargetElementTypesMatcher(
    val types: MutableList<TargetElementType> = mutableListOf(),
    var matchType: MatchType = MatchType.Contains,
)
