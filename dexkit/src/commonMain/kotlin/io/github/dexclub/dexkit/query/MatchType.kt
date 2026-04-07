package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
enum class MatchType {
    Contains,
    Equals,
}
