package io.github.dexclub.cli

import io.github.dexclub.core.api.dex.DexQueryError
import io.github.dexclub.core.api.dex.DexInspectError
import io.github.dexclub.core.api.dex.DexExportError
import io.github.dexclub.core.api.shared.CapabilityError
import io.github.dexclub.core.api.shared.Operation
import io.github.dexclub.core.api.shared.Services
import io.github.dexclub.core.api.shared.createDefaultServices
import io.github.dexclub.core.api.resource.ResourceDecodeError
import io.github.dexclub.core.api.workspace.WorkspaceInitError
import io.github.dexclub.core.api.workspace.WorkspaceResolveError
import io.github.dexclub.core.api.workspace.WorkspaceResolveErrorReason
import java.nio.file.Paths

class CliApp(
    private val services: Services = createDefaultServices(),
    private val cwdProvider: () -> String = {
        Paths.get("").toAbsolutePath().normalize().toString()
    },
) {
    private val parser = CliParser()
    private val workdirResolver = WorkdirResolver(cwdProvider)
    private val queryTextLoader = QueryTextLoader()
    private val dispatcher = CommandDispatcher(
        workspace = WorkspaceCommandAdapter(services, workdirResolver),
        inspect = InspectCommandAdapter(services, workdirResolver),
        resource = ResourceCommandAdapter(services, queryTextLoader, workdirResolver),
        dexSearch = DexSearchCommandAdapter(services, queryTextLoader, workdirResolver),
        export = ExportCommandAdapter(services, workdirResolver),
    )
    private val renderer = Renderer()
    private val outputWriter = OutputWriter()

    fun run(argv: List<String>, stdout: Appendable, stderr: Appendable): Int {
        var parsedRequest: CliRequest? = null
        val rendered = try {
            parsedRequest = parser.parse(argv)
            val commandResult = dispatcher.dispatch(parsedRequest)
            renderer.render(commandResult)
        } catch (error: CliUsageError) {
            renderer.renderCliUsageError(error)
        } catch (error: WorkspaceResolveError) {
            renderer.renderWorkspaceError(
                message = error.message ?: "Workspace operation failed",
                hint = when (error.reason) {
                    WorkspaceResolveErrorReason.NotInitialized ->
                        "Run 'cli init <input>' to create a workspace."

                    WorkspaceResolveErrorReason.MissingBoundInput ->
                        "Run 'cli status' for workspace details."

                    else -> null
                },
            )
        } catch (error: WorkspaceInitError) {
            renderer.renderWorkspaceError(
                message = error.message ?: "Workspace initialization failed",
                hint = null,
            )
        } catch (error: CapabilityError) {
            renderer.renderWorkspaceError(
                message = "command '${parsedRequest?.toCommandName() ?: error.operation.toCommandName()}' is not supported by the current workspace (kind=${error.kind})",
                hint = null,
            )
        } catch (error: DexQueryError) {
            renderer.renderWorkspaceError(
                message = error.message ?: "Dex query failed",
                hint = null,
            )
        } catch (error: DexInspectError) {
            renderer.renderWorkspaceError(
                message = error.message ?: "Method inspection failed",
                hint = null,
            )
        } catch (error: DexExportError) {
            renderer.renderWorkspaceError(
                message = error.message ?: "Dex export failed",
                hint = null,
            )
        } catch (error: ResourceDecodeError) {
            renderer.renderWorkspaceError(
                message = error.message,
                hint = null,
            )
        } catch (error: Throwable) {
            renderer.renderWorkspaceError(
                message = error.message ?: "Unexpected failure",
                hint = null,
            )
        }

        outputWriter.write(rendered, stdout, stderr)
        return rendered.exitCode
    }
}

private fun CliRequest.toCommandName(): String =
    when (this) {
        is CliRequest.Help -> "help"
        is CliRequest.Version -> "version"
        is CliRequest.Init -> "init"
        is CliRequest.Status -> "status"
        is CliRequest.Gc -> "gc"
        is CliRequest.Inspect -> "inspect"
        is CliRequest.InspectMethod -> "inspect-method"
        is CliRequest.Manifest -> "manifest"
        is CliRequest.ResTable -> "res-table"
        is CliRequest.DecodeXml -> "decode-xml"
        is CliRequest.ListRes -> "list-res"
        is CliRequest.ResolveRes -> "resolve-res"
        is CliRequest.FindRes -> "find-res"
        is CliRequest.FindClass -> "find-class"
        is CliRequest.FindMethod -> "find-method"
        is CliRequest.FindField -> "find-field"
        is CliRequest.FindClassUsingStrings -> "find-class-using-strings"
        is CliRequest.FindMethodUsingStrings -> "find-method-using-strings"
        is CliRequest.ExportClassDex -> "export-class-dex"
        is CliRequest.ExportClassJava -> "export-class-java"
        is CliRequest.ExportClassSmali -> "export-class-smali"
        is CliRequest.ExportMethodSmali -> "export-method-smali"
        is CliRequest.ExportMethodDex -> "export-method-dex"
    }

private fun Operation.toCommandName(): String =
    when (this) {
        Operation.Inspect -> "inspect"
        Operation.FindClass -> "find-class"
        Operation.FindMethod -> "find-method"
        Operation.FindField -> "find-field"
        Operation.ExportDex -> "export-class-dex"
        Operation.ExportSmali -> "export-class-smali"
        Operation.ExportJava -> "export-class-java"
        Operation.ManifestDecode -> "manifest"
        Operation.ResourceTableDecode -> "res-table"
        Operation.XmlDecode -> "decode-xml"
        Operation.ResourceEntryList -> "list-res"
    }
