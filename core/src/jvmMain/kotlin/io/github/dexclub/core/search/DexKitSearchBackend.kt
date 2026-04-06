package io.github.dexclub.core.search

import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.findClass
import io.github.dexclub.dexkit.findMethod
import io.github.dexclub.dexkit.query.StringMatchType
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.MethodData

internal class DexKitSearchBackend(
    private val bridgeProvider: () -> DexKitBridge?,
) {
    fun searchClassesByName(keyword: String): List<ClassData> {
        val bridge = bridgeProvider()
            ?: return emptyList()
        return bridge.findClass {
            matcher {
                className(
                    value = keyword,
                    matchType = StringMatchType.Contains,
                    ignoreCase = true,
                )
            }
        }
    }

    fun searchMethodsByString(keyword: String): List<MethodData> {
        val bridge = bridgeProvider()
            ?: return emptyList()
        return bridge.findMethod {
            matcher {
                addUsingString(
                    value = keyword,
                    matchType = StringMatchType.Contains,
                    ignoreCase = true,
                )
            }
        }
    }
}
