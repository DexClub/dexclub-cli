package io.github.dexclub.cli

internal class CommandDispatcher(
    private val workspace: WorkspaceCommandAdapter,
    private val inspect: InspectCommandAdapter,
    private val resource: ResourceCommandAdapter,
    private val dexSearch: DexSearchCommandAdapter,
    private val export: ExportCommandAdapter,
) {
    fun dispatch(request: CliRequest): CommandResult =
        when (request) {
            is CliRequest.Help -> CommandResult(
                payload = RenderPayload.Help(CliHelp.render(request.command)),
                outputFormat = OutputFormat.Text,
                exitCode = 0,
            )
            is CliRequest.Version -> CommandResult(
                payload = RenderPayload.Version(CliBuildInfo.version),
                outputFormat = OutputFormat.Text,
                exitCode = 0,
            )
            is CliRequest.Init -> workspace.initialize(request)
            is CliRequest.Status -> workspace.loadStatus(request)
            is CliRequest.Gc -> workspace.gc(request)
            is CliRequest.Inspect -> inspect.inspect(request)
            is CliRequest.Manifest -> resource.manifest(request)
            is CliRequest.ResTable -> resource.resTable(request)
            is CliRequest.DecodeXml -> resource.decodeXml(request)
            is CliRequest.ListRes -> resource.listRes(request)
            is CliRequest.ResolveRes -> resource.resolveRes(request)
            is CliRequest.FindRes -> resource.findRes(request)
            is CliRequest.FindClass -> dexSearch.findClass(request)
            is CliRequest.FindMethod -> dexSearch.findMethod(request)
            is CliRequest.FindField -> dexSearch.findField(request)
            is CliRequest.FindClassUsingStrings -> dexSearch.findClassUsingStrings(request)
            is CliRequest.FindMethodUsingStrings -> dexSearch.findMethodUsingStrings(request)
            is CliRequest.ExportClassDex -> export.exportClassDex(request)
            is CliRequest.ExportClassJava -> export.exportClassJava(request)
            is CliRequest.ExportClassSmali -> export.exportClassSmali(request)
            is CliRequest.ExportMethodSmali -> export.exportMethodSmali(request)
        }
}
