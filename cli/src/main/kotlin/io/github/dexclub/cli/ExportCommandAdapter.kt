package io.github.dexclub.cli

import io.github.dexclub.core.api.dex.ExportClassSmaliRequest
import io.github.dexclub.core.api.dex.ExportClassDexRequest
import io.github.dexclub.core.api.dex.ExportClassJavaRequest
import io.github.dexclub.core.api.dex.ExportMethodSmaliRequest
import io.github.dexclub.core.api.shared.MethodSmaliMode
import io.github.dexclub.core.api.shared.SourceLocator
import io.github.dexclub.core.api.shared.Services

internal class ExportCommandAdapter(
    private val services: Services,
    private val workdirResolver: WorkdirResolver,
) {
    fun exportClassDex(request: CliRequest.ExportClassDex): CommandResult {
        val workspace = services.workspace.open(workdirResolver.resolve(request.workdir))
        val result = services.dex.exportClassDex(
            workspace = workspace,
            request = ExportClassDexRequest(
                className = request.className,
                source = SourceLocator(
                    sourcePath = request.sourcePath,
                    sourceEntry = request.sourceEntry,
                ),
                outputPath = request.output,
            ),
        )
        return CommandResult(
            payload = RenderPayload.Export(ExportView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun exportClassJava(request: CliRequest.ExportClassJava): CommandResult {
        val workspace = services.workspace.open(workdirResolver.resolve(request.workdir))
        val result = services.dex.exportClassJava(
            workspace = workspace,
            request = ExportClassJavaRequest(
                className = request.className,
                source = SourceLocator(
                    sourcePath = request.sourcePath,
                    sourceEntry = request.sourceEntry,
                ),
                outputPath = request.output,
            ),
        )
        return CommandResult(
            payload = RenderPayload.Export(ExportView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun exportClassSmali(request: CliRequest.ExportClassSmali): CommandResult {
        val workspace = services.workspace.open(workdirResolver.resolve(request.workdir))
        val result = services.dex.exportClassSmali(
            workspace = workspace,
            request = ExportClassSmaliRequest(
                className = request.className,
                source = SourceLocator(
                    sourcePath = request.sourcePath,
                    sourceEntry = request.sourceEntry,
                ),
                outputPath = request.output,
                autoUnicodeDecode = request.autoUnicodeDecode,
            ),
        )
        return CommandResult(
            payload = RenderPayload.Export(ExportView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
    }

    fun exportMethodSmali(request: CliRequest.ExportMethodSmali): CommandResult {
        val workspace = services.workspace.open(workdirResolver.resolve(request.workdir))
        val result = services.dex.exportMethodSmali(
            workspace = workspace,
            request = ExportMethodSmaliRequest(
                methodSignature = request.methodSignature,
                source = SourceLocator(
                    sourcePath = request.sourcePath,
                    sourceEntry = request.sourceEntry,
                ),
                outputPath = request.output,
                autoUnicodeDecode = request.autoUnicodeDecode,
                mode = when (request.mode) {
                    "snippet" -> MethodSmaliMode.Snippet
                    "class" -> MethodSmaliMode.Class
                    else -> error("unsupported method smali mode: ${request.mode}")
                },
            ),
        )
        return CommandResult(
            payload = RenderPayload.Export(ExportView.from(result)),
            outputFormat = request.outputFormat,
            exitCode = 0,
        )
        }
    }
