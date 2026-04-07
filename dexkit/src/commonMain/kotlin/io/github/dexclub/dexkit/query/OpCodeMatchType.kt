package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
enum class OpCodeMatchType {
    Contains,
    StartsWith,
    EndsWith,
    Equals,
}
