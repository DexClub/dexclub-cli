package io.github.dexclub.core.request

import io.github.dexclub.dexkit.query.MethodMatcher
import kotlinx.serialization.Serializable

@Serializable
data class DexMethodQueryRequest(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: MethodMatcher? = null,
    var findFirst: Boolean = false,
)
