package io.github.dexclub.core.input

import io.github.dexclub.core.model.DexArchiveInfo
import io.github.dexclub.core.model.DexInputKind
import io.github.dexclub.core.model.DexInputRef
import java.io.File
import java.util.zip.ZipFile

internal class DexArchiveInspector(
    private val inputPaths: List<String>,
    private val inputFiles: List<File>,
    private val dexCountProvider: () -> Int,
    private val classCountProvider: () -> Int,
) {
    fun inspect(): DexArchiveInfo {
        val kind = inferInputKind()
        return DexArchiveInfo(
            kind = kind,
            inputs = inputPaths.map(::DexInputRef),
            dexCount = when (kind) {
                DexInputKind.Apk -> countApkDexEntries(inputFiles.single())
                DexInputKind.Dex -> dexCountProvider()
                DexInputKind.Unknown -> 0
            },
            classCount = when (kind) {
                DexInputKind.Dex -> classCountProvider()
                DexInputKind.Apk,
                DexInputKind.Unknown,
                -> null
            },
        )
    }

    fun singleDexSourcePath(): String? {
        return inputFiles.singleOrNull()
            ?.takeIf(DexInputInspector::isDex)
            ?.absolutePath
    }

    private fun inferInputKind(): DexInputKind {
        val inputFile = inputFiles.singleOrNull()
            ?: return if (inputFiles.isEmpty()) DexInputKind.Unknown else DexInputKind.Dex
        return when {
            inputFile.extension.equals("apk", ignoreCase = true) -> DexInputKind.Apk
            DexInputInspector.isDex(inputFile) -> DexInputKind.Dex
            else -> DexInputKind.Unknown
        }
    }

    private fun countApkDexEntries(apkFile: File): Int {
        return ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .count { entry -> entry.name.endsWith(".dex", ignoreCase = true) }
        }
    }
}
