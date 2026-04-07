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
) {

    fun addGroup(name: String, vararg strings: String) {
        groups[name] = strings.map { StringMatcher(it) }
    }

    fun addGroup(name: String, matchers: List<StringMatcher>) {
        groups[name] = matchers
    }

    fun groups(keywordsMap: Map<String, List<String>>, matchType: StringMatchType = StringMatchType.Contains, ignoreCase: Boolean = false) {
        keywordsMap.forEach { (key, strings) ->
            groups[key] = strings.map { StringMatcher(it, matchType, ignoreCase) }
        }
    }
}
