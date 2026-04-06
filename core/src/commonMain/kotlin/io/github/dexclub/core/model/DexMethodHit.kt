package io.github.dexclub.core.model

data class DexMethodHit(
    val className: String,
    val name: String,
    val descriptor: String,
    val sourceDexPath: String?,
)
