package io.github.dexclub.cli

private data class CommandHelpSpec(
    val command: String,
    val usage: String,
    val description: String,
    val arguments: List<String> = emptyList(),
    val options: List<String> = emptyList(),
    val output: String,
    val notes: List<String> = emptyList(),
)

internal object CliHelp {
    private val helpFlags = setOf("--help", "-h")

    private val commandSpecs: Map<String, CommandHelpSpec> = listOf(
        CommandHelpSpec(
            command = "init",
            usage = CliUsages.init,
            description = "Initialize a workspace and create the managed .dexclub directory for the given input.",
            arguments = listOf("<input>  Single input file to bind as the active target."),
            output = "Text only. Prints the initialized workspace status summary.",
            notes = listOf(
                "This is the only command that may create .dexclub.",
                "The input argument is required and may not be omitted.",
                "Directory input is not supported in the first version.",
            ),
        ),
        CommandHelpSpec(
            command = "status",
            usage = CliUsages.status,
            description = "Read the current workspace status without modifying managed state.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf("--json  Render the structured status as JSON."),
            output = "Text prints workspace identity, binding summary, capability summary, cache state, and issues.",
            notes = listOf(
                "Status is read-only and does not refresh snapshot or rebuild cache.",
                "If workdir is omitted, cli uses cwd directly and does not search parent directories.",
            ),
        ),
        CommandHelpSpec(
            command = "gc",
            usage = CliUsages.gc,
            description = "Delete safe-to-rebuild derived state for the active target.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf("--json  Render the deletion summary as JSON."),
            output = "Text prints workdir, targetId, deletedFiles, and deletedBytes.",
            notes = listOf(
                "Only active-target cache directories are cleaned.",
                "Binding metadata, target metadata, and snapshot metadata are preserved.",
            ),
        ),
        CommandHelpSpec(
            command = "inspect",
            usage = CliUsages.inspect,
            description = "Print the current active target summary for the workspace.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf("--json  Render the structured summary as JSON."),
            output = "Text prints kind, input summary, inventory counts, optional classCount, and capabilities.",
            notes = listOf(
                "Inspect focuses on the active target summary rather than workspace status.",
            ),
        ),
        CommandHelpSpec(
            command = "find-class",
            usage = CliUsages.findClass,
            description = "Search classes in the active target using a DexKit JSON query.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--query-json <json>  Inline JSON query text.",
                "--query-file <file>  UTF-8 JSON query file.",
                "--offset <n>  Zero-based result offset.",
                "--limit <n>  Positive page size.",
                "--json  Render hits as JSON.",
            ),
            output = "Text prints a stable, tab-separated hit table. JSON prints the hit array directly.",
            notes = listOf(
                "--query-json and --query-file are required and mutually exclusive.",
                "Hits are stably sorted before offset and limit are applied.",
            ),
        ),
        CommandHelpSpec(
            command = "find-method",
            usage = CliUsages.findMethod,
            description = "Search methods in the active target using a DexKit JSON query.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--query-json <json>  Inline JSON query text.",
                "--query-file <file>  UTF-8 JSON query file.",
                "--offset <n>  Zero-based result offset.",
                "--limit <n>  Positive page size.",
                "--json  Render hits as JSON.",
            ),
            output = "Text prints a stable, tab-separated hit table. JSON prints the hit array directly.",
            notes = listOf(
                "--query-json and --query-file are required and mutually exclusive.",
                "Hits are stably sorted before offset and limit are applied.",
            ),
        ),
        CommandHelpSpec(
            command = "find-field",
            usage = CliUsages.findField,
            description = "Search fields in the active target using a DexKit JSON query.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--query-json <json>  Inline JSON query text.",
                "--query-file <file>  UTF-8 JSON query file.",
                "--offset <n>  Zero-based result offset.",
                "--limit <n>  Positive page size.",
                "--json  Render hits as JSON.",
            ),
            output = "Text prints a stable, tab-separated hit table. JSON prints the hit array directly.",
            notes = listOf(
                "--query-json and --query-file are required and mutually exclusive.",
                "Hits are stably sorted before offset and limit are applied.",
            ),
        ),
        CommandHelpSpec(
            command = "find-class-using-strings",
            usage = CliUsages.findClassUsingStrings,
            description = "Search classes using grouped string matcher batches.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--query-json <json>  Inline JSON query text.",
                "--query-file <file>  UTF-8 JSON query file.",
                "--offset <n>  Zero-based result offset.",
                "--limit <n>  Positive page size.",
                "--json  Render hits as JSON.",
            ),
            output = "Text prints a stable, tab-separated hit table. JSON prints the hit array directly.",
            notes = listOf(
                "Batch query groups are merged before rendering.",
                "Result rows do not expose group identity.",
            ),
        ),
        CommandHelpSpec(
            command = "find-method-using-strings",
            usage = CliUsages.findMethodUsingStrings,
            description = "Search methods using grouped string matcher batches.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--query-json <json>  Inline JSON query text.",
                "--query-file <file>  UTF-8 JSON query file.",
                "--offset <n>  Zero-based result offset.",
                "--limit <n>  Positive page size.",
                "--json  Render hits as JSON.",
            ),
            output = "Text prints a stable, tab-separated hit table. JSON prints the hit array directly.",
            notes = listOf(
                "Batch query groups are merged before rendering.",
                "Result rows do not expose group identity.",
            ),
        ),
        CommandHelpSpec(
            command = "export-class-dex",
            usage = CliUsages.exportClassDex,
            description = "Export a uniquely resolved class as a single-class dex file.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--class <class-name>  Target class name.",
                "--source-path <path>  Optional source path disambiguation.",
                "--source-entry <entry>  Optional container entry disambiguation.",
                "--output <file>  Output dex file path.",
            ),
            output = "Success always prints output=<absolute-path>.",
            notes = listOf(
                "The target class must resolve uniquely within the selected source scope.",
            ),
        ),
        CommandHelpSpec(
            command = "export-class-smali",
            usage = CliUsages.exportClassSmali,
            description = "Export a uniquely resolved class as smali text.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--class <class-name>  Target class name.",
                "--source-path <path>  Optional source path disambiguation.",
                "--source-entry <entry>  Optional container entry disambiguation.",
                "--output <file>  Output smali file path.",
                "--auto-unicode-decode true|false  Decode unicode escapes in string text. Defaults to true.",
            ),
            output = "Success always prints output=<absolute-path>.",
            notes = listOf(
                "The target class must resolve uniquely within the selected source scope.",
            ),
        ),
        CommandHelpSpec(
            command = "export-class-java",
            usage = CliUsages.exportClassJava,
            description = "Export a uniquely resolved class as decompiled Java text.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--class <class-name>  Target class name.",
                "--source-path <path>  Optional source path disambiguation.",
                "--source-entry <entry>  Optional container entry disambiguation.",
                "--output <file>  Output Java file path.",
            ),
            output = "Success always prints output=<absolute-path>.",
            notes = listOf(
                "The target class must resolve uniquely within the selected source scope.",
            ),
        ),
        CommandHelpSpec(
            command = "export-method-smali",
            usage = CliUsages.exportMethodSmali,
            description = "Export a uniquely resolved method as smali text.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--method <signature>  Full smali method signature.",
                "--source-path <path>  Optional source path disambiguation.",
                "--source-entry <entry>  Optional container entry disambiguation.",
                "--output <file>  Output smali file path.",
                "--auto-unicode-decode true|false  Decode unicode escapes in string text. Defaults to true.",
                "--mode snippet|class  Export only the method snippet or a minimal class wrapper.",
            ),
            output = "Success always prints output=<absolute-path>.",
            notes = listOf(
                "The target method must resolve uniquely within the selected source scope.",
            ),
        ),
        CommandHelpSpec(
            command = "manifest",
            usage = CliUsages.manifest,
            description = "Decode the active workspace AndroidManifest.xml.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf("--json  Render source metadata and decoded text as JSON."),
            output = "Text prints the decoded manifest text directly.",
            notes = listOf(
                "This command is only supported by resource-capable workspaces.",
            ),
        ),
        CommandHelpSpec(
            command = "res-table",
            usage = CliUsages.resTable,
            description = "Decode the active workspace resources.arsc summary.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf("--json  Render the structured resource-table summary as JSON."),
            output = "Text prints source identity and table counts.",
            notes = listOf(
                "This command is only supported by resource-capable workspaces.",
            ),
        ),
        CommandHelpSpec(
            command = "decode-xml",
            usage = CliUsages.decodeXml,
            description = "Decode a resource XML file or APK XML entry by path.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--path <xml-path>  Workspace file path or APK entry path to decode.",
                "--json  Render source metadata and decoded text as JSON.",
            ),
            output = "Text prints the decoded XML text directly.",
            notes = listOf(
                "The xml path must resolve to a unique source within the workspace.",
            ),
        ),
        CommandHelpSpec(
            command = "list-res",
            usage = CliUsages.listRes,
            description = "List resource entries visible from the active workspace.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf("--json  Render the resource entry array as JSON."),
            output = "Text prints a stable, tab-separated resource entry table.",
            notes = listOf(
                "This command lists resource identity and source mapping, not decoded values.",
            ),
        ),
        CommandHelpSpec(
            command = "resolve-res",
            usage = CliUsages.resolveRes,
            description = "Resolve a single resource entry by id or by type and name.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--id <res-id>  Resource identifier selector.",
                "--type <type>  Resource type selector.",
                "--name <name>  Resource name selector.",
                "--json  Render the structured resource value as JSON.",
            ),
            output = "Text prints resourceId, type, name, and value lines.",
            notes = listOf(
                "--id and --type/--name are mutually exclusive.",
                "Only direct values or direct reference forms are rendered in the first version.",
            ),
        ),
        CommandHelpSpec(
            command = "find-res",
            usage = CliUsages.findRes,
            description = "Search resource entries by direct value using a JSON query.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--query-json <json>  Inline JSON query text.",
                "--query-file <file>  UTF-8 JSON query file.",
                "--offset <n>  Zero-based result offset.",
                "--limit <n>  Positive page size.",
                "--json  Render hits as JSON.",
            ),
            output = "Text prints a stable, tab-separated hit table. JSON prints the hit array directly.",
            notes = listOf(
                "--query-json and --query-file are required and mutually exclusive.",
                "Only direct values or direct reference forms are searched in the first version.",
            ),
        ),
    ).associateBy(CommandHelpSpec::command)

    fun isHelpFlag(token: String): Boolean = token in helpFlags

    fun isKnownCommand(command: String): Boolean = commandSpecs.containsKey(command)

    fun render(command: String?): String =
        if (command == null) {
            renderGeneralHelp()
        } else {
            renderCommandHelp(commandSpecs[command] ?: error("unknown command: $command"))
        }

    private fun renderGeneralHelp(): String =
        buildString {
            appendLine("DexClub CLI")
            appendLine("Version: ${CliBuildInfo.version}")
            appendLine()
            appendLine("Usage:")
            appendLine("  cli <command> [args]")
            appendLine()
            appendLine("Lifecycle Commands:")
            appendLine("  init                     Initialize a workspace from an input path.")
            appendLine("  status                   Show workspace status and issues.")
            appendLine("  gc                       Delete safe-to-rebuild derived state.")
            appendLine("  inspect                  Show the active target summary.")
            appendLine()
            appendLine("Dex Analysis Commands:")
            appendLine("  find-class               Search classes with a DexKit query.")
            appendLine("  find-method              Search methods with a DexKit query.")
            appendLine("  find-field               Search fields with a DexKit query.")
            appendLine("  find-class-using-strings Search classes with grouped string matchers.")
            appendLine("  find-method-using-strings")
            appendLine("                           Search methods with grouped string matchers.")
            appendLine("  export-class-dex         Export a uniquely resolved class as dex.")
            appendLine("  export-class-smali       Export a uniquely resolved class as smali.")
            appendLine("  export-class-java        Export a uniquely resolved class as Java.")
            appendLine("  export-method-smali      Export a uniquely resolved method as smali.")
            appendLine()
            appendLine("Resource Commands:")
            appendLine("  manifest                 Decode AndroidManifest.xml.")
            appendLine("  res-table                Decode resources.arsc summary.")
            appendLine("  decode-xml               Decode a resource XML by path.")
            appendLine("  list-res                 List resource entries.")
            appendLine("  resolve-res              Resolve a resource by id or by type and name.")
            appendLine("  find-res                 Search resources by direct value.")
            appendLine()
            appendLine("Global Rules:")
            appendLine("  init is the required first step for creating .dexclub.")
            appendLine("  Non-init commands consume an initialized workspace only.")
            appendLine("  [workdir] is an optional positional argument.")
            appendLine("  If [workdir] is omitted, cli uses cwd directly and does not search parents.")
            appendLine()
            append("Run 'cli help <command>' for command-specific details.")
        }

    private fun renderCommandHelp(spec: CommandHelpSpec): String =
        buildString {
            appendLine("Command:")
            appendLine("  ${spec.command}")
            appendLine()
            appendLine("Usage:")
            appendLine("  ${spec.usage}")
            appendLine()
            appendLine("Description:")
            appendLine("  ${spec.description}")
            appendLine()
            if (spec.arguments.isNotEmpty()) {
                appendLine("Arguments:")
                spec.arguments.forEach { argument ->
                    appendLine("  $argument")
                }
                appendLine()
            }
            if (spec.options.isNotEmpty()) {
                appendLine("Options:")
                spec.options.forEach { option ->
                    appendLine("  $option")
                }
                appendLine()
            }
            appendLine("Output:")
            appendLine("  ${spec.output}")
            if (spec.notes.isNotEmpty()) {
                appendLine()
                appendLine("Notes:")
                spec.notes.forEach { note ->
                    appendLine("  $note")
                }
            }
        }.trimEnd()
}
