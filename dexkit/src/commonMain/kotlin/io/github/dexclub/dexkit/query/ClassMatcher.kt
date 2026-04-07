package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.IntRangeSerializer
import kotlinx.serialization.Serializable

@Serializable
class ClassMatcher(
    var classNameMatcher: StringMatcher? = null,
    var superClassMatcher: ClassMatcher? = null,
    val interfaceMatchers: MutableList<StringMatcher> = mutableListOf(),
    val usingStringMatchers: MutableList<StringMatcher> = mutableListOf(),
    val annotationMatchers: MutableList<StringMatcher> = mutableListOf(),
    @Serializable(with = IntRangeSerializer::class)
    var methodCountRange: IntRange? = null,
    @Serializable(with = IntRangeSerializer::class)
    var fieldCountRange: IntRange? = null,
    var modifiers: Int? = null,
) {

    fun className(value: String, matchType: StringMatchType = StringMatchType.Equals, ignoreCase: Boolean = false) {
        classNameMatcher = StringMatcher(value, matchType, ignoreCase)
    }

    fun className(matcher: StringMatcher) {
        classNameMatcher = matcher
    }

    fun superClass(value: String, matchType: StringMatchType = StringMatchType.Equals, ignoreCase: Boolean = false) {
        superClassMatcher = ClassMatcher().also { it.className(value, matchType, ignoreCase) }
    }

    fun superClass(init: ClassMatcher.() -> Unit) {
        superClassMatcher = ClassMatcher().apply(init)
    }

    fun addInterface(value: String, matchType: StringMatchType = StringMatchType.Equals, ignoreCase: Boolean = false) {
        interfaceMatchers += StringMatcher(value, matchType, ignoreCase)
    }

    fun usingStrings(vararg values: String) {
        values.forEach { usingStringMatchers += StringMatcher(it) }
    }

    fun addUsingString(value: String, matchType: StringMatchType = StringMatchType.Contains, ignoreCase: Boolean = false) {
        usingStringMatchers += StringMatcher(value, matchType, ignoreCase)
    }

    fun addUsingString(matcher: StringMatcher) {
        usingStringMatchers += matcher
    }

    fun addAnnotation(value: String, matchType: StringMatchType = StringMatchType.Equals, ignoreCase: Boolean = false) {
        annotationMatchers += StringMatcher(value, matchType, ignoreCase)
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
