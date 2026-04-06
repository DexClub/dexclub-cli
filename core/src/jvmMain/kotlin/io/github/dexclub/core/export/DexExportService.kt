package io.github.dexclub.core.export

import io.github.dexclub.core.config.DexFormatConfig
import io.github.dexclub.core.config.JavaDecompileConfig
import io.github.dexclub.core.config.SmaliRenderConfig
import io.github.dexclub.core.model.DexExportFormat
import io.github.dexclub.core.model.DexExportResult
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.SmaliExportRequest
import io.github.dexclub.core.session.DexSession
import java.io.File

internal class DexExportService(
    private val session: DexSession,
    private val dexFormatConfig: DexFormatConfig = DexFormatConfig(),
    private val javaDecompileConfig: JavaDecompileConfig = JavaDecompileConfig(),
    private val smaliRenderConfig: SmaliRenderConfig = SmaliRenderConfig(),
) {
    private val dexBinaryExportService by lazy(LazyThreadSafetyMode.NONE) {
        DexBinaryExportService(
            session = session,
            dexFormatConfig = dexFormatConfig,
        )
    }
    private val smaliRenderService by lazy(LazyThreadSafetyMode.NONE) {
        SmaliRenderService(
            session = session,
            dexFormatConfig = dexFormatConfig,
        )
    }
    private val jadxDecompilerService by lazy(LazyThreadSafetyMode.NONE) {
        JadxDecompilerService()
    }

    suspend fun exportDex(request: DexExportRequest): DexExportResult {
        return DexExportResult(
            outputPath = exportSingleDex(
                className = request.className,
                dexPath = request.sourceDexPath,
                outputPath = request.outputPath,
            ),
            format = DexExportFormat.Dex,
            className = request.className,
        )
    }

    @Throws(IllegalArgumentException::class)
    suspend fun exportSingleDex(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String {
        return dexBinaryExportService.exportSingleDex(
            className = className,
            dexPath = dexPath,
            outputPath = outputPath,
        )
    }

    suspend fun exportSmali(request: SmaliExportRequest): DexExportResult {
        return DexExportResult(
            outputPath = exportSingleSmali(
                className = request.className,
                dexPath = request.sourceDexPath,
                outputPath = request.outputPath,
                renderConfig = request.config ?: smaliRenderConfig,
            ),
            format = DexExportFormat.Smali,
            className = request.className,
        )
    }

    suspend fun exportSingleSmali(
        autoUnicodeDecode: Boolean,
        className: String,
        dexPath: String,
        outputPath: String,
    ): String {
        return exportSingleSmali(
            className = className,
            dexPath = dexPath,
            outputPath = outputPath,
            renderConfig = smaliRenderConfig.copy(autoUnicodeDecode = autoUnicodeDecode),
        )
    }

    private suspend fun exportSingleSmali(
        className: String,
        dexPath: String,
        outputPath: String,
        renderConfig: SmaliRenderConfig,
    ): String {
        return smaliRenderService.exportSingleSmali(
            className = className,
            dexPath = dexPath,
            outputPath = outputPath,
            renderConfig = renderConfig,
        )
    }

    suspend fun exportJava(request: JavaExportRequest): DexExportResult {
        return DexExportResult(
            outputPath = exportSingleJavaSource(
                className = request.className,
                dexPath = request.sourceDexPath,
                outputPath = request.outputPath,
                decompileConfig = request.config ?: javaDecompileConfig,
            ),
            format = DexExportFormat.JavaSource,
            className = request.className,
        )
    }

    suspend fun exportSingleJavaSource(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String {
        return exportSingleJavaSource(
            className = className,
            dexPath = dexPath,
            outputPath = outputPath,
            decompileConfig = javaDecompileConfig,
        )
    }

    private suspend fun exportSingleJavaSource(
        className: String,
        dexPath: String,
        outputPath: String,
        decompileConfig: JavaDecompileConfig,
    ): String {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        val tempDex = File(outputFile.parentFile ?: File("."), "${outputFile.name}.tmp.dex")
        return try {
            exportSingleDex(
                className = className,
                dexPath = dexPath,
                outputPath = tempDex.absolutePath,
            )
            jadxDecompilerService.decompileDexToJavaSource(
                dexPath = tempDex.absolutePath,
                outputPath = outputPath,
                decompileConfig = decompileConfig,
            )
        } finally {
            tempDex.delete()
        }
    }
}
