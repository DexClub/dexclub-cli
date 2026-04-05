package io.github.dexclub.dexkit.result

data class MethodData(
    val descriptor: String,
    val name: String,
    val className: String,
    val paramTypeNames: List<String>,
    val returnTypeName: String,
    val modifiers: Int,
    val isConstructor: Boolean,
    val isStaticInitializer: Boolean,
)
