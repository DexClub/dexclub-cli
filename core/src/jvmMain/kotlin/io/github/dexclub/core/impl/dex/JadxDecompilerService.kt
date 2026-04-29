package io.github.dexclub.core.impl.dex

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.impl.NoOpCodeCache
import jadx.api.impl.SimpleCodeWriter
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.loader.JadxPluginLoader
import jadx.api.usage.impl.EmptyUsageInfoCache
import jadx.plugins.input.dex.DexInputPlugin
import jadx.plugins.kotlin.metadata.KotlinMetadataPlugin
import java.io.File
import java.util.UUID
import java.util.function.Function

internal class JadxDecompilerService {
    // 保持显式插件注册，避免最小 dex 导出链再依赖 ServiceLoader 或 fat jar 的服务文件合并结果。
    private val pluginLoader = object : JadxPluginLoader {
        override fun load(): List<JadxPlugin> =
            listOf(
                DexInputPlugin(),
                KotlinMetadataPlugin(),
            )

        override fun close() {
            // no-op
        }
    }

    fun decompileDexToJavaSource(
        dexPath: String,
        outputPath: String,
    ): String {
        val dexFile = File(dexPath)
        val javaFile = File(outputPath)
        val outputDirectory = javaFile.parentFile
            ?: throw IllegalArgumentException("output must have a parent directory")
        val jadxOutputDirectory = File(
            outputDirectory,
            ".jadx-tmp-${javaFile.nameWithoutExtension}-${UUID.randomUUID().toString().replace("-", "")}",
        )
        return try {
            val javaCode = loadSingleJavaCode(
                dexFile = dexFile,
                outputDirectory = jadxOutputDirectory,
            )
            javaFile.parentFile?.mkdirs()
            javaFile.writeText(javaCode, Charsets.UTF_8)
            javaFile.absolutePath
        } finally {
            jadxOutputDirectory.deleteRecursively()
        }
    }

    private fun loadSingleJavaCode(
        dexFile: File,
        outputDirectory: File,
    ): String {
        val resolvedCodes = loadClassCodes(
            dexFile = dexFile,
            outputDirectory = outputDirectory,
        )
        return resolvedCodes.singleOrNull()
            ?: throw IllegalStateException(
                "Expected exactly one decompiled class from `${dexFile.absolutePath}`, but got ${resolvedCodes.size}",
            )
    }

    private fun loadClassCodes(
        dexFile: File,
        outputDirectory: File,
    ): List<String> {
        val args = JadxArgs().apply {
            setInputFile(dexFile)
            outDir = outputDirectory
            codeCache = NoOpCodeCache()
            usageInfoCache = EmptyUsageInfoCache()
            codeWriterProvider = Function(::SimpleCodeWriter)
            setUseDxInput(true)
            pluginLoader = this@JadxDecompilerService.pluginLoader
            isRenameValid = true
            isRenameCaseSensitive = true
            isShowInconsistentCode = false
            isDebugInfo = false
            isMoveInnerClasses = false
            isInlineAnonymousClasses = false
        }
        JadxDecompiler(args).use { decompiler ->
            decompiler.load()
            val directCodes = decompiler.classesWithInners
                .filterNot { it.isNoCode }
                .map { it.code }
            if (directCodes.isNotEmpty()) {
                return directCodes
            }

            decompiler.save()
            val generatedSourcesDir = File(outputDirectory, "sources")
            if (!generatedSourcesDir.isDirectory) {
                return emptyList()
            }
            return generatedSourcesDir.walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .map { it.readText(Charsets.UTF_8) }
                .toList()
        }
    }
}
