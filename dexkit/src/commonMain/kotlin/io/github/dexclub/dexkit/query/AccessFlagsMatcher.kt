package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class AccessFlagsMatcher(
    var modifiers: Int,
    var matchType: MatchType = MatchType.Contains,
)
