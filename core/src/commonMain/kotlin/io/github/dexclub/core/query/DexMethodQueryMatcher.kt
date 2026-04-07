package io.github.dexclub.core.query

import kotlinx.serialization.Serializable

@Serializable
data class DexMethodQueryMatcher(
    var nameMatcher: DexStringMatcher? = null,
    var declaredClassMatcher: DexClassQueryMatcher? = null,
    var returnTypeMatcher: DexStringMatcher? = null,
    val paramTypeMatchers: MutableList<DexStringMatcher?> = mutableListOf(),
    var paramCount: Int? = null,
    val usingStringMatchers: MutableList<DexStringMatcher> = mutableListOf(),
    val opCodes: MutableList<Int> = mutableListOf(),
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

    fun returnType(
        value: String,
        matchType: DexStringMatchType = DexStringMatchType.Equals,
        ignoreCase: Boolean = false,
    ) {
        returnTypeMatcher = DexStringMatcher(value, matchType, ignoreCase)
    }

    fun returnType(matcher: DexStringMatcher) {
        returnTypeMatcher = matcher
    }

    fun paramTypes(vararg types: String?) {
        paramTypeMatchers.clear()
        types.forEach { paramTypeMatchers += it?.let { value -> DexStringMatcher(value, DexStringMatchType.Equals) } }
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

    fun addOpCode(opCode: Int) {
        opCodes += opCode
    }
}
