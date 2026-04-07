package io.github.dexclub.dexkit.result

import kotlinx.serialization.Serializable

@Serializable
data class ClassData(
    val descriptor: String,
    val name: String,
    val simpleName: String,
    val sourceFile: String,
    val modifiers: Int,
)
