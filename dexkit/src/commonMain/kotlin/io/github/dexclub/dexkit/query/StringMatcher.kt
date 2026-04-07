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

fun String.matchExact(ignoreCase: Boolean = false) = StringMatcher(this, StringMatchType.Equals, ignoreCase)
fun String.matchContains(ignoreCase: Boolean = false) = StringMatcher(this, StringMatchType.Contains, ignoreCase)
fun String.matchStartsWith(ignoreCase: Boolean = false) = StringMatcher(this, StringMatchType.StartsWith, ignoreCase)
fun String.matchEndsWith(ignoreCase: Boolean = false) = StringMatcher(this, StringMatchType.EndsWith, ignoreCase)
fun String.matchSimilar() = StringMatcher(this, StringMatchType.SimilarRegex)
