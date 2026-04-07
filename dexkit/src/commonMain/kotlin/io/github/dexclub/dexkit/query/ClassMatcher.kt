package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class ClassMatcher(
    var source: StringMatcher? = null,
    var className: StringMatcher? = null,
    var modifiers: AccessFlagsMatcher? = null,
    var superClass: ClassMatcher? = null,
    var interfaces: InterfacesMatcher? = null,
    var annotations: AnnotationsMatcher? = null,
    var fields: FieldsMatcher? = null,
    var methods: MethodsMatcher? = null,
    val usingStrings: MutableList<StringMatcher> = mutableListOf(),
)
