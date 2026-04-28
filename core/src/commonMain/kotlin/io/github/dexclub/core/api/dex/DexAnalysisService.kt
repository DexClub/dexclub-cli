package io.github.dexclub.core.api.dex

import io.github.dexclub.core.api.workspace.WorkspaceContext

interface DexAnalysisService {
    fun findClasses(workspace: WorkspaceContext, request: FindClassesRequest): List<ClassHit>

    fun findMethods(workspace: WorkspaceContext, request: FindMethodsRequest): List<MethodHit>

    fun findFields(workspace: WorkspaceContext, request: FindFieldsRequest): List<FieldHit>

    fun findClassesUsingStrings(
        workspace: WorkspaceContext,
        request: FindClassesUsingStringsRequest,
    ): List<ClassHit>

    fun findMethodsUsingStrings(
        workspace: WorkspaceContext,
        request: FindMethodsUsingStringsRequest,
    ): List<MethodHit>

    fun inspectMethod(workspace: WorkspaceContext, request: InspectMethodRequest): MethodDetail

    fun exportClassDex(workspace: WorkspaceContext, request: ExportClassDexRequest): ExportResult

    fun exportClassSmali(workspace: WorkspaceContext, request: ExportClassSmaliRequest): ExportResult

    fun exportClassJava(workspace: WorkspaceContext, request: ExportClassJavaRequest): ExportResult

    fun exportMethodSmali(workspace: WorkspaceContext, request: ExportMethodSmaliRequest): ExportResult
}
