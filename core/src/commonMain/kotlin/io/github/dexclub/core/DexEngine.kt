package io.github.dexclub.core

import io.github.dexclub.core.config.CoreRuntimeConfig
import io.github.dexclub.core.model.DexArchiveInfo
import io.github.dexclub.core.model.DexClassHit
import io.github.dexclub.core.model.DexExportResult
import io.github.dexclub.core.model.DexMethodHit
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.SmaliExportRequest
import io.github.dexclub.core.source.DexIndexedClass

expect class DexEngine(
    dexPaths: List<String>,
    config: CoreRuntimeConfig = CoreRuntimeConfig(),
) : AutoCloseable {
    fun inspect(): DexArchiveInfo

    fun dexCount(): Int

    fun classCount(): Int

    fun indexedClasses(): Sequence<DexIndexedClass>

    fun searchClassHitsByName(keyword: String): List<DexClassHit>

    fun searchMethodHitsByString(keyword: String): List<DexMethodHit>

    suspend fun exportDex(
        request: DexExportRequest,
    ): DexExportResult

    suspend fun exportSmali(
        request: SmaliExportRequest,
    ): DexExportResult

    suspend fun exportJava(
        request: JavaExportRequest,
    ): DexExportResult

    override fun close()

    companion object {
        fun isDex(path: String): Boolean
    }
}
