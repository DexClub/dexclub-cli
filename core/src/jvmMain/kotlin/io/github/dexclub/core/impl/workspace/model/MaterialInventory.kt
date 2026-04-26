package io.github.dexclub.core.impl.workspace.model

import io.github.dexclub.core.api.shared.InventoryCounts

internal data class MaterialInventory(
    val apkFiles: List<String> = emptyList(),
    val dexFiles: List<String> = emptyList(),
    val manifestFiles: List<String> = emptyList(),
    val arscFiles: List<String> = emptyList(),
    val binaryXmlFiles: List<String> = emptyList(),
) {
    fun isEmpty(): Boolean =
        apkFiles.isEmpty() &&
            dexFiles.isEmpty() &&
            manifestFiles.isEmpty() &&
            arscFiles.isEmpty() &&
            binaryXmlFiles.isEmpty()

    fun counts(): InventoryCounts =
        InventoryCounts(
            apkCount = apkFiles.size,
            dexCount = dexFiles.size,
            manifestCount = manifestFiles.size,
            arscCount = arscFiles.size,
            binaryXmlCount = binaryXmlFiles.size,
        )
}
