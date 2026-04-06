package io.github.dexclub.core.config

data class SmaliRenderConfig(
    val autoUnicodeDecode: Boolean = true,
    val parameterRegisters: Boolean = true,
    val localsDirective: Boolean = true,
    val debugInfo: Boolean = true,
    val accessorComments: Boolean = true,
)
