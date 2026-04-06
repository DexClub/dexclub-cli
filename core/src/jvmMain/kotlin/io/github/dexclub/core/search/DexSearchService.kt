package io.github.dexclub.core.search

import io.github.dexclub.core.model.DexClassHit
import io.github.dexclub.core.model.DexMethodHit
import io.github.dexclub.core.model.toDexClassHit
import io.github.dexclub.core.model.toDexMethodHit
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.MethodData

internal class DexSearchService(
    private val backend: DexKitSearchBackend,
    private val sourceDexPathProvider: () -> String?,
) {
    fun searchClassHitsByName(keyword: String): List<DexClassHit> {
        val sourceDexPath = sourceDexPathProvider()
        return searchClassesByName(keyword).map { result ->
            result.toDexClassHit(sourceDexPath)
        }
    }

    fun searchMethodHitsByString(keyword: String): List<DexMethodHit> {
        val sourceDexPath = sourceDexPathProvider()
        return searchMethodsByString(keyword).map { result ->
            result.toDexMethodHit(sourceDexPath)
        }
    }

    fun searchClassesByName(keyword: String): List<ClassData> {
        return backend.searchClassesByName(keyword)
    }

    fun searchMethodsByString(keyword: String): List<MethodData> {
        return backend.searchMethodsByString(keyword)
    }
}
