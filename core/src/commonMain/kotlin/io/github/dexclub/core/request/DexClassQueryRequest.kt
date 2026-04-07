package io.github.dexclub.core.request

import io.github.dexclub.core.query.DexClassQueryMatcher
import kotlinx.serialization.Serializable

@Serializable
data class DexClassQueryRequest(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: DexClassQueryMatcher? = null,
    var findFirst: Boolean = false,
) {
    fun matcher(init: DexClassQueryMatcher.() -> Unit) {
        matcher = DexClassQueryMatcher().apply(init)
    }
}
