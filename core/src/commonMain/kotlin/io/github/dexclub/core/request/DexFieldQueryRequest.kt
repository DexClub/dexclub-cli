package io.github.dexclub.core.request

import io.github.dexclub.core.query.DexFieldQueryMatcher
import kotlinx.serialization.Serializable

@Serializable
data class DexFieldQueryRequest(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: DexFieldQueryMatcher? = null,
    var findFirst: Boolean = false,
) {
    fun matcher(init: DexFieldQueryMatcher.() -> Unit) {
        matcher = DexFieldQueryMatcher().apply(init)
    }
}
