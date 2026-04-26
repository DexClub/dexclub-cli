package io.github.dexclub.core.impl.dex

import io.github.dexclub.core.api.shared.CapabilityError
import io.github.dexclub.core.api.shared.Operation
import io.github.dexclub.core.api.workspace.WorkspaceContext

internal class CapabilityChecker {
    fun require(workspace: WorkspaceContext, operation: Operation) {
        val capabilities = workspace.snapshot.capabilities
        when (operation) {
            Operation.Inspect ->
                requireCapability(capabilities.inspect, operation, "inspect", workspace)
            Operation.FindClass ->
                requireCapability(capabilities.findClass, operation, "findClass", workspace)
            Operation.FindMethod ->
                requireCapability(capabilities.findMethod, operation, "findMethod", workspace)
            Operation.FindField ->
                requireCapability(capabilities.findField, operation, "findField", workspace)
            Operation.ExportDex ->
                requireCapability(capabilities.exportDex, operation, "exportDex", workspace)
            Operation.ExportSmali ->
                requireCapability(capabilities.exportSmali, operation, "exportSmali", workspace)
            Operation.ExportJava ->
                requireCapability(capabilities.exportJava, operation, "exportJava", workspace)
            Operation.ManifestDecode ->
                requireCapability(capabilities.manifestDecode, operation, "manifestDecode", workspace)
            Operation.ResourceTableDecode ->
                requireCapability(capabilities.resourceTableDecode, operation, "resourceTableDecode", workspace)
            Operation.XmlDecode ->
                requireCapability(capabilities.xmlDecode, operation, "xmlDecode", workspace)
            Operation.ResourceEntryList ->
                requireCapability(capabilities.resourceEntryList, operation, "resourceEntryList", workspace)
        }
    }

    private fun requireCapability(
        supported: Boolean,
        operation: Operation,
        requiredCapability: String,
        workspace: WorkspaceContext,
    ) {
        if (!supported) {
            throw CapabilityError(
                operation = operation,
                requiredCapability = requiredCapability,
                kind = workspace.snapshot.kind.name.lowercase(),
            )
        }
    }
}
