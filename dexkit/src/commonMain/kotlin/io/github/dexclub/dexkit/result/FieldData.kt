package io.github.dexclub.dexkit.result

data class FieldData(
    val descriptor: String,
    val name: String,
    val className: String,
    val typeName: String,
    val modifiers: Int,
)
