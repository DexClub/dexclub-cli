package io.github.dexclub.dexkit.query

class FieldMatcher {

    var nameMatcher: StringMatcher? = null
    var declaredClassMatcher: ClassMatcher? = null
    var typeMatcher: StringMatcher? = null
    val annotationMatchers: MutableList<StringMatcher> = mutableListOf()
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

    fun type(value: String, matchType: StringMatchType = StringMatchType.Equals, ignoreCase: Boolean = false) {
        typeMatcher = StringMatcher(value, matchType, ignoreCase)
    }

    fun type(matcher: StringMatcher) {
        typeMatcher = matcher
    }

    fun addAnnotation(value: String, matchType: StringMatchType = StringMatchType.Equals, ignoreCase: Boolean = false) {
        annotationMatchers += StringMatcher(value, matchType, ignoreCase)
    }
}
