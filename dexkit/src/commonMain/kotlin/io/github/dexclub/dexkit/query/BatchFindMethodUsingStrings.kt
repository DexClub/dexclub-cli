package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.MethodData
import kotlinx.serialization.Serializable

@Serializable
class BatchFindMethodUsingStrings(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var searchInClasses: List<ClassData> = emptyList(),
    var searchInMethods: List<MethodData> = emptyList(),
    val groups: MutableMap<String, List<StringMatcher>> = mutableMapOf(),
)
