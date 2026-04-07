package io.github.dexclub.core.search

import io.github.dexclub.core.query.DexStringMatchType
import io.github.dexclub.core.request.DexClassQueryRequest
import io.github.dexclub.core.request.DexFieldQueryRequest
import io.github.dexclub.core.request.DexMethodQueryRequest
import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.findClass
import io.github.dexclub.dexkit.findField
import io.github.dexclub.dexkit.findMethod
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.FieldData
import io.github.dexclub.dexkit.result.MethodData

internal class DexKitSearchBackend(
    private val bridgeProvider: () -> DexKitBridge?,
) {
    fun searchClassesByName(keyword: String): List<ClassData> {
        return findClasses(
            DexClassQueryRequest().apply {
                matcher {
                    className(
                        value = keyword,
                        matchType = DexStringMatchType.Contains,
                        ignoreCase = true,
                    )
                }
            },
        )
    }

    fun searchMethodsByString(keyword: String): List<MethodData> {
        return findMethods(
            DexMethodQueryRequest().apply {
                matcher {
                    addUsingString(
                        value = keyword,
                        matchType = DexStringMatchType.Contains,
                        ignoreCase = true,
                    )
                }
            },
        )
    }

    fun findClasses(request: DexClassQueryRequest): List<ClassData> {
        val bridge = bridgeProvider()
            ?: return emptyList()
        return bridge.findClass(request.toDexKitQuery())
    }

    fun findMethods(request: DexMethodQueryRequest): List<MethodData> {
        val bridge = bridgeProvider()
            ?: return emptyList()
        return bridge.findMethod(request.toDexKitQuery())
    }

    fun findFields(request: DexFieldQueryRequest): List<FieldData> {
        val bridge = bridgeProvider()
            ?: return emptyList()
        return bridge.findField(request.toDexKitQuery())
    }
}
