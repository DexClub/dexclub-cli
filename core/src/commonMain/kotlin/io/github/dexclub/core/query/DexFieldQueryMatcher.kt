package io.github.dexclub.core.query

import kotlinx.serialization.Serializable

@Serializable
data class DexFieldQueryMatcher(
    var nameMatcher: DexStringMatcher? = null,
    var declaredClassMatcher: DexClassQueryMatcher? = null,
    var typeMatcher: DexStringMatcher? = null,
    val annotationMatchers: MutableList<DexStringMatcher> = mutableListOf(),
    var modifiers: Int? = null,
) {
    fun name(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false,
    ) {
        nameMatcher = DexStringMatcher(value, matchType, ignoreCase)
    }

    fun name(matcher: DexStringMatcher) {
        nameMatcher = matcher
    }

    fun declaredClass(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false,
    ) {
        declaredClassMatcher = DexClassQueryMatcher().also { it.className(value, matchType, ignoreCase) }
    }

    fun declaredClass(init: DexClassQueryMatcher.() -> Unit) {
        declaredClassMatcher = DexClassQueryMatcher().apply(init)
    }

    fun type(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false,
    ) {
        typeMatcher = DexStringMatcher(value, matchType, ignoreCase)
    }

    fun type(matcher: DexStringMatcher) {
        typeMatcher = matcher
    }

    fun addAnnotation(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false,
    ) {
        annotationMatchers += DexStringMatcher(value, matchType, ignoreCase)
    }
}
