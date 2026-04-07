package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.result.ClassData
import kotlinx.serialization.Serializable

@Serializable
class BatchFindClassUsingStrings(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var searchInClasses: List<ClassData> = emptyList(),
    val groups: MutableMap<String, List<StringMatcher>> = mutableMapOf(),
)
