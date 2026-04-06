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

        val args = JadxArgs().apply {
            setInputFile(dexFile)
            outDir = outputDirectory
            codeCache = NoOpCodeCache()
            decompileConfig.applyTo(this)
        }

        JadxDecompiler(args).use { decompiler ->
            decompiler.load()
            val classes = decompiler.classesWithInners.filterNot { it.isNoCode }
            val javaClass = classes.singleOrNull()
                ?: throw IllegalStateException(
                    "Expected exactly one decompiled class from `$dexPath`, but got ${classes.size}",
                )
            javaFile.parentFile?.mkdirs()
            javaFile.writeText(javaClass.code, Charsets.UTF_8)
        }

        return javaFile.absolutePath
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
