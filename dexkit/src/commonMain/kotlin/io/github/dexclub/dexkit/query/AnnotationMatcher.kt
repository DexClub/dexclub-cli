package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class AnnotationMatcher(
    var type: ClassMatcher? = null,
    var targetElementTypes: TargetElementTypesMatcher? = null,
    var policy: RetentionPolicyType? = null,
    var elements: AnnotationElementsMatcher? = null,
    val usingStrings: MutableList<StringMatcher> = mutableListOf(),
)
