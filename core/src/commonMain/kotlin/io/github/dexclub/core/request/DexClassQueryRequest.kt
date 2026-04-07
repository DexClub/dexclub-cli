package io.github.dexclub.core.request

import io.github.dexclub.dexkit.query.ClassMatcher
import kotlinx.serialization.Serializable

@Serializable
data class DexClassQueryRequest(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: ClassMatcher? = null,
    var findFirst: Boolean = false,
)
