package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class ParameterMatcher(
    var type: ClassMatcher? = null,
    var annotations: AnnotationsMatcher? = null,
)
