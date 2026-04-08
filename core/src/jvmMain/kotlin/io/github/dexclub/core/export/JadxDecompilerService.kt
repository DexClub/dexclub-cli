package io.github.dexclub.core.export

import io.github.dexclub.core.config.JavaDecompileConfig
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.impl.NoOpCodeCache
import java.io.File

internal class JadxDecompilerService {
    fun decompileDexToJavaSource(
        dexPath: String,
        outputPath: String,
        decompileConfig: JavaDecompileConfig,
    ): String {
        val dexFile = File(dexPath)
        val javaFile = File(outputPath)
        val outputDirectory = javaFile.parentFile
            ?: throw IllegalArgumentException("output must have a parent directory")

        val javaCode = loadSingleJavaCode(
            dexFile = dexFile,
            outputDirectory = outputDirectory,
            decompileConfig = decompileConfig,
        )
        javaFile.parentFile?.mkdirs()
        javaFile.writeText(javaCode, Charsets.UTF_8)

        return javaFile.absolutePath
    }

    private fun loadSingleJavaCode(
        dexFile: File,
        outputDirectory: File,
        decompileConfig: JavaDecompileConfig,
    ): String {
        val initialCodes = loadClassCodes(
            dexFile = dexFile,
            outputDirectory = outputDirectory,
            decompileConfig = decompileConfig,
        )
        val resolvedCodes = if (initialCodes.isEmpty() && decompileConfig.useDxInput) {
            loadClassCodes(
                dexFile = dexFile,
                outputDirectory = outputDirectory,
                decompileConfig = decompileConfig.copy(useDxInput = false),
            )
        } else {
            initialCodes
        }
        return resolvedCodes.singleOrNull()
            ?: throw IllegalStateException(
                "Expected exactly one decompiled class from `${dexFile.absolutePath}`, but got ${resolvedCodes.size}",
            )
    }

    private fun loadClassCodes(
        dexFile: File,
        outputDirectory: File,
        decompileConfig: JavaDecompileConfig,
    ): List<String> {
        val args = JadxArgs().apply {
            setInputFile(dexFile)
            outDir = outputDirectory
            codeCache = NoOpCodeCache()
            decompileConfig.applyTo(this)
        }
        JadxDecompiler(args).use { decompiler ->
            decompiler.load()
            return decompiler.classesWithInners
                .filterNot { it.isNoCode }
                .map { it.code }
        }
    }

    private fun JavaDecompileConfig.applyTo(args: JadxArgs) {
        args.setUseDxInput(useDxInput)
        args.isRenameValid = renameValid
        args.isRenameCaseSensitive = renameCaseSensitive
        args.isShowInconsistentCode = showInconsistentCode
        args.isDebugInfo = debugInfo
        args.isMoveInnerClasses = moveInnerClasses
        args.isInlineAnonymousClasses = inlineAnonymousClasses
    }
}
