package io.github.dexclub.core.search

import io.github.dexclub.core.model.DexClassHit
import io.github.dexclub.core.model.DexFieldHit
import io.github.dexclub.core.model.DexMethodHit
import io.github.dexclub.core.model.toDexClassHit
import io.github.dexclub.core.model.toDexFieldHit
import io.github.dexclub.core.model.toDexMethodHit
import io.github.dexclub.core.request.DexClassQueryRequest
import io.github.dexclub.core.request.DexFieldQueryRequest
import io.github.dexclub.core.request.DexMethodQueryRequest
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.FieldData
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

    fun findClassHits(request: DexClassQueryRequest): List<DexClassHit> {
        return findClasses(request).map { result ->
            result.toDexClassHit(classDescriptorSourcePathProvider(result.descriptor))
        }
    }

    fun findMethodHits(request: DexMethodQueryRequest): List<DexMethodHit> {
        return findMethods(request).map { result ->
            result.toDexMethodHit(classNameSourcePathProvider(result.className))
        }
    }

    fun findFieldHits(request: DexFieldQueryRequest): List<DexFieldHit> {
        return findFields(request).map { result ->
            result.toDexFieldHit(classNameSourcePathProvider(result.className))
        }
    }

    fun searchClassesByName(keyword: String): List<ClassData> {
        return backend.searchClassesByName(keyword)
    }

    fun searchMethodsByString(keyword: String): List<MethodData> {
        return backend.searchMethodsByString(keyword)
    }

    fun findClasses(request: DexClassQueryRequest): List<ClassData> {
        return backend.findClasses(request)
    }

    fun findMethods(request: DexMethodQueryRequest): List<MethodData> {
        return backend.findMethods(request)
    }

    fun findFields(request: DexFieldQueryRequest): List<FieldData> {
        return backend.findFields(request)
    }
}
