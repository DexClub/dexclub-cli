package io.github.dexclub.dexkit.result

import kotlinx.serialization.Serializable

@Serializable
data class FieldData(
    val descriptor: String,
    val name: String,
    val className: String,
    val typeName: String,
    val modifiers: Int,
)
