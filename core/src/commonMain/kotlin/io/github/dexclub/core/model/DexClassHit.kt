package io.github.dexclub.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DexClassHit(
    val name: String,
    val descriptor: String,
    val sourceDexPath: String?,
)
