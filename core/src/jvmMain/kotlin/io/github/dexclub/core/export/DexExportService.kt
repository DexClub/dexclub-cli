package io.github.dexclub.core.export

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import io.github.dexclub.core.UnescapedUnicodeBaksmaliWriter
import io.github.dexclub.core.config.DexFormatConfig
import io.github.dexclub.core.config.JavaDecompileConfig
import io.github.dexclub.core.config.SmaliRenderConfig
import io.github.dexclub.core.session.DexSession
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.impl.NoOpCodeCache
import java.io.File
import java.io.StringWriter

internal class DexExportService(
    private val session: DexSession,
    private val dexFormatConfig: DexFormatConfig = DexFormatConfig(),
    private val javaDecompileConfig: JavaDecompileConfig = JavaDecompileConfig(),
    private val smaliRenderConfig: SmaliRenderConfig = SmaliRenderConfig(),
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
        val dexPool = DexPool(dexFormatConfig.toOpcodes())
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

        val effectiveSmaliRenderConfig = smaliRenderConfig
            .copy(autoUnicodeDecode = autoUnicodeDecode)
        val options = effectiveSmaliRenderConfig.toBaksmaliOptions(dexFormatConfig)

        val stringWriter = StringWriter()
        val baksmaliWriter = if (effectiveSmaliRenderConfig.autoUnicodeDecode) {
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
        val outputDirectory = javaFile.parentFile
            ?: throw IllegalArgumentException("output must have a parent directory")

        val args = JadxArgs().apply {
            setInputFile(dexFile)
            outDir = outputDirectory
            codeCache = NoOpCodeCache()
            javaDecompileConfig.applyTo(this)
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

    private fun DexFormatConfig.toOpcodes(): Opcodes {
        return opcodeApiLevel?.let(Opcodes::forApi) ?: Opcodes.getDefault()
    }

    private fun SmaliRenderConfig.toBaksmaliOptions(
        dexFormatConfig: DexFormatConfig,
    ): BaksmaliOptions {
        return BaksmaliOptions().apply {
            apiLevel = dexFormatConfig.toOpcodes().api
            parameterRegisters = this@toBaksmaliOptions.parameterRegisters
            localsDirective = this@toBaksmaliOptions.localsDirective
            debugInfo = this@toBaksmaliOptions.debugInfo
            accessorComments = this@toBaksmaliOptions.accessorComments
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
