package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class UsingFieldMatcher(
    var field: FieldMatcher? = null,
    var usingType: UsingType = UsingType.Any,
)
