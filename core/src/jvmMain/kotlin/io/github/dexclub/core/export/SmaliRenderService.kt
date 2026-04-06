package io.github.dexclub.core.export

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.Opcodes
import io.github.dexclub.core.UnescapedUnicodeBaksmaliWriter
import io.github.dexclub.core.config.DexFormatConfig
import io.github.dexclub.core.config.SmaliRenderConfig
import io.github.dexclub.core.session.DexSession
import java.io.File
import java.io.StringWriter

internal class SmaliRenderService(
    private val session: DexSession,
    private val dexFormatConfig: DexFormatConfig = DexFormatConfig(),
) {
    fun exportSingleSmali(
        className: String,
        dexPath: String,
        outputPath: String,
        renderConfig: SmaliRenderConfig,
    ): String {
        if (className.trim().isEmpty()) {
            throw IllegalArgumentException("className must not be empty")
        }

        val classDef = session.requireClassDef(
            className = className,
            dexPath = dexPath,
        )

        val options = renderConfig.toBaksmaliOptions(dexFormatConfig)
        val stringWriter = StringWriter()
        val baksmaliWriter = if (renderConfig.autoUnicodeDecode) {
            UnescapedUnicodeBaksmaliWriter(stringWriter)
        } else {
            BaksmaliWriter(stringWriter)
        }

        ClassDefinition(options, classDef).writeTo(baksmaliWriter)
        baksmaliWriter.flush()

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(stringWriter.toString(), Charsets.UTF_8)
        return outputFile.absolutePath
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

    private fun DexFormatConfig.toOpcodes(): Opcodes {
        return opcodeApiLevel?.let(Opcodes::forApi) ?: Opcodes.getDefault()
    }
}
