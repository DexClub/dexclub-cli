package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class FieldMatcher(
    var name: StringMatcher? = null,
    var modifiers: AccessFlagsMatcher? = null,
    var declaredClass: ClassMatcher? = null,
    var type: ClassMatcher? = null,
    var annotations: AnnotationsMatcher? = null,
    var readMethods: MethodsMatcher? = null,
    var writeMethods: MethodsMatcher? = null,
)
