package io.github.dexclub.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DexFieldHit(
    val className: String,
    val name: String,
    val descriptor: String,
    val sourceDexPath: String?,
)
