package io.github.dexclub.core.query

import io.github.dexclub.core.CoreIntRangeSerializer
import kotlinx.serialization.Serializable

@Serializable
data class DexClassQueryMatcher(
    var classNameMatcher: DexStringMatcher? = null,
    var superClassMatcher: DexClassQueryMatcher? = null,
    val interfaceMatchers: MutableList<DexStringMatcher> = mutableListOf(),
    val usingStringMatchers: MutableList<DexStringMatcher> = mutableListOf(),
    val annotationMatchers: MutableList<DexStringMatcher> = mutableListOf(),
    @Serializable(with = CoreIntRangeSerializer::class)
    var methodCountRange: IntRange? = null,
    @Serializable(with = CoreIntRangeSerializer::class)
    var fieldCountRange: IntRange? = null,
    var modifiers: Int? = null,
) {
    fun className(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false,
    ) {
        classNameMatcher = DexStringMatcher(value, matchType, ignoreCase)
    }

    fun className(matcher: DexStringMatcher) {
        classNameMatcher = matcher
    }

    fun superClass(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false,
    ) {
        superClassMatcher = DexClassQueryMatcher().also { it.className(value, matchType, ignoreCase) }
    }

    fun superClass(init: DexClassQueryMatcher.() -> Unit) {
        superClassMatcher = DexClassQueryMatcher().apply(init)
    }

    fun addInterface(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false,
    ) {
        interfaceMatchers += DexStringMatcher(value, matchType, ignoreCase)
    }

    fun usingStrings(vararg values: String) {
        values.forEach { usingStringMatchers += DexStringMatcher(it) }
    }

    fun addUsingString(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Contains,
        ignoreCase: Boolean = false,
    ) {
        usingStringMatchers += DexStringMatcher(value, matchType, ignoreCase)
    }

    fun addUsingString(matcher: DexStringMatcher) {
        usingStringMatchers += matcher
    }

    fun addAnnotation(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false,
    ) {
        annotationMatchers += DexStringMatcher(value, matchType, ignoreCase)
    }

    fun methodCount(count: Int) {
        methodCountRange = count..count
    }

    fun methodCount(min: Int = 0, max: Int = Int.MAX_VALUE) {
        methodCountRange = min..max
    }

    fun fieldCount(count: Int) {
        fieldCountRange = count..count
    }

    fun fieldCount(min: Int = 0, max: Int = Int.MAX_VALUE) {
        fieldCountRange = min..max
    }
}
