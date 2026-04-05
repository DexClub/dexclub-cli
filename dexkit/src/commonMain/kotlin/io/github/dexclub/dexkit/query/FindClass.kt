package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.result.ClassData

data class FindClass(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: ClassMatcher? = null,
    var findFirst: Boolean = false,
    var searchInClasses: List<ClassData> = emptyList(),
) {
    fun matcher(init: ClassMatcher.() -> Unit) {
        matcher = ClassMatcher().apply(init)
    }
}
