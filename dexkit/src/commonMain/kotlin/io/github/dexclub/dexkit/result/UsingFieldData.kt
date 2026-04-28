package io.github.dexclub.dexkit.result

import kotlinx.serialization.Serializable

@Serializable
data class UsingFieldData(
    val field: FieldData,
    val usingType: FieldUsingType,
)
