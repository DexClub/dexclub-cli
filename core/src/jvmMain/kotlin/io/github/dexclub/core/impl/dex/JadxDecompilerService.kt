package io.github.dexclub.core.impl.dex

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.impl.NoOpCodeCache
import java.io.File

internal class JadxDecompilerService {
    fun decompileDexToJavaSource(
        dexPath: String,
        outputPath: String,
    ): String {
        val dexFile = File(dexPath)
        val javaFile = File(outputPath)
        val outputDirectory = javaFile.parentFile
            ?: throw IllegalArgumentException("output must have a parent directory")

        val javaCode = loadSingleJavaCode(
            dexFile = dexFile,
            outputDirectory = outputDirectory,
        )
        javaFile.parentFile?.mkdirs()
        javaFile.writeText(javaCode, Charsets.UTF_8)
        return javaFile.absolutePath
    }

    private fun loadSingleJavaCode(
        dexFile: File,
        outputDirectory: File,
    ): String {
        val initialCodes = loadClassCodes(
            dexFile = dexFile,
            outputDirectory = outputDirectory,
            useDxInput = true,
        )
        val resolvedCodes = if (initialCodes.isEmpty()) {
            loadClassCodes(
                dexFile = dexFile,
                outputDirectory = outputDirectory,
                useDxInput = false,
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
        useDxInput: Boolean,
    ): List<String> {
        val args = JadxArgs().apply {
            setInputFile(dexFile)
            outDir = outputDirectory
            codeCache = NoOpCodeCache()
            setUseDxInput(useDxInput)
            isRenameValid = false
            isRenameCaseSensitive = true
            isShowInconsistentCode = false
            isDebugInfo = false
            isMoveInnerClasses = false
            isInlineAnonymousClasses = false
        }
        JadxDecompiler(args).use { decompiler ->
            decompiler.load()
            return decompiler.classesWithInners
                .filterNot { it.isNoCode }
                .map { it.code }
        }
    }
}
