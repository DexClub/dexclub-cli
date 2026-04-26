package io.github.dexclub.core.impl.workspace.runtime

import io.github.dexclub.core.api.shared.CapabilitySet
import io.github.dexclub.core.impl.workspace.model.MaterialInventory

internal class CapabilityResolver {
    fun resolve(inventory: MaterialInventory): CapabilitySet {
        val hasDexMaterials = inventory.apkFiles.isNotEmpty() || inventory.dexFiles.isNotEmpty()
        val hasManifest = inventory.apkFiles.isNotEmpty() || inventory.manifestFiles.isNotEmpty()
        val hasResourceTable = inventory.apkFiles.isNotEmpty() || inventory.arscFiles.isNotEmpty()
        val hasXml = hasManifest || inventory.binaryXmlFiles.isNotEmpty()
        val hasResourceEntries = inventory.apkFiles.isNotEmpty()
        return CapabilitySet(
            inspect = true,
            findClass = hasDexMaterials,
            findMethod = hasDexMaterials,
            findField = hasDexMaterials,
            exportDex = hasDexMaterials,
            exportSmali = hasDexMaterials,
            exportJava = hasDexMaterials,
            manifestDecode = hasManifest,
            resourceTableDecode = hasResourceTable,
            xmlDecode = hasXml,
            resourceEntryList = hasResourceEntries,
        )
    }
}
