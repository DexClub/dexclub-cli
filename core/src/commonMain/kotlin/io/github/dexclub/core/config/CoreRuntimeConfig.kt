package io.github.dexclub.core.config

data class CoreRuntimeConfig(
    val dexFormat: DexFormatConfig = DexFormatConfig(),
    val dexKit: DexKitRuntimeConfig = DexKitRuntimeConfig(),
    val javaDecompile: JavaDecompileConfig = JavaDecompileConfig(),
    val smaliRender: SmaliRenderConfig = SmaliRenderConfig(),
)
