package io.github.dexclub.core.config

data class JavaDecompileConfig(
    val useDxInput: Boolean = true,
    val renameValid: Boolean = false,
    val renameCaseSensitive: Boolean = true,
    val showInconsistentCode: Boolean = false,
    val debugInfo: Boolean = false,
    val moveInnerClasses: Boolean = false,
    val inlineAnonymousClasses: Boolean = false,
)
