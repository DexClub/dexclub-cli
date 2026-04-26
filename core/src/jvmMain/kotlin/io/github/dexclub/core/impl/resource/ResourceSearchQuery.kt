package io.github.dexclub.core.impl.resource

internal data class ResourceSearchQuery(
    val type: String,
    val value: String,
    val contains: Boolean = false,
    val ignoreCase: Boolean = false,
)
