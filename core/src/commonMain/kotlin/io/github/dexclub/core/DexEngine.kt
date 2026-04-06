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

    @Deprecated(
        message = "请改用 searchClassHitsByName",
        replaceWith = ReplaceWith("searchClassHitsByName(keyword)"),
    )
    fun searchClassesByName(keyword: String): List<ClassData>

    @Deprecated(
        message = "请改用 searchMethodHitsByString",
        replaceWith = ReplaceWith("searchMethodHitsByString(keyword)"),
    )
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

    @Deprecated(
        message = "请改用 exportDex",
        replaceWith = ReplaceWith(
            "exportDex(DexExportRequest(className = className, sourceDexPath = dexPath, outputPath = outputPath))",
        ),
    )
    suspend fun exportSingleDex(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String

    @Deprecated(
        message = "请改用 exportSmali",
        replaceWith = ReplaceWith(
            "exportSmali(SmaliExportRequest(className = className, sourceDexPath = dexPath, outputPath = outputPath))",
        ),
    )
    suspend fun exportSingleSmali(
        autoUnicodeDecode: Boolean,
        className: String,
        dexPath: String,
        outputPath: String,
    ): String

    @Deprecated(
        message = "请改用 exportJava",
        replaceWith = ReplaceWith(
            "exportJava(JavaExportRequest(className = className, sourceDexPath = dexPath, outputPath = outputPath))",
        ),
    )
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
