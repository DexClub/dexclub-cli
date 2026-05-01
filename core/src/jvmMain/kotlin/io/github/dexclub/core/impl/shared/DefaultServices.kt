package io.github.dexclub.core.api.shared

import io.github.dexclub.core.api.dex.DexAnalysisService
import io.github.dexclub.core.api.resource.ResourceService
import io.github.dexclub.core.api.workspace.WorkspaceService
import io.github.dexclub.core.impl.dex.CapabilityChecker
import io.github.dexclub.core.impl.dex.DefaultDexAnalysisService
import io.github.dexclub.core.impl.dex.DefaultDexExportExecutor
import io.github.dexclub.core.impl.dex.DefaultDexSearchExecutor
import io.github.dexclub.core.impl.dex.DexQueryParser
import io.github.dexclub.core.impl.resource.DefaultManifestExecutor
import io.github.dexclub.core.impl.resource.DefaultResourceEntryListExecutor
import io.github.dexclub.core.impl.resource.DefaultResourceService
import io.github.dexclub.core.impl.resource.DefaultResourceTableExecutor
import io.github.dexclub.core.impl.resource.DefaultResourceValueExecutor
import io.github.dexclub.core.impl.resource.DefaultXmlExecutor
import io.github.dexclub.core.impl.resource.ResourceSearchQueryParser
import io.github.dexclub.core.impl.resource.ResourceTableLoader
import io.github.dexclub.core.impl.shared.CoreBuildInfo
import io.github.dexclub.core.impl.workspace.runtime.CapabilityResolver
import io.github.dexclub.core.impl.workspace.runtime.DefaultWorkspaceRuntimeResolver
import io.github.dexclub.core.impl.workspace.runtime.InventoryScanner
import io.github.dexclub.core.impl.workspace.runtime.SnapshotBuilder
import io.github.dexclub.core.impl.workspace.runtime.WorkspaceBootstrapper
import io.github.dexclub.core.impl.workspace.runtime.WorkspaceInputResolver
import io.github.dexclub.core.impl.workspace.store.DefaultWorkspaceStore
import io.github.dexclub.core.impl.workspace.DefaultWorkspaceService

actual fun createDefaultServices(): Services {
    val toolVersion = CoreBuildInfo.version
    val inputResolver = WorkspaceInputResolver()
    val capabilityResolver = CapabilityResolver()
    val snapshotBuilder = SnapshotBuilder(
        capabilityResolver = capabilityResolver,
    )
    val inventoryScanner = InventoryScanner(inputResolver)
    val store = DefaultWorkspaceStore()
    val runtimeResolver = DefaultWorkspaceRuntimeResolver(
        store = store,
        inputResolver = inputResolver,
        inventoryScanner = inventoryScanner,
        snapshotBuilder = snapshotBuilder,
        toolVersion = toolVersion,
    )
    val bootstrapper = WorkspaceBootstrapper(
        inputResolver = inputResolver,
        inventoryScanner = inventoryScanner,
        snapshotBuilder = snapshotBuilder,
        toolVersion = toolVersion,
    )
    val workspace: WorkspaceService = DefaultWorkspaceService(
        store = store,
        bootstrapper = bootstrapper,
        runtimeResolver = runtimeResolver,
        inputResolver = inputResolver,
    )
    val capabilityChecker = CapabilityChecker()
    val resourceTableLoader = ResourceTableLoader()
    val dex: DexAnalysisService = DefaultDexAnalysisService(
        store = store,
        capabilityChecker = capabilityChecker,
        queryParser = DexQueryParser(),
        searchExecutor = DefaultDexSearchExecutor(),
        exportExecutor = DefaultDexExportExecutor(store, toolVersion),
    )
    val resource: ResourceService = DefaultResourceService(
        store = store,
        capabilityChecker = capabilityChecker,
        manifestExecutor = DefaultManifestExecutor(store, toolVersion),
        resourceTableExecutor = DefaultResourceTableExecutor(store, resourceTableLoader, toolVersion),
        xmlExecutor = DefaultXmlExecutor(store, toolVersion),
        resourceEntryListExecutor = DefaultResourceEntryListExecutor(store, toolVersion),
        resourceValueExecutor = DefaultResourceValueExecutor(store, resourceTableLoader, ResourceSearchQueryParser(), toolVersion),
    )
    return Services(
        workspace = workspace,
        dex = dex,
        resource = resource,
    )
}
