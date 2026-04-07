package io.github.dexclub.core

import io.github.dexclub.core.config.CoreRuntimeConfig
import io.github.dexclub.core.export.DexExportService
import io.github.dexclub.core.input.DexArchiveInspector
import io.github.dexclub.core.input.DexInputInspector
import io.github.dexclub.core.model.DexArchiveInfo
import io.github.dexclub.core.model.DexClassHit
import io.github.dexclub.core.model.DexExportResult
import io.github.dexclub.core.model.DexFieldHit
import io.github.dexclub.core.model.DexMethodHit
import io.github.dexclub.core.request.DexClassQueryRequest
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.DexFieldQueryRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.DexMethodQueryRequest
import io.github.dexclub.core.request.SmaliExportRequest
import io.github.dexclub.core.runtime.DexKitRuntime
import io.github.dexclub.core.search.DexKitSearchBackend
import io.github.dexclub.core.search.DexSearchService
import io.github.dexclub.core.session.DexSessionLoader
import io.github.dexclub.core.source.DexIndexedClass
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
    private val singleApkInputPath by lazy(LazyThreadSafetyMode.NONE) {
        dexFiles.singleOrNull()
            ?.takeIf { it.extension.equals("apk", ignoreCase = true) }
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
    private val dexArchiveInspector by lazy(LazyThreadSafetyMode.NONE) {
        DexArchiveInspector(
            inputPaths = normalizedDexPaths,
            inputFiles = dexFiles,
            dexCountProvider = ::dexCount,
            classCountProvider = ::classCount,
            readDexNumProvider = dexKitRuntime::readDexNum,
        )
    }
    private val dexKitSearchBackend by lazy(LazyThreadSafetyMode.NONE) {
        DexKitSearchBackend(
            bridgeProvider = dexKitRuntime::getOrCreateBridge,
        )
    }
    private val dexSearchService by lazy(LazyThreadSafetyMode.NONE) {
        DexSearchService(
            backend = dexKitSearchBackend,
            classDescriptorSourcePathProvider = ::findSourcePathByClassDescriptor,
            classNameSourcePathProvider = ::findSourcePathByClassName,
        )
    }

    private fun findSourcePathByClassDescriptor(descriptor: String): String? {
        return singleApkInputPath ?: dexSession.findDexPathByClassDescriptor(descriptor)
    }

    private fun findSourcePathByClassName(className: String): String? {
        return singleApkInputPath ?: dexSession.findDexPathByClassName(className)
    }

    actual fun inspect(): DexArchiveInfo {
        return dexArchiveInspector.inspect()
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

    actual fun searchClassHitsByName(keyword: String): List<DexClassHit> {
        return dexSearchService.searchClassHitsByName(keyword)
    }

    actual fun searchMethodHitsByString(keyword: String): List<DexMethodHit> {
        return dexSearchService.searchMethodHitsByString(keyword)
    }

    actual fun findClassHits(request: DexClassQueryRequest): List<DexClassHit> {
        return dexSearchService.findClassHits(request)
    }

    actual fun findMethodHits(request: DexMethodQueryRequest): List<DexMethodHit> {
        return dexSearchService.findMethodHits(request)
    }

    actual fun findFieldHits(request: DexFieldQueryRequest): List<DexFieldHit> {
        return dexSearchService.findFieldHits(request)
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

    actual override fun close() {
        dexKitRuntime.close()
    }

    actual companion object {
        actual fun isDex(path: String): Boolean {
            return DexInputInspector.isDex(File(path))
        }
    }
}
