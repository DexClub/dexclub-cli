package io.github.dexclub.core.query

import kotlinx.serialization.Serializable

@Serializable
enum class DexStringMatchType {
    Contains,
    StartsWith,
    EndsWith,
    Equals,
    SimilarRegex,
}

@Serializable
data class DexStringMatcher(
    val value: String,
    val matchType: DexStringMatchType = DexStringMatchType.Contains,
    val ignoreCase: Boolean = false,
)
