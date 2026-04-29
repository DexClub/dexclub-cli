package io.github.dexclub.cli

import io.github.dexclub.core.api.dex.MethodDetailSection
import io.github.dexclub.core.api.shared.PageWindow

internal class CliParser {
    fun parse(argv: List<String>): CliRequest {
        if (argv.isEmpty()) {
            return CliRequest.Help()
        }
        if (argv.size == 1 && argv.first() == "--version") {
            return CliRequest.Version()
        }
        if (argv.size == 1 && CliHelp.isHelpFlag(argv.first())) {
            return CliRequest.Help()
        }
        return when (val command = argv.first()) {
            "help" -> parseHelp(argv.drop(1))
            "init" -> parseInit(argv.drop(1))
            "status" -> parseWorkdirCommand(command, argv.drop(1))
            "gc" -> parseWorkdirCommand(command, argv.drop(1))
            "inspect" -> parseWorkdirCommand(command, argv.drop(1))
            "manifest" -> parseWorkdirCommand(command, argv.drop(1))
            "res-table" -> parseWorkdirCommand(command, argv.drop(1))
            "decode-xml" -> parseDecodeXml(argv.drop(1))
            "list-res" -> parseWorkdirCommand(command, argv.drop(1))
            "resolve-res" -> parseResolveRes(argv.drop(1))
            "find-res" -> parseFindRes(argv.drop(1))
            "find-class" -> parseFindClass(argv.drop(1))
            "find-method" -> parseFindMethod(argv.drop(1))
            "inspect-method" -> parseInspectMethod(argv.drop(1))
            "find-field" -> parseFindField(argv.drop(1))
            "find-class-using-strings" -> parseFindClassUsingStrings(argv.drop(1))
            "find-method-using-strings" -> parseFindMethodUsingStrings(argv.drop(1))
            "export-class-dex" -> parseExportClassDex(argv.drop(1))
            "export-class-java" -> parseExportClassJava(argv.drop(1))
            "export-class-smali" -> parseExportClassSmali(argv.drop(1))
            "export-method-smali" -> parseExportMethodSmali(argv.drop(1))
            "export-method-dex" -> parseExportMethodDex(argv.drop(1))
            "export-method-java" -> parseExportMethodJava(argv.drop(1))
            else -> throw CliUsageError(
                message = "unknown command: $command",
                usage = CliUsages.general,
                hint = "Run 'cli help' to see available commands.",
            )
        }
    }

    private fun parseHelp(tokens: List<String>): CliRequest.Help {
        if (tokens.isEmpty()) {
            return CliRequest.Help()
        }
        if (tokens.size != 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = CliUsages.help,
            )
        }
        val command = tokens.single()
        if (command.startsWith("--")) {
            throw CliUsageError(
                message = "unknown option: $command",
                usage = CliUsages.help,
            )
        }
        if (!CliHelp.isKnownCommand(command)) {
            throw CliUsageError(
                message = "unknown command: $command",
                usage = CliUsages.help,
                hint = "Run 'cli help' to see available commands.",
            )
        }
        return CliRequest.Help(command)
    }

    private fun parseInit(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("init")
        }
        val parsed = parsePositionalAndFlags(tokens, allowJson = false, usage = CliUsages.init)
        if (parsed.positionals.size != 1) {
            throw CliUsageError(
                message = "missing required argument: <input>",
                usage = CliUsages.init,
            )
        }
        return CliRequest.Init(
            input = parsed.positionals.single(),
            outputFormat = parsed.outputFormat,
        )
    }

    private fun parseWorkdirCommand(command: String, tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help(command)
        }
        val usage = CliUsages.forCommand(command)
        val parsed = parsePositionalAndFlags(tokens, allowJson = true, usage = usage)
        if (parsed.positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }
        return when (command) {
            "status" -> CliRequest.Status(parsed.positionals.singleOrNull(), parsed.outputFormat)
            "gc" -> CliRequest.Gc(parsed.positionals.singleOrNull(), parsed.outputFormat)
            "inspect" -> CliRequest.Inspect(parsed.positionals.singleOrNull(), parsed.outputFormat)
            "manifest" -> CliRequest.Manifest(parsed.positionals.singleOrNull(), parsed.outputFormat)
            "res-table" -> CliRequest.ResTable(parsed.positionals.singleOrNull(), parsed.outputFormat)
            "list-res" -> CliRequest.ListRes(parsed.positionals.singleOrNull(), parsed.outputFormat)
            else -> error("unsupported command: $command")
        }
    }

    private fun parseFindClass(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("find-class")
        }
        val parsed = parseQueryCommand(tokens, CliUsages.findClass)
        return CliRequest.FindClass(
            workdir = parsed.workdir,
            query = parsed.query,
            window = parsed.window,
            outputFormat = parsed.outputFormat,
        )
    }

    private fun parseDecodeXml(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("decode-xml")
        }
        val usage = CliUsages.decodeXml
        val positionals = mutableListOf<String>()
        var outputFormat = OutputFormat.Text
        var path: String? = null
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }
            when (token) {
                "--json" -> {
                    outputFormat = OutputFormat.Json
                    index += 1
                }

                "--path" -> {
                    path = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }

        return CliRequest.DecodeXml(
            workdir = positionals.singleOrNull(),
            path = path ?: throw CliUsageError(
                message = "missing required option: --path",
                usage = usage,
            ),
            outputFormat = outputFormat,
        )
    }

    private fun parseResolveRes(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("resolve-res")
        }
        val usage = CliUsages.resolveRes
        val positionals = mutableListOf<String>()
        var outputFormat = OutputFormat.Text
        var resourceId: String? = null
        var type: String? = null
        var name: String? = null
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }
            when (token) {
                "--json" -> {
                    outputFormat = OutputFormat.Json
                    index += 1
                }

                "--id" -> {
                    resourceId = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--type" -> {
                    type = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--name" -> {
                    name = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }
        if (resourceId != null && (type != null || name != null)) {
            throw CliUsageError(
                message = "--id and --type/--name are mutually exclusive",
                usage = usage,
            )
        }
        if ((type == null) != (name == null)) {
            throw CliUsageError(
                message = "--type and --name must be specified together",
                usage = usage,
            )
        }
        if (resourceId == null && type == null && name == null) {
            throw CliUsageError(
                message = "missing required selector: --id or --type with --name",
                usage = usage,
            )
        }

        return CliRequest.ResolveRes(
            workdir = positionals.singleOrNull(),
            resourceId = resourceId,
            type = type,
            name = name,
            outputFormat = outputFormat,
        )
    }

    private fun parseFindRes(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("find-res")
        }
        val parsed = parseQueryCommand(tokens, CliUsages.findRes)
        return CliRequest.FindRes(
            workdir = parsed.workdir,
            query = parsed.query,
            window = parsed.window,
            outputFormat = parsed.outputFormat,
        )
    }

    private fun parseFindMethod(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("find-method")
        }
        val parsed = parseQueryCommand(tokens, CliUsages.findMethod)
        return CliRequest.FindMethod(
            workdir = parsed.workdir,
            query = parsed.query,
            window = parsed.window,
            outputFormat = parsed.outputFormat,
        )
    }

    private fun parseInspectMethod(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("inspect-method")
        }
        val usage = CliUsages.inspectMethod
        val positionals = mutableListOf<String>()
        var outputFormat = OutputFormat.Text
        var descriptor: String? = null
        var includes: Set<MethodDetailSection>? = null
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }

            when (token) {
                "--json" -> {
                    outputFormat = OutputFormat.Json
                    index += 1
                }

                "--descriptor" -> {
                    descriptor = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--include" -> {
                    includes = parseMethodDetailIncludes(
                        requireOptionValue(tokens, token, index, usage),
                        usage,
                    )
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }

        return CliRequest.InspectMethod(
            workdir = positionals.singleOrNull(),
            descriptor = descriptor ?: throw CliUsageError(
                message = "missing required option: --descriptor",
                usage = usage,
            ),
            includes = includes ?: MethodDetailSection.entries.toSet(),
            outputFormat = outputFormat,
        )
    }

    private fun parseFindField(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("find-field")
        }
        val parsed = parseQueryCommand(tokens, CliUsages.findField)
        return CliRequest.FindField(
            workdir = parsed.workdir,
            query = parsed.query,
            window = parsed.window,
            outputFormat = parsed.outputFormat,
        )
    }

    private fun parseFindClassUsingStrings(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("find-class-using-strings")
        }
        val parsed = parseQueryCommand(tokens, CliUsages.findClassUsingStrings)
        return CliRequest.FindClassUsingStrings(
            workdir = parsed.workdir,
            query = parsed.query,
            window = parsed.window,
            outputFormat = parsed.outputFormat,
        )
    }

    private fun parseFindMethodUsingStrings(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("find-method-using-strings")
        }
        val parsed = parseQueryCommand(tokens, CliUsages.findMethodUsingStrings)
        return CliRequest.FindMethodUsingStrings(
            workdir = parsed.workdir,
            query = parsed.query,
            window = parsed.window,
            outputFormat = parsed.outputFormat,
        )
    }

    private fun parseExportClassSmali(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("export-class-smali")
        }
        val parsed = parseExportClassSmaliCommand(tokens, CliUsages.exportClassSmali)
        return CliRequest.ExportClassSmali(
            workdir = parsed.workdir,
            className = parsed.className,
            sourcePath = parsed.sourcePath,
            sourceEntry = parsed.sourceEntry,
            output = parsed.output,
            autoUnicodeDecode = parsed.autoUnicodeDecode,
        )
    }

    private fun parseExportClassDex(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("export-class-dex")
        }
        val parsed = parseExportClassDexCommand(tokens, CliUsages.exportClassDex)
        return CliRequest.ExportClassDex(
            workdir = parsed.workdir,
            className = parsed.className,
            sourcePath = parsed.sourcePath,
            sourceEntry = parsed.sourceEntry,
            output = parsed.output,
        )
    }

    private fun parseExportClassJava(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("export-class-java")
        }
        val parsed = parseExportClassJavaCommand(tokens, CliUsages.exportClassJava)
        return CliRequest.ExportClassJava(
            workdir = parsed.workdir,
            className = parsed.className,
            sourcePath = parsed.sourcePath,
            sourceEntry = parsed.sourceEntry,
            output = parsed.output,
        )
    }

    private fun parseExportMethodSmali(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("export-method-smali")
        }
        val parsed = parseExportMethodSmaliCommand(tokens, CliUsages.exportMethodSmali)
        return CliRequest.ExportMethodSmali(
            workdir = parsed.workdir,
            methodSignature = parsed.methodSignature,
            sourcePath = parsed.sourcePath,
            sourceEntry = parsed.sourceEntry,
            output = parsed.output,
            autoUnicodeDecode = parsed.autoUnicodeDecode,
            mode = parsed.mode,
        )
    }

    private fun parseExportMethodDex(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("export-method-dex")
        }
        val parsed = parseExportMethodDexCommand(tokens, CliUsages.exportMethodDex)
        return CliRequest.ExportMethodDex(
            workdir = parsed.workdir,
            methodSignature = parsed.methodSignature,
            sourcePath = parsed.sourcePath,
            sourceEntry = parsed.sourceEntry,
            output = parsed.output,
        )
    }

    private fun parseExportMethodJava(tokens: List<String>): CliRequest {
        if (tokens.size == 1 && CliHelp.isHelpFlag(tokens.single())) {
            return CliRequest.Help("export-method-java")
        }
        val parsed = parseExportMethodJavaCommand(tokens, CliUsages.exportMethodJava)
        return CliRequest.ExportMethodJava(
            workdir = parsed.workdir,
            methodSignature = parsed.methodSignature,
            sourcePath = parsed.sourcePath,
            sourceEntry = parsed.sourceEntry,
            output = parsed.output,
        )
    }

    private fun parseQueryCommand(tokens: List<String>, usage: String): ParsedQueryCommand {
        val positionals = mutableListOf<String>()
        var outputFormat = OutputFormat.Text
        var queryJson: String? = null
        var queryFile: String? = null
        var offset = 0
        var limit: Int? = null
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }
            when (token) {
                "--json" -> {
                    outputFormat = OutputFormat.Json
                    index += 1
                }

                "--query-json" -> {
                    queryJson = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--query-file" -> {
                    queryFile = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--offset" -> {
                    val value = requireOptionValue(tokens, token, index, usage)
                    offset = value.toIntOrNull()
                        ?.takeIf { it >= 0 }
                        ?: throw CliUsageError(
                            message = "invalid value for --offset: expected a non-negative integer",
                            usage = usage,
                        )
                    index += 2
                }

                "--limit" -> {
                    val value = requireOptionValue(tokens, token, index, usage)
                    limit = value.toIntOrNull()
                        ?.takeIf { it > 0 }
                        ?: throw CliUsageError(
                            message = "invalid value for --limit: expected a positive integer",
                            usage = usage,
                        )
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }
        if (queryJson != null && queryFile != null) {
            throw CliUsageError(
                message = "--query-json and --query-file are mutually exclusive",
                usage = usage,
            )
        }
        val query = when {
            queryJson != null -> QueryInput.Json(queryJson)
            queryFile != null -> QueryInput.File(queryFile)
            else -> throw CliUsageError(
                message = "missing required option: --query-json or --query-file",
                usage = usage,
            )
        }
        return ParsedQueryCommand(
            workdir = positionals.singleOrNull(),
            query = query,
            window = PageWindow(offset = offset, limit = limit),
            outputFormat = outputFormat,
        )
    }

    private fun parsePositionalAndFlags(
        tokens: List<String>,
        allowJson: Boolean,
        usage: String,
    ): ParsedCommand {
        val positionals = mutableListOf<String>()
        var outputFormat = OutputFormat.Text
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.startsWith("--")) {
                seenOption = true
                if (!seenFlags.add(token)) {
                    throw CliUsageError(
                        message = "option may only be specified once: $token",
                        usage = usage,
                    )
                }
                when (token) {
                    "--json" -> {
                        if (!allowJson) {
                            throw CliUsageError(
                                message = "unknown option: $token",
                                usage = usage,
                            )
                        }
                        outputFormat = OutputFormat.Json
                    }

                    else -> throw CliUsageError(
                        message = "unknown option: $token",
                        usage = usage,
                    )
                }
                index += 1
                continue
            }

            if (seenOption) {
                throw CliUsageError(
                    message = "positional arguments must appear before options",
                    usage = usage,
                )
            }
            positionals += token
            index += 1
        }
        return ParsedCommand(
            positionals = positionals,
            outputFormat = outputFormat,
        )
    }

    private fun parseExportClassSmaliCommand(
        tokens: List<String>,
        usage: String,
    ): ParsedExportClassSmaliCommand {
        val positionals = mutableListOf<String>()
        var className: String? = null
        var sourcePath: String? = null
        var sourceEntry: String? = null
        var output: String? = null
        var autoUnicodeDecode = true
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }

            when (token) {
                "--class" -> {
                    className = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-path" -> {
                    sourcePath = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-entry" -> {
                    sourceEntry = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--output" -> {
                    output = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--auto-unicode-decode" -> {
                    autoUnicodeDecode = parseBooleanOption(tokens, token, index, usage)
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }
        if (sourceEntry != null && sourcePath == null) {
            throw CliUsageError(
                message = "--source-entry requires --source-path",
                usage = usage,
            )
        }

        return ParsedExportClassSmaliCommand(
            workdir = positionals.singleOrNull(),
            className = className ?: throw CliUsageError(
                message = "missing required option: --class",
                usage = usage,
            ),
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
            output = output ?: throw CliUsageError(
                message = "missing required option: --output",
                usage = usage,
            ),
            autoUnicodeDecode = autoUnicodeDecode,
        )
    }

    private fun parseExportClassDexCommand(
        tokens: List<String>,
        usage: String,
    ): ParsedExportClassDexCommand {
        val positionals = mutableListOf<String>()
        var className: String? = null
        var sourcePath: String? = null
        var sourceEntry: String? = null
        var output: String? = null
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }

            when (token) {
                "--class" -> {
                    className = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-path" -> {
                    sourcePath = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-entry" -> {
                    sourceEntry = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--output" -> {
                    output = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }
        if (sourceEntry != null && sourcePath == null) {
            throw CliUsageError(
                message = "--source-entry requires --source-path",
                usage = usage,
            )
        }

        return ParsedExportClassDexCommand(
            workdir = positionals.singleOrNull(),
            className = className ?: throw CliUsageError(
                message = "missing required option: --class",
                usage = usage,
            ),
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
            output = output ?: throw CliUsageError(
                message = "missing required option: --output",
                usage = usage,
            ),
        )
    }

    private fun parseExportMethodSmaliCommand(
        tokens: List<String>,
        usage: String,
    ): ParsedExportMethodSmaliCommand {
        val positionals = mutableListOf<String>()
        var methodSignature: String? = null
        var sourcePath: String? = null
        var sourceEntry: String? = null
        var output: String? = null
        var autoUnicodeDecode = true
        var mode = "snippet"
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }

            when (token) {
                "--method" -> {
                    methodSignature = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-path" -> {
                    sourcePath = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-entry" -> {
                    sourceEntry = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--output" -> {
                    output = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--auto-unicode-decode" -> {
                    autoUnicodeDecode = parseBooleanOption(tokens, token, index, usage)
                    index += 2
                }

                "--mode" -> {
                    mode = requireOptionValue(tokens, token, index, usage).also { value ->
                        if (value != "snippet" && value != "class") {
                            throw CliUsageError(
                                message = "invalid value for --mode: expected snippet or class",
                                usage = usage,
                            )
                        }
                    }
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }
        if (sourceEntry != null && sourcePath == null) {
            throw CliUsageError(
                message = "--source-entry requires --source-path",
                usage = usage,
            )
        }

        return ParsedExportMethodSmaliCommand(
            workdir = positionals.singleOrNull(),
            methodSignature = methodSignature ?: throw CliUsageError(
                message = "missing required option: --method",
                usage = usage,
            ),
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
            output = output ?: throw CliUsageError(
                message = "missing required option: --output",
                usage = usage,
            ),
            autoUnicodeDecode = autoUnicodeDecode,
            mode = mode,
        )
    }

    private fun parseExportMethodDexCommand(
        tokens: List<String>,
        usage: String,
    ): ParsedExportMethodDexCommand {
        val positionals = mutableListOf<String>()
        var methodSignature: String? = null
        var sourcePath: String? = null
        var sourceEntry: String? = null
        var output: String? = null
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }

            when (token) {
                "--method" -> {
                    methodSignature = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-path" -> {
                    sourcePath = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-entry" -> {
                    sourceEntry = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--output" -> {
                    output = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }
        if (sourceEntry != null && sourcePath == null) {
            throw CliUsageError(
                message = "--source-entry requires --source-path",
                usage = usage,
            )
        }

        return ParsedExportMethodDexCommand(
            workdir = positionals.singleOrNull(),
            methodSignature = methodSignature ?: throw CliUsageError(
                message = "missing required option: --method",
                usage = usage,
            ),
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
            output = output ?: throw CliUsageError(
                message = "missing required option: --output",
                usage = usage,
            ),
        )
    }

    private fun parseExportMethodJavaCommand(
        tokens: List<String>,
        usage: String,
    ): ParsedExportMethodJavaCommand {
        val positionals = mutableListOf<String>()
        var methodSignature: String? = null
        var sourcePath: String? = null
        var sourceEntry: String? = null
        var output: String? = null
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }

            when (token) {
                "--method" -> {
                    methodSignature = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-path" -> {
                    sourcePath = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-entry" -> {
                    sourceEntry = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--output" -> {
                    output = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }
        if (sourceEntry != null && sourcePath == null) {
            throw CliUsageError(
                message = "--source-entry requires --source-path",
                usage = usage,
            )
        }

        return ParsedExportMethodJavaCommand(
            workdir = positionals.singleOrNull(),
            methodSignature = methodSignature ?: throw CliUsageError(
                message = "missing required option: --method",
                usage = usage,
            ),
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
            output = output ?: throw CliUsageError(
                message = "missing required option: --output",
                usage = usage,
            ),
        )
    }

    private fun parseExportClassJavaCommand(
        tokens: List<String>,
        usage: String,
    ): ParsedExportClassJavaCommand {
        val positionals = mutableListOf<String>()
        var className: String? = null
        var sourcePath: String? = null
        var sourceEntry: String? = null
        var output: String? = null
        var seenOption = false
        val seenFlags = mutableSetOf<String>()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                if (seenOption) {
                    throw CliUsageError(
                        message = "positional arguments must appear before options",
                        usage = usage,
                    )
                }
                positionals += token
                index += 1
                continue
            }

            seenOption = true
            if (!seenFlags.add(token)) {
                throw CliUsageError(
                    message = "option may only be specified once: $token",
                    usage = usage,
                )
            }

            when (token) {
                "--class" -> {
                    className = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-path" -> {
                    sourcePath = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--source-entry" -> {
                    sourceEntry = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                "--output" -> {
                    output = requireOptionValue(tokens, token, index, usage)
                    index += 2
                }

                else -> throw CliUsageError(
                    message = "unknown option: $token",
                    usage = usage,
                )
            }
        }

        if (positionals.size > 1) {
            throw CliUsageError(
                message = "too many positional arguments",
                usage = usage,
            )
        }
        if (sourceEntry != null && sourcePath == null) {
            throw CliUsageError(
                message = "--source-entry requires --source-path",
                usage = usage,
            )
        }

        return ParsedExportClassJavaCommand(
            workdir = positionals.singleOrNull(),
            className = className ?: throw CliUsageError(
                message = "missing required option: --class",
                usage = usage,
            ),
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
            output = output ?: throw CliUsageError(
                message = "missing required option: --output",
                usage = usage,
            ),
        )
    }

    private fun requireOptionValue(
        tokens: List<String>,
        option: String,
        index: Int,
        usage: String,
    ): String {
        if (index + 1 >= tokens.size) {
            throw CliUsageError(
                message = "missing value for option: $option",
                usage = usage,
            )
        }
        val value = tokens[index + 1].trim()
        if (value.isEmpty() || value.startsWith("--")) {
            throw CliUsageError(
                message = "missing value for option: $option",
                usage = usage,
            )
        }
        return value
    }

    private fun parseBooleanOption(
        tokens: List<String>,
        option: String,
        index: Int,
        usage: String,
    ): Boolean {
        val value = requireOptionValue(tokens, option, index, usage)
        return when (value) {
            "true" -> true
            "false" -> false
            else -> throw CliUsageError(
                message = "invalid value for $option: expected true or false",
                usage = usage,
            )
        }
    }

    private fun parseMethodDetailIncludes(value: String, usage: String): Set<MethodDetailSection> {
        if (value.isBlank()) {
            throw CliUsageError(
                message = "invalid value for --include: expected a comma-separated list",
                usage = usage,
            )
        }
        val sections = value.split(',')
            .map { token ->
                when (val normalized = token.trim()) {
                    "using-fields" -> MethodDetailSection.UsingFields
                    "callers" -> MethodDetailSection.Callers
                    "invokes" -> MethodDetailSection.Invokes
                    "" -> throw CliUsageError(
                        message = "invalid value for --include: empty section is not allowed",
                        usage = usage,
                    )
                    else -> throw CliUsageError(
                        message = "invalid value for --include: unsupported section '$normalized'",
                        usage = usage,
                    )
                }
            }
        if (sections.distinct().size != sections.size) {
            throw CliUsageError(
                message = "invalid value for --include: duplicate sections are not allowed",
                usage = usage,
            )
        }
        return sections.toSet()
    }

    private data class ParsedCommand(
        val positionals: List<String>,
        val outputFormat: OutputFormat,
    )

    private data class ParsedQueryCommand(
        val workdir: String?,
        val query: QueryInput,
        val window: PageWindow,
        val outputFormat: OutputFormat,
    )

    private data class ParsedExportClassSmaliCommand(
        val workdir: String?,
        val className: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
        val autoUnicodeDecode: Boolean,
    )

    private data class ParsedExportClassDexCommand(
        val workdir: String?,
        val className: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
    )

    private data class ParsedExportMethodSmaliCommand(
        val workdir: String?,
        val methodSignature: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
        val autoUnicodeDecode: Boolean,
        val mode: String,
    )

    private data class ParsedExportMethodDexCommand(
        val workdir: String?,
        val methodSignature: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
    )

    private data class ParsedExportMethodJavaCommand(
        val workdir: String?,
        val methodSignature: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
    )

    private data class ParsedExportClassJavaCommand(
        val workdir: String?,
        val className: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
    )
}

internal object CliUsages {
    const val general: String = "cli <command> ..."
    const val help: String = "cli help [command]"
    const val init: String = "cli init <input>"
    const val status: String = "cli status [workdir] [--json]"
    const val gc: String = "cli gc [workdir] [--json]"
    const val inspect: String = "cli inspect [workdir] [--json]"
    const val manifest: String = "cli manifest [workdir] [--json]"
    const val resTable: String = "cli res-table [workdir] [--json]"
    const val decodeXml: String = "cli decode-xml [workdir] --path <xml-path> [--json]"
    const val listRes: String = "cli list-res [workdir] [--json]"
    const val resolveRes: String =
        "cli resolve-res [workdir] (--id <res-id> | --type <type> --name <name>) [--json]"
    const val findRes: String =
        "cli find-res [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]"
    const val findClass: String =
        "cli find-class [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]"
    const val findMethod: String =
        "cli find-method [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]"
    const val inspectMethod: String =
        "cli inspect-method [workdir] --descriptor <method-descriptor> [--include <sections>] [--json]"
    const val findField: String =
        "cli find-field [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]"
    const val findClassUsingStrings: String =
        "cli find-class-using-strings [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]"
    const val findMethodUsingStrings: String =
        "cli find-method-using-strings [workdir] (--query-json <json> | --query-file <file>) [--offset <n>] [--limit <n>] [--json]"
    const val exportClassDex: String =
        "cli export-class-dex [workdir] --class <class-name> [--source-path <path>] [--source-entry <entry>] --output <file>"
    const val exportClassJava: String =
        "cli export-class-java [workdir] --class <class-name> [--source-path <path>] [--source-entry <entry>] --output <file>"
    const val exportClassSmali: String =
        "cli export-class-smali [workdir] --class <class-name> [--source-path <path>] [--source-entry <entry>] --output <file> [--auto-unicode-decode true|false]"
    const val exportMethodSmali: String =
        "cli export-method-smali [workdir] --method <signature> [--source-path <path>] [--source-entry <entry>] --output <file> [--auto-unicode-decode true|false] [--mode snippet|class]"
    const val exportMethodDex: String =
        "cli export-method-dex [workdir] --method <signature> [--source-path <path>] [--source-entry <entry>] --output <file>"
    const val exportMethodJava: String =
        "cli export-method-java [workdir] --method <signature> [--source-path <path>] [--source-entry <entry>] --output <file>"

    fun forCommand(command: String): String =
        when (command) {
            "init" -> init
            "status" -> status
            "gc" -> gc
            "inspect" -> inspect
            "manifest" -> manifest
            "res-table" -> resTable
            "decode-xml" -> decodeXml
            "list-res" -> listRes
            "resolve-res" -> resolveRes
            "find-res" -> findRes
            "find-class" -> findClass
            "find-method" -> findMethod
            "inspect-method" -> inspectMethod
            "find-field" -> findField
            "find-class-using-strings" -> findClassUsingStrings
            "find-method-using-strings" -> findMethodUsingStrings
            "export-class-dex" -> exportClassDex
            "export-class-java" -> exportClassJava
            "export-class-smali" -> exportClassSmali
            "export-method-smali" -> exportMethodSmali
            "export-method-dex" -> exportMethodDex
            "export-method-java" -> exportMethodJava
            else -> general
        }
}
