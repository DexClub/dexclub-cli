package io.github.dexclub.cli

import io.github.dexclub.core.api.dex.FindClassesRequest
import io.github.dexclub.core.api.dex.FindClassesUsingStringsRequest
import io.github.dexclub.core.api.dex.FindFieldsRequest
import io.github.dexclub.core.api.dex.FindMethodsRequest
import io.github.dexclub.core.api.dex.FindMethodsUsingStringsRequest
import io.github.dexclub.core.api.shared.Services
import io.github.dexclub.core.api.workspace.WorkspaceRef

internal class DexSearchCommandAdapter(
    private val services: Services,
    private val queryTextLoader: QueryTextLoader,
    private val workdirResolver: WorkdirResolver,
) {
    fun findClass(request: CliRequest.FindClass): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(WorkspaceRef(workspaceRef.workdir))
        val queryText = queryTextLoader.load(request.query, CliUsages.findClass)
        val result = services.dex.findClasses(
            workspace = workspace,
            request = FindClassesRequest(
                queryText = queryText,
                window = request.window,
            ),
        )
        return CommandResult(
            payload = RenderPayload.ClassHits(result.map(ClassHitView::from)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun findMethod(request: CliRequest.FindMethod): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(WorkspaceRef(workspaceRef.workdir))
        val queryText = queryTextLoader.load(request.query, CliUsages.findMethod)
        val result = services.dex.findMethods(
            workspace = workspace,
            request = FindMethodsRequest(
                queryText = queryText,
                window = request.window,
            ),
        )
        return CommandResult(
            payload = RenderPayload.MethodHits(result.map(MethodHitView::from)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun findField(request: CliRequest.FindField): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(WorkspaceRef(workspaceRef.workdir))
        val queryText = queryTextLoader.load(request.query, CliUsages.findField)
        val result = services.dex.findFields(
            workspace = workspace,
            request = FindFieldsRequest(
                queryText = queryText,
                window = request.window,
            ),
        )
        return CommandResult(
            payload = RenderPayload.FieldHits(result.map(FieldHitView::from)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun findClassUsingStrings(request: CliRequest.FindClassUsingStrings): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(WorkspaceRef(workspaceRef.workdir))
        val queryText = queryTextLoader.load(request.query, CliUsages.findClassUsingStrings)
        val result = services.dex.findClassesUsingStrings(
            workspace = workspace,
            request = FindClassesUsingStringsRequest(
                queryText = queryText,
                window = request.window,
            ),
        )
        return CommandResult(
            payload = RenderPayload.ClassHits(result.map(ClassHitView::from)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun findMethodUsingStrings(request: CliRequest.FindMethodUsingStrings): CommandResult {
        val workspaceRef = workdirResolver.resolve(request.workdir)
        val workspace = services.workspace.open(WorkspaceRef(workspaceRef.workdir))
        val queryText = queryTextLoader.load(request.query, CliUsages.findMethodUsingStrings)
        val result = services.dex.findMethodsUsingStrings(
            workspace = workspace,
            request = FindMethodsUsingStringsRequest(
                queryText = queryText,
                window = request.window,
            ),
        )
        return CommandResult(
            payload = RenderPayload.MethodHits(result.map(MethodHitView::from)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }
}
