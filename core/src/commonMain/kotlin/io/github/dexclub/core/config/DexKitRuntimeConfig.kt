package io.github.dexclub.core.config

data class DexKitRuntimeConfig(
    val threadCount: Int? = null,
    val initFullCache: Boolean = false,
)
