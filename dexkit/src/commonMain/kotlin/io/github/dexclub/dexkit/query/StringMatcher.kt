package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
enum class StringMatchType {
    Contains,
    StartsWith,
    EndsWith,
    Equals,
    SimilarRegex,
}

@Serializable
data class StringMatcher(
    val value: String,
    val matchType: StringMatchType = StringMatchType.Contains,
    val ignoreCase: Boolean = false,
)
