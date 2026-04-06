package io.github.dexclub.core.export

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import io.github.dexclub.core.config.DexFormatConfig
import io.github.dexclub.core.session.DexSession
import java.io.File

internal class DexBinaryExportService(
    private val session: DexSession,
    private val dexFormatConfig: DexFormatConfig = DexFormatConfig(),
) {
    @Throws(IllegalArgumentException::class)
    fun exportSingleDex(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String {
        if (className.trim().isEmpty()) {
            throw IllegalArgumentException("className must not be empty")
        }

        val classDef = session.requireClassDef(
            className = className,
            dexPath = dexPath,
        )

        val dataStore = MemoryDataStore()
        val dexPool = DexPool(dexFormatConfig.toOpcodes())
        dexPool.internClass(classDef)
        dexPool.writeTo(dataStore)

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(dataStore.data)
        return outputFile.absolutePath
    }

    private fun DexFormatConfig.toOpcodes(): Opcodes {
        return opcodeApiLevel?.let(Opcodes::forApi) ?: Opcodes.getDefault()
    }
}
