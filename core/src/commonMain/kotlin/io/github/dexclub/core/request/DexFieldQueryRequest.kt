package io.github.dexclub.core.request

import io.github.dexclub.dexkit.query.FieldMatcher
import kotlinx.serialization.Serializable

@Serializable
data class DexFieldQueryRequest(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: FieldMatcher? = null,
    var findFirst: Boolean = false,
)
