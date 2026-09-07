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
    val toolVersion = CoreBuildInfo.VERSION
    val store = DefaultWorkspaceStore()
    val workspaceDependencies = createWorkspaceDependencies(store, toolVersion)
    val workspace = createWorkspaceService(store, workspaceDependencies)
    val dex = createDexAnalysisService(store, toolVersion)
    val resource = createResourceService(store, toolVersion)
    return Services(
        workspace = workspace,
        dex = dex,
        resource = resource,
    )
}

private data class WorkspaceDependencies(
    val inputResolver: WorkspaceInputResolver,
    val inventoryScanner: InventoryScanner,
    val snapshotBuilder: SnapshotBuilder,
    val runtimeResolver: DefaultWorkspaceRuntimeResolver,
    val bootstrapper: WorkspaceBootstrapper,
)

private fun createWorkspaceDependencies(
    store: DefaultWorkspaceStore,
    toolVersion: String,
): WorkspaceDependencies {
    val inputResolver = WorkspaceInputResolver()
    val inventoryScanner = InventoryScanner(inputResolver)
    val snapshotBuilder = SnapshotBuilder(
        capabilityResolver = CapabilityResolver(),
    )
    return WorkspaceDependencies(
        inputResolver = inputResolver,
        inventoryScanner = inventoryScanner,
        snapshotBuilder = snapshotBuilder,
        runtimeResolver = DefaultWorkspaceRuntimeResolver(
            store = store,
            inputResolver = inputResolver,
            inventoryScanner = inventoryScanner,
            snapshotBuilder = snapshotBuilder,
            toolVersion = toolVersion,
        ),
        bootstrapper = WorkspaceBootstrapper(
            inputResolver = inputResolver,
            inventoryScanner = inventoryScanner,
            snapshotBuilder = snapshotBuilder,
            toolVersion = toolVersion,
        ),
    )
}

private fun createWorkspaceService(
    store: DefaultWorkspaceStore,
    dependencies: WorkspaceDependencies,
): WorkspaceService =
    DefaultWorkspaceService(
        store = store,
        bootstrapper = dependencies.bootstrapper,
        runtimeResolver = dependencies.runtimeResolver,
        inputResolver = dependencies.inputResolver,
    )

private fun createDexAnalysisService(
    store: DefaultWorkspaceStore,
    toolVersion: String,
): DexAnalysisService {
    val capabilityChecker = CapabilityChecker()
    return DefaultDexAnalysisService(
        store = store,
        capabilityChecker = capabilityChecker,
        queryParser = DexQueryParser(),
        searchExecutor = DefaultDexSearchExecutor(store),
        exportExecutor = DefaultDexExportExecutor(store, toolVersion),
    )
}

private fun createResourceService(
    store: DefaultWorkspaceStore,
    toolVersion: String,
): ResourceService {
    val capabilityChecker = CapabilityChecker()
    val resourceTableLoader = ResourceTableLoader()
    return DefaultResourceService(
        store = store,
        capabilityChecker = capabilityChecker,
        manifestExecutor = DefaultManifestExecutor(store, toolVersion),
        resourceTableExecutor = DefaultResourceTableExecutor(store, resourceTableLoader, toolVersion),
        xmlExecutor = DefaultXmlExecutor(store, toolVersion),
        resourceEntryListExecutor = DefaultResourceEntryListExecutor(store, toolVersion),
        resourceValueExecutor = DefaultResourceValueExecutor(
            store,
            resourceTableLoader,
            ResourceSearchQueryParser(),
            toolVersion,
        ),
    )
}
