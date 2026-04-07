package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.result.ClassData
import kotlinx.serialization.Serializable

@Serializable
data class FindClass(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: ClassMatcher? = null,
    var findFirst: Boolean = false,
    var searchInClasses: List<ClassData> = emptyList(),
)
