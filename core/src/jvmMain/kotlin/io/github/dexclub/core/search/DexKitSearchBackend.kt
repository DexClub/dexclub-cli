package io.github.dexclub.core.search

import io.github.dexclub.core.request.DexClassQueryRequest
import io.github.dexclub.core.request.DexFieldQueryRequest
import io.github.dexclub.core.request.DexMethodQueryRequest
import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.query.ClassMatcher
import io.github.dexclub.dexkit.query.MethodMatcher
import io.github.dexclub.dexkit.query.StringMatchType
import io.github.dexclub.dexkit.query.StringMatcher
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.FieldData
import io.github.dexclub.dexkit.result.MethodData

internal class DexKitSearchBackend(
    private val bridgeProvider: () -> DexKitBridge?,
) {
    fun searchClassesByName(keyword: String): List<ClassData> {
        return findClasses(
            DexClassQueryRequest().apply {
                matcher = ClassMatcher(
                    className = StringMatcher(keyword, StringMatchType.Contains, true),
                )
            },
        )
    }

    fun searchMethodsByString(keyword: String): List<MethodData> {
        return findMethods(
            DexMethodQueryRequest().apply {
                matcher = MethodMatcher().apply {
                    usingStrings += StringMatcher(keyword, StringMatchType.Contains, true)
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
