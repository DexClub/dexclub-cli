package io.github.dexclub.core.export

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import io.github.dexclub.core.UnescapedUnicodeBaksmaliWriter
import io.github.dexclub.core.session.DexSession
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.impl.NoOpCodeCache
import java.io.File
import java.io.StringWriter

internal class DexExportService(
    private val session: DexSession,
) {
    @Throws(IllegalArgumentException::class)
    suspend fun exportSingleDex(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String {
        if (className.trim().isEmpty()) {
            throw IllegalArgumentException("className must not be empty")
        }

        val findClassDef = session.requireClassDef(
            className = className,
            dexPath = dexPath,
        )

        val dataStore = MemoryDataStore()
        val dexPool = DexPool(Opcodes.getDefault())
        dexPool.internClass(findClassDef)
        dexPool.writeTo(dataStore)

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(dataStore.data)
        return outputFile.absolutePath
    }

    suspend fun exportSingleSmali(
        autoUnicodeDecode: Boolean,
        className: String,
        dexPath: String,
        outputPath: String,
    ): String {
        if (className.trim().isEmpty()) {
            throw IllegalArgumentException("className must not be empty")
        }

        val findClassDef = session.requireClassDef(
            className = className,
            dexPath = dexPath,
        )

        val options = BaksmaliOptions().apply {
            parameterRegisters = true
            localsDirective = true
            debugInfo = true
            accessorComments = true
        }

        val stringWriter = StringWriter()
        val baksmaliWriter = if (autoUnicodeDecode) {
            UnescapedUnicodeBaksmaliWriter(stringWriter)
        } else {
            BaksmaliWriter(stringWriter)
        }

        val classDefinition = ClassDefinition(options, findClassDef)
        classDefinition.writeTo(baksmaliWriter)
        baksmaliWriter.flush()

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(stringWriter.toString(), Charsets.UTF_8)
        return outputFile.absolutePath
    }

    suspend fun exportSingleJavaSource(
        className: String,
        dexPath: String,
        outputPath: String,
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
            decompileDexToJavaSource(
                dexPath = tempDex.absolutePath,
                outputPath = outputPath,
            )
        } finally {
            tempDex.delete()
        }
    }

    private fun decompileDexToJavaSource(
        dexPath: String,
        outputPath: String,
    ): String {
        val dexFile = File(dexPath)
        val javaFile = File(outputPath)
        val outDir = javaFile.parentFile
            ?: throw IllegalArgumentException("output must have a parent directory")

        val args = JadxArgs()
        args.setInputFile(dexFile)
        args.outDir = outDir
        args.codeCache = NoOpCodeCache()
        args.setUseDxInput(true)
        args.isRenameValid = false
        args.isRenameCaseSensitive = true
        args.isShowInconsistentCode = false
        args.isDebugInfo = false
        args.isMoveInnerClasses = false
        args.isInlineAnonymousClasses = false

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
}
