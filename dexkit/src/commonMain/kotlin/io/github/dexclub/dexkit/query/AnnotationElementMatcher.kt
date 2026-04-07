package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class AnnotationElementMatcher(
    var name: StringMatcher? = null,
    var value: AnnotationEncodeValueMatcher? = null,
)
