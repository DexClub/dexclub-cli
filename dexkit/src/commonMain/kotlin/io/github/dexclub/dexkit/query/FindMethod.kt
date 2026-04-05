package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.MethodData

data class FindMethod(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: MethodMatcher? = null,
    var findFirst: Boolean = false,
    var searchInClasses: List<ClassData> = emptyList(),
    var searchInMethods: List<MethodData> = emptyList(),
) {
    fun matcher(init: MethodMatcher.() -> Unit) {
        matcher = MethodMatcher().apply(init)
    }
}
