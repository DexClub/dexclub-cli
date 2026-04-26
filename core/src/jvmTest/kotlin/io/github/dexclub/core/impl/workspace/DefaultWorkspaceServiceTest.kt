package io.github.dexclub.core.impl.workspace

import io.github.dexclub.core.api.shared.WorkspaceKind
import io.github.dexclub.core.api.shared.WorkspaceState
import io.github.dexclub.core.api.workspace.WorkspaceRef
import io.github.dexclub.core.impl.workspace.runtime.CapabilityResolver
import io.github.dexclub.core.impl.workspace.runtime.DefaultWorkspaceRuntimeResolver
import io.github.dexclub.core.impl.workspace.runtime.InventoryScanner
import io.github.dexclub.core.impl.workspace.runtime.SnapshotBuilder
import io.github.dexclub.core.impl.workspace.runtime.WorkspaceBootstrapper
import io.github.dexclub.core.impl.workspace.runtime.WorkspaceInputResolver
import io.github.dexclub.core.impl.workspace.store.DefaultWorkspaceStore
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultWorkspaceServiceTest {
    @Test
    fun initializeOpenLoadStatusGcAndInspectFormClosedLoop() {
        val workspaceDir = createTempDirectory("dexclub-core-service")
        val apkFile = workspaceDir.resolve("app.apk")
        apkFile.writeText("")

        val service = createService()

        val initialized = service.initialize(apkFile.toString())
        assertEquals(workspaceDir.toString(), initialized.workdir)
        assertEquals(WorkspaceKind.Apk, initialized.snapshot.kind)
        assertTrue(workspaceDir.resolve(".dexclub/workspace.json").exists())

        val opened = service.open(WorkspaceRef(workspaceDir.toString()))
        assertEquals(initialized.workspaceId, opened.workspaceId)
        assertEquals(initialized.activeTargetId, opened.activeTargetId)

        val status = service.loadStatus(WorkspaceRef(workspaceDir.toString()))
        assertEquals(WorkspaceState.Healthy, status.state)
        assertEquals(WorkspaceKind.Apk, status.snapshot?.kind)

        val cacheRoot = workspaceDir.resolve(".dexclub/targets").resolve(opened.activeTargetId).resolve("cache")
        val decodedFile = cacheRoot.resolve("decoded/manifest.json")
        decodedFile.parent.createDirectories()
        decodedFile.writeText("manifest")

        val gcResult = service.gc(opened)
        assertEquals(opened.activeTargetId, gcResult.targetId)
        assertTrue(!decodedFile.exists())

        val inspect = service.inspect(opened)
        assertEquals(opened.activeTarget, inspect.target)
        assertEquals(opened.snapshot, inspect.snapshot)
        assertNull(inspect.classCount)
    }

    @Test
    fun loadStatusReportsBrokenWhenBoundInputIsMissing() {
        val workspaceDir = createTempDirectory("dexclub-core-service-broken")
        val dexFile = workspaceDir.resolve("1.dex")
        dexFile.writeText("")

        val service = createService()
        val context = service.initialize(dexFile.toString())
        dexFile.deleteExisting()

        val status = service.loadStatus(WorkspaceRef(workspaceDir.toString()))
        assertEquals(WorkspaceState.Broken, status.state)
        assertTrue(status.issues.any { it.code == "missing_input" || it.code == "unrecognized_materials" })

        val inspect = service.inspect(context)
        assertEquals(context.snapshot, inspect.snapshot)
    }

    private fun createService(): DefaultWorkspaceService {
        val inputResolver = WorkspaceInputResolver()
        val capabilityResolver = CapabilityResolver()
        val snapshotBuilder = SnapshotBuilder(capabilityResolver)
        val inventoryScanner = InventoryScanner(inputResolver)
        val store = DefaultWorkspaceStore()
        val runtimeResolver = DefaultWorkspaceRuntimeResolver(
            store = store,
            inputResolver = inputResolver,
            inventoryScanner = inventoryScanner,
            snapshotBuilder = snapshotBuilder,
            toolVersion = "test",
        )
        val bootstrapper = WorkspaceBootstrapper(
            inputResolver = inputResolver,
            inventoryScanner = inventoryScanner,
            snapshotBuilder = snapshotBuilder,
            toolVersion = "test",
        )
        return DefaultWorkspaceService(
            store = store,
            bootstrapper = bootstrapper,
            runtimeResolver = runtimeResolver,
        )
    }
}

