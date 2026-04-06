package io.github.dexclub.core

import io.github.dexclub.core.config.CoreRuntimeConfig
import io.github.dexclub.core.export.DexExportService
import io.github.dexclub.core.input.DexInputInspector
import io.github.dexclub.core.model.DexArchiveInfo
import io.github.dexclub.core.model.DexClassHit
import io.github.dexclub.core.model.DexInputKind
import io.github.dexclub.core.model.DexInputRef
import io.github.dexclub.core.model.DexMethodHit
import io.github.dexclub.core.model.toDexClassHit
import io.github.dexclub.core.model.toDexMethodHit
import io.github.dexclub.core.model.DexExportResult
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.SmaliExportRequest
import io.github.dexclub.core.runtime.DexKitRuntime
import io.github.dexclub.core.session.DexSessionLoader
import io.github.dexclub.core.source.DexIndexedClass
import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.findClass
import io.github.dexclub.dexkit.findMethod
import io.github.dexclub.dexkit.query.StringMatchType
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.MethodData
import java.io.File

actual class DexEngine actual constructor(
    dexPaths: List<String>,
    private val config: CoreRuntimeConfig,
) : AutoCloseable {
    private val normalizedDexPaths = dexPaths
        .map(String::trim)
        .filter(String::isNotEmpty)
    private val dexFiles by lazy(LazyThreadSafetyMode.NONE) {
        normalizedDexPaths.map(::File)
    }
    private val singleDexSourcePath by lazy(LazyThreadSafetyMode.NONE) {
        dexFiles.singleOrNull()
            ?.takeIf(DexInputInspector::isDex)
            ?.absolutePath
    }
    private val dexSession by lazy(LazyThreadSafetyMode.NONE) {
        DexSessionLoader.loadMultiDex(
            dexFiles = dexFiles,
            config = config.dexFormat,
        )
    }
    private val dexExportService by lazy(LazyThreadSafetyMode.NONE) {
        DexExportService(
            session = dexSession,
            dexFormatConfig = config.dexFormat,
            javaDecompileConfig = config.javaDecompile,
            smaliRenderConfig = config.smaliRender,
        )
    }
    private val dexKitRuntime by lazy(LazyThreadSafetyMode.NONE) {
        DexKitRuntime(
            dexPaths = normalizedDexPaths,
            config = config.dexKit,
        )
    }

    actual fun inspect(): DexArchiveInfo {
        val kind = inferInputKind()
        return DexArchiveInfo(
            kind = kind,
            inputs = normalizedDexPaths.map(::DexInputRef),
            dexCount = when (kind) {
                DexInputKind.Apk -> readDexNum() ?: 0
                DexInputKind.Dex -> dexCount()
                DexInputKind.Unknown -> 0
            },
            classCount = when (kind) {
                DexInputKind.Dex -> classCount()
                DexInputKind.Apk,
                DexInputKind.Unknown,
                -> null
            },
        )
    }

    actual fun dexCount(): Int {
        return dexSession.dexCount
    }

    actual fun classCount(): Int {
        return dexSession.classCount
    }

    actual fun indexedClasses(): Sequence<DexIndexedClass> {
        return dexSession.classes()
    }

    actual fun getOrCreateBridge(): DexKitBridge? {
        return dexKitRuntime.getOrCreateBridge()
    }

    actual fun readDexNum(): Int? {
        return dexKitRuntime.readDexNum()
    }

    actual fun searchClassHitsByName(keyword: String): List<DexClassHit> {
        return searchClassesByName(keyword).map { result ->
            result.toDexClassHit(singleDexSourcePath)
        }
    }

    actual fun searchMethodHitsByString(keyword: String): List<DexMethodHit> {
        return searchMethodsByString(keyword).map { result ->
            result.toDexMethodHit(singleDexSourcePath)
        }
    }

    actual fun searchClassesByName(keyword: String): List<ClassData> {
        val bridge = getOrCreateBridge()
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

    actual fun searchMethodsByString(keyword: String): List<MethodData> {
        val bridge = getOrCreateBridge()
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

    actual suspend fun exportDex(
        request: DexExportRequest,
    ): DexExportResult {
        return dexExportService.exportDex(request)
    }

    actual suspend fun exportSmali(
        request: SmaliExportRequest,
    ): DexExportResult {
        return dexExportService.exportSmali(request)
    }

    actual suspend fun exportJava(
        request: JavaExportRequest,
    ): DexExportResult {
        return dexExportService.exportJava(request)
    }

    actual suspend fun exportSingleDex(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String {
        return dexExportService.exportSingleDex(
            className = className,
            dexPath = dexPath,
            outputPath = outputPath,
        )
    }

    actual suspend fun exportSingleSmali(
        autoUnicodeDecode: Boolean,
        className: String,
        dexPath: String,
        outputPath: String,
    ): String {
        return dexExportService.exportSingleSmali(
            autoUnicodeDecode = autoUnicodeDecode,
            className = className,
            dexPath = dexPath,
            outputPath = outputPath,
        )
    }

    actual suspend fun exportSingleJavaSource(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String {
        return dexExportService.exportSingleJavaSource(
            className = className,
            dexPath = dexPath,
            outputPath = outputPath,
        )
    }

    actual override fun close() {
        dexKitRuntime.close()
    }

    actual companion object {
        actual fun isDex(path: String): Boolean {
            return DexInputInspector.isDex(File(path))
        }
    }

    private fun inferInputKind(): DexInputKind {
        val inputFile = dexFiles.singleOrNull()
            ?: return if (dexFiles.isEmpty()) DexInputKind.Unknown else DexInputKind.Dex
        return when {
            inputFile.extension.equals("apk", ignoreCase = true) -> DexInputKind.Apk
            DexInputInspector.isDex(inputFile) -> DexInputKind.Dex
            else -> DexInputKind.Unknown
        }
    }
}
