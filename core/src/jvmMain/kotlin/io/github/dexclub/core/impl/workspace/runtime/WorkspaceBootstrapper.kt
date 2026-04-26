package io.github.dexclub.core.impl.workspace.runtime

import io.github.dexclub.core.api.workspace.WorkspaceInitError
import io.github.dexclub.core.api.workspace.WorkspaceInitErrorReason
import io.github.dexclub.core.impl.workspace.model.PreparedWorkspace
import io.github.dexclub.core.impl.workspace.model.TargetRecord
import io.github.dexclub.core.impl.workspace.model.WorkspaceRecord
import java.time.Instant
import java.util.UUID

internal class WorkspaceBootstrapper(
    private val inputResolver: WorkspaceInputResolver,
    private val inventoryScanner: InventoryScanner,
    private val snapshotBuilder: SnapshotBuilder,
    private val toolVersion: String = "dev",
    private val nowProvider: () -> Instant = { Instant.now() },
    private val workspaceIdProvider: () -> String = { UUID.randomUUID().toString() },
) {
    fun prepare(input: String): PreparedWorkspace {
        val resolved = inputResolver.resolveInitializationInput(input)
        val now = nowProvider().toString()
        val target = TargetRecord(
            targetId = resolved.targetId,
            createdAt = now,
            updatedAt = now,
            inputType = resolved.inputType,
            inputPath = resolved.inputPath,
        )
        val inventory = inventoryScanner.scan(resolved.workdirPath, target)
        if (inventory.isEmpty()) {
            throw WorkspaceInitError(
                reason = WorkspaceInitErrorReason.NoRecognizableMaterials,
                path = resolved.absoluteInputPath.toString(),
                message = "Initialization failed: input file is not a supported material: ${resolved.absoluteInputPath}",
            )
        }
        val snapshot = snapshotBuilder.build(resolved.workdirPath, target, inventory)
        val workspace = WorkspaceRecord(
            workspaceId = workspaceIdProvider(),
            createdAt = now,
            updatedAt = now,
            toolVersion = toolVersion,
            activeTargetId = target.targetId,
        )
        return PreparedWorkspace(
            ref = resolved.ref,
            workspace = workspace,
            target = target,
            snapshot = snapshot,
        )
    }
}
