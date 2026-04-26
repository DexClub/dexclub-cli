package io.github.dexclub.core.impl.workspace.runtime

import io.github.dexclub.core.api.shared.InputType
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.core.impl.workspace.model.TargetRecord
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

internal class InventoryScanner(
    private val inputResolver: WorkspaceInputResolver,
) {
    fun scan(workdirPath: Path, target: TargetRecord): MaterialInventory {
        val inputPath = inputResolver.resolveBoundInput(workdirPath, target.inputPath, target.inputType)
        return scanFile(workdirPath, inputPath)
    }

    private fun scanFile(workdirPath: Path, inputPath: Path): MaterialInventory {
        val apkFiles = mutableListOf<String>()
        val dexFiles = mutableListOf<String>()
        val manifestFiles = mutableListOf<String>()
        val arscFiles = mutableListOf<String>()
        val binaryXmlFiles = mutableListOf<String>()
        val relative = inputResolver.normalizeRelativePath(workdirPath.relativize(inputPath))
        classifyFile(relative, inputPath, apkFiles, dexFiles, manifestFiles, arscFiles, binaryXmlFiles)
        return MaterialInventory(
            apkFiles = apkFiles,
            dexFiles = dexFiles,
            manifestFiles = manifestFiles,
            arscFiles = arscFiles,
            binaryXmlFiles = binaryXmlFiles,
        )
    }

    private fun classifyFile(
        relativePath: String,
        file: Path,
        apkFiles: MutableList<String>,
        dexFiles: MutableList<String>,
        manifestFiles: MutableList<String>,
        arscFiles: MutableList<String>,
        binaryXmlFiles: MutableList<String>,
    ) {
        val name = file.fileName.toString()
        when {
            name.endsWith(".apk", ignoreCase = true) -> apkFiles += relativePath
            name.endsWith(".dex", ignoreCase = true) -> dexFiles += relativePath
            name == "AndroidManifest.xml" -> manifestFiles += relativePath
            name == "resources.arsc" -> arscFiles += relativePath
            name.endsWith(".xml", ignoreCase = true) -> binaryXmlFiles += relativePath
        }
    }
}
