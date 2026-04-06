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
import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.MethodData

expect class DexEngine(
    dexPaths: List<String>,
    config: CoreRuntimeConfig = CoreRuntimeConfig(),
) : AutoCloseable {
    fun inspect(): DexArchiveInfo

    fun dexCount(): Int

    fun classCount(): Int

    fun indexedClasses(): Sequence<DexIndexedClass>

    fun getOrCreateBridge(): DexKitBridge?

    fun readDexNum(): Int?

    fun searchClassHitsByName(keyword: String): List<DexClassHit>

    fun searchMethodHitsByString(keyword: String): List<DexMethodHit>

    fun searchClassesByName(keyword: String): List<ClassData>

    fun searchMethodsByString(keyword: String): List<MethodData>

    suspend fun exportDex(
        request: DexExportRequest,
    ): DexExportResult

    suspend fun exportSmali(
        request: SmaliExportRequest,
    ): DexExportResult

    suspend fun exportJava(
        request: JavaExportRequest,
    ): DexExportResult

    suspend fun exportSingleDex(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String

    suspend fun exportSingleSmali(
        autoUnicodeDecode: Boolean,
        className: String,
        dexPath: String,
        outputPath: String,
    ): String

    suspend fun exportSingleJavaSource(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String

    override fun close()

    companion object {
        fun isDex(path: String): Boolean
    }
}
