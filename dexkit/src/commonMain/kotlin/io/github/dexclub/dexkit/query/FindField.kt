package io.github.dexclub.dexkit.query

import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.FieldData
import kotlinx.serialization.Serializable

@Serializable
data class FindField(
    var searchPackages: List<String> = emptyList(),
    var excludePackages: List<String> = emptyList(),
    var ignorePackagesCase: Boolean = false,
    var matcher: FieldMatcher? = null,
    var findFirst: Boolean = false,
    var searchInClasses: List<ClassData> = emptyList(),
    var searchInFields: List<FieldData> = emptyList(),
) {
    fun matcher(init: FieldMatcher.() -> Unit) {
        matcher = FieldMatcher().apply(init)
    }
}
