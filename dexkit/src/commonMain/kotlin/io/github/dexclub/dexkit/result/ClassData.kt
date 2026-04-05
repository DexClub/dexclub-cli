package io.github.dexclub.dexkit.result

data class ClassData(
    val descriptor: String,
    val name: String,
    val simpleName: String,
    val sourceFile: String,
    val modifiers: Int,
)
