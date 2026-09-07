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

    private val commandSpecs: Map<String, CommandHelpSpec> = buildMap {
        CliWorkspaceCommandCatalog.commands.forEach { metadata ->
            put(metadata.command, metadata.toHelpSpec())
        }
        putAll(listOf(
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
            command = "inspect-method",
            usage = CliUsages.inspectMethod,
            description = "Inspect one uniquely resolved method and return its relation details.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--descriptor <method-descriptor>  Full method descriptor such as Lfoo/Bar;->baz(I)V.",
                "--include <sections>  Comma-separated sections from using-fields,callers,invokes,strings,annotations.",
                "--json  Render the structured method detail as JSON.",
            ),
            output = "Text prints the inspected method first, then renders any requested relation sections. JSON prints a single detail object.",
            notes = listOf(
                "inspect-method is a detail command, not a search command.",
                "The descriptor must already identify one unique method within the current workspace.",
                "If --include is omitted, cli returns using-fields, callers, invokes, strings, and annotations.",
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
            command = "export-method-dex",
            usage = CliUsages.exportMethodDex,
            description = "Export a uniquely resolved method as a method-only dex file.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--method <signature>  Full smali method signature.",
                "--source-path <path>  Optional source path disambiguation.",
                "--source-entry <entry>  Optional container entry disambiguation.",
                "--output <file>  Output dex file path.",
            ),
            output = "Success always prints output=<absolute-path>.",
            notes = listOf(
                "The target method must resolve uniquely within the selected source scope.",
            ),
        ),
        CommandHelpSpec(
            command = "export-method-java",
            usage = CliUsages.exportMethodJava,
            description = "Export a uniquely resolved method as decompiled Java text.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--method <signature>  Full smali method signature.",
                "--source-path <path>  Optional source path disambiguation.",
                "--source-entry <entry>  Optional container entry disambiguation.",
                "--output <file>  Output Java file path.",
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
            command = "get-res-value",
            usage = CliUsages.getResValue,
            description = "Resolve a single resource entry by id or by type and name.",
            arguments = listOf("[workdir]  Optional workspace directory. Defaults to the current directory."),
            options = listOf(
                "--id <res-id>  Resource identifier selector.",
                "--type <type>  Resource type selector.",
                "--name <name>  Resource name selector.",
                "--json  Render the structured resource value as JSON.",
            ),
            output = "Text prints resource identity followed by JSON-encoded configuration variants. JSON preserves the structured variants.",
            notes = listOf(
                "--id and --type/--name are mutually exclusive.",
                "The default configuration variant is returned, falling back to the first available variant.",
                "Each variant contains either a typed value or a generic bag such as a style, array, attribute, or plurals bag.",
            ),
        ),
        CommandHelpSpec(
            command = "find-res-values",
            usage = CliUsages.findResValues,
            description = "Search resource configuration variants and bag items using a JSON query.",
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
                "The query requires resourceType and value; optional fields are contains, ignoreCase, packageName, qualifier, valueKind, and matchTarget.",
                "matchTarget accepts decoded_value, raw_data, reference, bag_key, or any.",
            ),
        ),
        ).associateBy(CommandHelpSpec::command))
    }

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
            appendLine("DexClub")
            appendLine("Version: ${CliBuildInfo.VERSION}")
            appendLine()
            appendLine("Usage:")
            appendLine("  cli <command> [args]")
            appendLine()
            appendLine("Lifecycle Commands:")
            CliWorkspaceCommandCatalog.commands.forEach { metadata ->
                appendLine(metadata.generalHelpLine)
            }
            appendLine()
            appendLine("Dex Analysis Commands:")
            appendLine("  find-class               Search classes with a DexKit query.")
            appendLine("  find-method              Search methods with a DexKit query.")
            appendLine("  find-field               Search fields with a DexKit query.")
            appendLine("  inspect-method           Inspect relation details for one method.")
            appendLine("  export-class-dex         Export a uniquely resolved class as dex.")
            appendLine("  export-class-smali       Export a uniquely resolved class as smali.")
            appendLine("  export-class-java        Export a uniquely resolved class as Java.")
            appendLine("  export-method-smali      Export a uniquely resolved method as smali.")
            appendLine("  export-method-dex        Export a uniquely resolved method as dex.")
            appendLine("  export-method-java       Export a uniquely resolved method as Java.")
            appendLine()
            appendLine("Resource Commands:")
            appendLine("  manifest                 Decode AndroidManifest.xml.")
            appendLine("  res-table                Decode resources.arsc summary.")
            appendLine("  decode-xml               Decode a resource XML by path.")
            appendLine("  list-res                 List resource entries.")
            appendLine("  get-res-value             Resolve a resource by id or by type and name.")
            appendLine("  find-res-values           Search resources by direct value.")
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

    private fun CliWorkspaceCommandMetadata.toHelpSpec(): CommandHelpSpec =
        CommandHelpSpec(
            command = command,
            usage = usage,
            description = description,
            arguments = arguments,
            options = options,
            output = output,
            notes = notes,
        )
}
