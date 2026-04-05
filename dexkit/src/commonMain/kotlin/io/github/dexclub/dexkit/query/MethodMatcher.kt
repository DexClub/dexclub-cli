package io.github.dexclub.dexkit.query

class MethodMatcher {

    var nameMatcher: StringMatcher? = null
    var declaredClassMatcher: ClassMatcher? = null
    var returnTypeMatcher: StringMatcher? = null
    val paramTypeMatchers: MutableList<StringMatcher?> = mutableListOf()
    var paramCount: Int? = null
    val usingStringMatchers: MutableList<StringMatcher> = mutableListOf()
    val opCodes: MutableList<Int> = mutableListOf()
    var modifiers: Int? = null

    fun name(value: String, matchType: StringMatchType = StringMatchType.Equals, ignoreCase: Boolean = false) {
        nameMatcher = StringMatcher(value, matchType, ignoreCase)
    }

    fun name(matcher: StringMatcher) {
        nameMatcher = matcher
    }

    fun declaredClass(value: String, matchType: StringMatchType = StringMatchType.Equals, ignoreCase: Boolean = false) {
        declaredClassMatcher = ClassMatcher().also { it.className(value, matchType, ignoreCase) }
    }

    fun declaredClass(init: ClassMatcher.() -> Unit) {
        declaredClassMatcher = ClassMatcher().apply(init)
    }

    fun returnType(value: String, matchType: StringMatchType = StringMatchType.Equals, ignoreCase: Boolean = false) {
        returnTypeMatcher = StringMatcher(value, matchType, ignoreCase)
    }

    fun returnType(matcher: StringMatcher) {
        returnTypeMatcher = matcher
    }

    fun paramTypes(vararg types: String?) {
        paramTypeMatchers.clear()
        types.forEach { paramTypeMatchers += it?.let { t -> StringMatcher(t, StringMatchType.Equals) } }
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

    fun addOpCode(opCode: Int) {
        opCodes += opCode
    }
}
