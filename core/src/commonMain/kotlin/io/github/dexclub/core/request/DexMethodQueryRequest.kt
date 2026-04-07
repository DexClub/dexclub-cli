package io.github.dexclub.core.request

import io.github.dexclub.core.query.DexMethodQueryMatcher
import kotlinx.serialization.Serializable

@Serializable
data class DexMethodQueryRequest(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: DexMethodQueryMatcher? = null,
    var findFirst: Boolean = false,
) {
    fun matcher(init: DexMethodQueryMatcher.() -> Unit) {
        matcher = DexMethodQueryMatcher().apply(init)
    }
}
