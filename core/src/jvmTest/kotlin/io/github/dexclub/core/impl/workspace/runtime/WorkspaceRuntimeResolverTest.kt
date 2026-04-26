package io.github.dexclub.core.impl.workspace.runtime

import io.github.dexclub.core.api.shared.WorkspaceState
import io.github.dexclub.core.api.shared.WorkspaceKind
import io.github.dexclub.core.api.workspace.WorkspaceRef
import io.github.dexclub.core.api.workspace.WorkspaceResolveError
import io.github.dexclub.core.api.workspace.WorkspaceResolveErrorReason
import io.github.dexclub.core.impl.workspace.store.DefaultWorkspaceStore
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkspaceRuntimeResolverTest {
    @Test
    fun openRefreshesSnapshotAndPersistsNewSummary() {
        val workspaceDir = createTempDirectory("dexclub-core-open")
        val dexFile = workspaceDir.resolve("1.dex")
        dexFile.writeText("before")

        val env = testEnvironment()
        val prepared = env.bootstrapper.prepare(dexFile.toString())
        env.store.initialize(prepared.ref.workdir, prepared.workspace, prepared.target, prepared.snapshot)
        val before = env.store.loadSnapshot(workspaceDir.toString(), prepared.target.targetId)

        dexFile.writeText("after")

        val context = env.runtimeResolver.open(WorkspaceRef(workspaceDir.toString()))

        assertEquals(WorkspaceKind.Dex, context.snapshot.kind)
        assertEquals(1, context.snapshot.inventoryCounts.dexCount)

        val persisted = env.store.loadSnapshot(workspaceDir.toString(), prepared.target.targetId)
        assertNotNull(persisted)
        assertEquals(WorkspaceKind.Dex, persisted.kind)
        assertEquals(1, persisted.inventory.counts().dexCount)
        assertTrue(before != persisted)
    }

    @Test
    fun openUpgradesWorkspaceToolVersion() {
        val workspaceDir = createTempDirectory("dexclub-core-tool-version")
        val dexFile = workspaceDir.resolve("1.dex")
        dexFile.writeText("")

        val env = testEnvironment(
            bootstrapToolVersion = "old",
            runtimeToolVersion = "new",
        )
        val prepared = env.bootstrapper.prepare(dexFile.toString())
        env.store.initialize(prepared.ref.workdir, prepared.workspace, prepared.target, prepared.snapshot)

        env.runtimeResolver.open(WorkspaceRef(workspaceDir.toString()))

        val persistedWorkspace = env.store.loadWorkspace(workspaceDir.toString())
        assertEquals("new", persistedWorkspace.toolVersion)
    }

    @Test
    fun loadStatusKeepsSnapshotReadOnlyAndReportsStaleState() {
        val workspaceDir = createTempDirectory("dexclub-core-status")
        val dexFile = workspaceDir.resolve("1.dex")
        dexFile.writeText("before")

        val env = testEnvironment()
        val prepared = env.bootstrapper.prepare(dexFile.toString())
        env.store.initialize(prepared.ref.workdir, prepared.workspace, prepared.target, prepared.snapshot)
        val before = env.store.loadSnapshot(workspaceDir.toString(), prepared.target.targetId)

        dexFile.writeText("after")

        val status = env.runtimeResolver.loadStatus(WorkspaceRef(workspaceDir.toString()))
        val after = env.store.loadSnapshot(workspaceDir.toString(), prepared.target.targetId)

        assertEquals(WorkspaceState.Degraded, status.state)
        assertEquals(prepared.workspace.workspaceId, status.workspaceId)
        assertEquals(prepared.target.targetId, status.activeTargetId)
        assertEquals(WorkspaceKind.Dex, status.snapshot?.kind)
        assertTrue(status.issues.any { it.code == "stale_snapshot" })
        assertEquals(before, after)
    }

    @Test
    fun loadStatusReportsMissingInputAndOpenFails() {
        val workspaceDir = createTempDirectory("dexclub-core-broken")
        val apkFile = workspaceDir.resolve("app.apk")
        apkFile.writeText("")

        val env = testEnvironment()
        val prepared = env.bootstrapper.prepare(apkFile.toString())
        env.store.initialize(prepared.ref.workdir, prepared.workspace, prepared.target, prepared.snapshot)

        apkFile.deleteExisting()

        val status = env.runtimeResolver.loadStatus(WorkspaceRef(workspaceDir.toString()))
        assertEquals(WorkspaceState.Broken, status.state)
        assertEquals(prepared.workspace.workspaceId, status.workspaceId)
        assertEquals(prepared.target.targetId, status.activeTargetId)
        assertTrue(status.issues.any { it.code == "missing_input" })
        assertNotNull(status.snapshot)

        val error = assertFailsWith<WorkspaceResolveError> {
            env.runtimeResolver.open(WorkspaceRef(workspaceDir.toString()))
        }
        assertEquals(WorkspaceResolveErrorReason.MissingBoundInput, error.reason)
    }

    @Test
    fun clearTargetCacheDeletesOnlyCacheContents() {
        val workspaceDir = createTempDirectory("dexclub-core-gc")
        val dexFile = workspaceDir.resolve("1.dex")
        dexFile.writeText("")

        val env = testEnvironment()
        val prepared = env.bootstrapper.prepare(dexFile.toString())
        env.store.initialize(prepared.ref.workdir, prepared.workspace, prepared.target, prepared.snapshot)

        val cacheRoot = workspaceDir.resolve(".dexclub/targets").resolve(prepared.target.targetId).resolve("cache")
        val decodedFile = cacheRoot.resolve("decoded/manifest.json")
        val indexFile = cacheRoot.resolve("indexes/search.idx")
        decodedFile.parent.createDirectories()
        indexFile.parent.createDirectories()
        decodedFile.writeText("abc")
        indexFile.writeText("12345")

        val result = env.store.clearTargetCache(workspaceDir.toString(), prepared.target.targetId)

        assertEquals(2, result.deletedFiles)
        assertEquals(8L, result.deletedBytes)
        assertTrue(cacheRoot.resolve("decoded").exists())
        assertTrue(cacheRoot.resolve("indexes").exists())
        assertTrue(!decodedFile.exists())
        assertTrue(!indexFile.exists())
    }

    private fun testEnvironment(
        bootstrapToolVersion: String = "test",
        runtimeToolVersion: String = bootstrapToolVersion,
    ): TestEnvironment {
        val inputResolver = WorkspaceInputResolver()
        val capabilityResolver = CapabilityResolver()
        val snapshotBuilder = SnapshotBuilder(
            capabilityResolver = capabilityResolver,
        )
        val inventoryScanner = InventoryScanner(inputResolver)
        val store = DefaultWorkspaceStore()
        return TestEnvironment(
            store = store,
            bootstrapper = WorkspaceBootstrapper(
                inputResolver = inputResolver,
                inventoryScanner = inventoryScanner,
                snapshotBuilder = snapshotBuilder,
                toolVersion = bootstrapToolVersion,
            ),
            runtimeResolver = DefaultWorkspaceRuntimeResolver(
                store = store,
                inputResolver = inputResolver,
                inventoryScanner = inventoryScanner,
                snapshotBuilder = snapshotBuilder,
                toolVersion = runtimeToolVersion,
            ),
        )
    }

    private data class TestEnvironment(
        val store: DefaultWorkspaceStore,
        val bootstrapper: WorkspaceBootstrapper,
        val runtimeResolver: DefaultWorkspaceRuntimeResolver,
    )
}

