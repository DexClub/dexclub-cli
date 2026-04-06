package io.github.dexclub.core.search

import io.github.dexclub.core.model.DexClassHit
import io.github.dexclub.core.model.DexMethodHit
import io.github.dexclub.core.model.toDexClassHit
import io.github.dexclub.core.model.toDexMethodHit
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.MethodData

internal class DexSearchService(
    private val backend: DexKitSearchBackend,
    private val classDescriptorSourcePathProvider: (String) -> String?,
    private val classNameSourcePathProvider: (String) -> String?,
) {
    fun searchClassHitsByName(keyword: String): List<DexClassHit> {
        return searchClassesByName(keyword).map { result ->
            result.toDexClassHit(classDescriptorSourcePathProvider(result.descriptor))
        }
    }

    fun searchMethodHitsByString(keyword: String): List<DexMethodHit> {
        return searchMethodsByString(keyword).map { result ->
            result.toDexMethodHit(classNameSourcePathProvider(result.className))
        }
    }

    fun searchClassesByName(keyword: String): List<ClassData> {
        return backend.searchClassesByName(keyword)
    }

    fun searchMethodsByString(keyword: String): List<MethodData> {
        return backend.searchMethodsByString(keyword)
    }
}
