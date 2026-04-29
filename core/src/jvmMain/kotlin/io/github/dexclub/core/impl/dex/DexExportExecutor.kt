package io.github.dexclub.core.impl.dex

import io.github.dexclub.core.api.dex.ExportClassDexRequest
import io.github.dexclub.core.api.dex.ExportClassJavaRequest
import io.github.dexclub.core.api.dex.ExportClassSmaliRequest
import io.github.dexclub.core.api.dex.ExportMethodDexRequest
import io.github.dexclub.core.api.dex.ExportMethodJavaRequest
import io.github.dexclub.core.api.dex.ExportMethodSmaliRequest
import io.github.dexclub.core.api.dex.ExportResult
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory

internal interface DexExportExecutor {
    fun exportClassDex(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportClassDexRequest,
    ): ExportResult

    fun exportClassSmali(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportClassSmaliRequest,
    ): ExportResult

    fun exportClassJava(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportClassJavaRequest,
    ): ExportResult

    fun exportMethodSmali(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportMethodSmaliRequest,
    ): ExportResult

    fun exportMethodDex(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportMethodDexRequest,
    ): ExportResult

    fun exportMethodJava(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportMethodJavaRequest,
    ): ExportResult
}
