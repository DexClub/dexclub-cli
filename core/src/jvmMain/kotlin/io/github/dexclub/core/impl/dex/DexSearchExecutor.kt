package io.github.dexclub.core.impl.dex

import io.github.dexclub.core.api.dex.ClassHit
import io.github.dexclub.core.api.dex.FieldHit
import io.github.dexclub.core.api.dex.MethodHit
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.FindField
import io.github.dexclub.dexkit.query.FindMethod

internal interface DexSearchExecutor {
    fun findClasses(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: FindClass,
    ): List<ClassHit>

    fun findMethods(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: FindMethod,
    ): List<MethodHit>

    fun findFields(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: FindField,
    ): List<FieldHit>

    fun findClassesUsingStrings(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: BatchFindClassUsingStrings,
    ): List<ClassHit>

    fun findMethodsUsingStrings(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: BatchFindMethodUsingStrings,
    ): List<MethodHit>
}
