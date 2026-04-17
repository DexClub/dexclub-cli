package io.github.dexclub.cli

import io.github.dexclub.core.DexEngine
import io.github.dexclub.core.config.SmaliRenderConfig
import io.github.dexclub.core.model.DexArchiveInfo
import io.github.dexclub.core.model.DexClassHit
import io.github.dexclub.core.model.DexFieldHit
import io.github.dexclub.core.model.DexInputKind
import io.github.dexclub.core.model.DexMethodHit
import io.github.dexclub.core.parseCoreJson
import io.github.dexclub.core.request.DexClassQueryRequest
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.DexFieldQueryRequest
import io.github.dexclub.core.request.DexMethodQueryRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.SmaliExportRequest
import io.github.dexclub.core.toCoreJsonString
import io.github.dexclub.core.workspace.WorkspaceCapabilities
import io.github.dexclub.core.workspace.WorkspaceInputKind
import io.github.dexclub.core.workspace.WorkspaceManager
import io.github.dexclub.core.workspace.WorkspaceResourceListing
import io.github.dexclub.core.workspace.WorkspaceStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

private data class CommandSpec(
    val name: String,
    val summary: String,
    val argumentsUsage: String,
    val details: List<String> = emptyList(),
    val handler: (Map<String, List<String>>) -> Unit,
)

private data class WorkspaceCommandSpec(
    val name: String,
    val summary: String,
    val argumentsUsage: String,
    val details: List<String> = emptyList(),
    val handler: (Map<String, List<String>>) -> Unit,
)

private enum class OutputFormat {
    Text,
    Json,
}

private class CliUsageException(
    messageText: String,
    val usageText: String,
) : IllegalArgumentException(messageText)

private data class CliStreams(
    val output: PrintStream,
    val error: PrintStream,
)

private data class CliInspectOutput(
    val inputType: String,
    val archiveInfo: DexArchiveInfo,
)

private val cliStreams = ThreadLocal<CliStreams>()

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}

internal fun runCli(
    args: Array<String>,
    output: PrintStream = System.out,
    error: PrintStream = System.err,
): Int {
    cliStreams.set(CliStreams(output = output, error = error))
    return try {
        try {
            when {
                args.isEmpty() -> {
                    printUsage(output)
                    0
                }

                args.first() in HELP_COMMANDS -> {
                    handleHelpCommand(args.drop(1), output)
                    0
                }

                args.first() in VERSION_COMMANDS -> {
                    printVersion(output)
                    0
                }

                args.first() == WORKSPACE_ROOT_COMMAND -> {
                    runWorkspaceEntry(args.drop(1))
                    0
                }

                else -> {
                    runCommand(args.first(), args.drop(1))
                    0
                }
            }
        } catch (failure: CliUsageException) {
            error.println("执行失败: ${failure.message ?: failure::class.simpleName.orEmpty()}")
            error.println()
            error.println(failure.usageText)
            1
        }
    } finally {
        cliStreams.remove()
    }
}

private fun runCommand(
    commandName: String,
    rawArgs: List<String>,
) {
    val command = findCommand(commandName) ?: failWithRootUsage("不支持的命令: $commandName")
    if (shouldPrintCommandHelp(rawArgs)) {
        printCommandUsage(command)
        return
    }

    runCatching {
        command.handler(parseOptions(rawArgs))
    }.onFailure { throwable ->
        failWithCommandUsage(
            command = command,
            message = throwable.message ?: throwable::class.simpleName.orEmpty(),
        )
    }
}

private fun runWorkspaceEntry(rawArgs: List<String>) {
    if (rawArgs.isEmpty()) {
        printWorkspaceUsage()
        return
    }
    when (val token = rawArgs.first()) {
        in HELP_COMMANDS -> {
            printWorkspaceUsage()
            return
        }

        "help" -> {
            val maybeSubcommand = rawArgs.drop(1).firstOrNull()
            if (maybeSubcommand == null) {
                printWorkspaceUsage()
                return
            }
            val command = findWorkspaceCommand(maybeSubcommand)
                ?: failWithRootUsage("不支持的 workspace 子命令: $maybeSubcommand")
            printWorkspaceCommandUsage(command)
            return
        }

        else -> runWorkspaceCommand(token, rawArgs.drop(1))
    }
}

private fun runWorkspaceCommand(
    subcommandName: String,
    rawArgs: List<String>,
) {
    val command = findWorkspaceCommand(subcommandName)
        ?: failWithRootUsage("不支持的 workspace 子命令: $subcommandName")
    if (shouldPrintCommandHelp(rawArgs)) {
        printWorkspaceCommandUsage(command)
        return
    }
    runCatching {
        command.handler(parseOptions(rawArgs))
    }.onFailure { throwable ->
        failWithWorkspaceCommandUsage(
            command = command,
            message = throwable.message ?: throwable::class.simpleName.orEmpty(),
        )
    }
}

private fun runInspect(options: Map<String, List<String>>) {
    val inputs = requireInspectOrSearchInputs(options)
    val archiveInfo = withSuppressedThirdPartyOutput {
        DexEngine(inputs).useEngine { dexEngine -> dexEngine.inspect() }
    }
    val inputKind = inferInspectOutputKind(inputs, archiveInfo)
    val outputFormat = requireOutputFormat(options, default = OutputFormat.Text)
    val outputText = when (outputFormat) {
        OutputFormat.Text -> formatInspectOutput(
            inputKind = inputKind,
            inputs = inputs,
            archiveInfo = archiveInfo,
        )

        OutputFormat.Json -> buildInspectJsonOutput(
            inputKind = inputKind,
            archiveInfo = archiveInfo,
        ).toString()
    }
    writeOutput(outputText, findOptionalOption(options, "output-file"))
}

private fun runExportDex(options: Map<String, List<String>>) = runBlocking {
    runSingleDexExport(options) { dexEngine, input, className, output ->
        dexEngine.exportDex(
            DexExportRequest(
                className = className,
                sourceDexPath = input,
                outputPath = output,
            ),
        ).outputPath
    }
}

private fun runExportSmali(options: Map<String, List<String>>) = runBlocking {
    val autoUnicodeDecode = findOptionalOption(options, "auto-unicode-decode")?.toBooleanStrictOrNull() ?: true
    runSingleDexExport(options) { dexEngine, input, className, output ->
        dexEngine.exportSmali(
            SmaliExportRequest(
                className = className,
                sourceDexPath = input,
                outputPath = output,
                config = SmaliRenderConfig(autoUnicodeDecode = autoUnicodeDecode),
            ),
        ).outputPath
    }
}

private fun runExportJava(options: Map<String, List<String>>) = runBlocking {
    runSingleDexExport(options) { dexEngine, input, className, output ->
        dexEngine.exportJava(
            JavaExportRequest(
                className = className,
                sourceDexPath = input,
                outputPath = output,
            ),
        ).outputPath
    }
}

private fun runFindClass(options: Map<String, List<String>>) {
    runAdvancedSearch(
        options = options,
        inputProvider = ::requireInspectOrSearchInputs,
    ) { dexEngine, _ ->
        dexEngine.findClassHits(requireQueryText(options).parseCoreJson<DexClassQueryRequest>())
    }
}

private fun runFindMethod(options: Map<String, List<String>>) {
    runAdvancedSearch(
        options = options,
        inputProvider = ::requireInspectOrSearchInputs,
    ) { dexEngine, _ ->
        dexEngine.findMethodHits(requireQueryText(options).parseCoreJson<DexMethodQueryRequest>())
    }
}

private fun runFindField(options: Map<String, List<String>>) {
    runAdvancedSearch(
        options = options,
        inputProvider = ::requireInspectOrSearchInputs,
    ) { dexEngine, _ ->
        dexEngine.findFieldHits(requireQueryText(options).parseCoreJson<DexFieldQueryRequest>())
    }
}

private fun runWorkspaceInit(options: Map<String, List<String>>) {
    val workspaceManager = requireWorkspaceManager(options)
    workspaceManager.init(
        rawInputs = requireWorkspaceInitInputs(options),
        requestedType = parseWorkspaceInputKind(findOptionalOption(options, "type")),
    )
    val status = workspaceManager.status()
    val outputFormat = requireOutputFormat(options, default = OutputFormat.Text)
    val outputText = when (outputFormat) {
        OutputFormat.Text -> formatWorkspaceStatus(status)
        OutputFormat.Json -> status.toCoreJsonString()
    }
    writeOutput(outputText, findOptionalOption(options, "output-file"))
}

private fun runWorkspaceStatus(options: Map<String, List<String>>) {
    val status = requireWorkspaceManager(options).status()
    val outputText = when (requireOutputFormat(options, default = OutputFormat.Text)) {
        OutputFormat.Text -> formatWorkspaceStatus(status)
        OutputFormat.Json -> status.toCoreJsonString()
    }
    writeOutput(outputText, findOptionalOption(options, "output-file"))
}

private fun runWorkspaceInspect(options: Map<String, List<String>>) {
    val manager = requireWorkspaceManager(options)
    val status = manager.status()
    val archiveInfo = withSuppressedThirdPartyOutput { manager.inspect() }
    val outputText = when (requireOutputFormat(options, default = OutputFormat.Text)) {
        OutputFormat.Text -> formatWorkspaceInspect(
            inputKind = status.metadata.input.type,
            archiveInfo = archiveInfo,
        )

        OutputFormat.Json -> buildInspectJsonOutput(
            inputKind = status.metadata.input.type,
            archiveInfo = archiveInfo,
        ).toString()
    }
    writeOutput(outputText, findOptionalOption(options, "output-file"))
}

private fun runWorkspaceCapabilities(options: Map<String, List<String>>) {
    val capabilities = requireWorkspaceManager(options).capabilities()
    val outputText = when (requireOutputFormat(options, default = OutputFormat.Text)) {
        OutputFormat.Text -> formatWorkspaceCapabilities(capabilities)
        OutputFormat.Json -> capabilities.toCoreJsonString()
    }
    writeOutput(outputText, findOptionalOption(options, "output-file"))
}

private fun runWorkspaceFindClass(options: Map<String, List<String>>) {
    runWorkspaceAdvancedSearch(options) {
        requireWorkspaceManager(options).findClassHits(requireQueryText(options).parseCoreJson<DexClassQueryRequest>())
    }
}

private fun runWorkspaceFindMethod(options: Map<String, List<String>>) {
    runWorkspaceAdvancedSearch(options) {
        requireWorkspaceManager(options).findMethodHits(requireQueryText(options).parseCoreJson<DexMethodQueryRequest>())
    }
}

private fun runWorkspaceFindField(options: Map<String, List<String>>) {
    runWorkspaceAdvancedSearch(options) {
        requireWorkspaceManager(options).findFieldHits(requireQueryText(options).parseCoreJson<DexFieldQueryRequest>())
    }
}

private fun runWorkspaceExportDex(options: Map<String, List<String>>) = runBlocking {
    val manager = requireWorkspaceManager(options)
    val className = requireOption(options, "class")
    val output = requireOption(options, "output")
    ensureParentDirectory(output)
    val result = withSuppressedThirdPartyOutput {
        manager.exportDex(className = className, outputPath = output)
    }
    cliOutput().println("output=${result.outputPath}")
}

private fun runWorkspaceExportSmali(options: Map<String, List<String>>) = runBlocking {
    val manager = requireWorkspaceManager(options)
    val className = requireOption(options, "class")
    val output = requireOption(options, "output")
    val autoUnicodeDecode = findOptionalOption(options, "auto-unicode-decode")?.toBooleanStrictOrNull() ?: true
    ensureParentDirectory(output)
    val result = withSuppressedThirdPartyOutput {
        manager.exportSmali(
            className = className,
            outputPath = output,
            autoUnicodeDecode = autoUnicodeDecode,
        )
    }
    cliOutput().println("output=${result.outputPath}")
}

private fun runWorkspaceExportJava(options: Map<String, List<String>>) = runBlocking {
    val manager = requireWorkspaceManager(options)
    val className = requireOption(options, "class")
    val output = requireOption(options, "output")
    ensureParentDirectory(output)
    val result = withSuppressedThirdPartyOutput {
        manager.exportJava(className = className, outputPath = output)
    }
    cliOutput().println("output=${result.outputPath}")
}

private fun runWorkspaceManifest(options: Map<String, List<String>>) {
    val manifest = withSuppressedThirdPartyOutput {
        requireWorkspaceManager(options).manifest()
    }
    val outputText = when (requireOutputFormat(options, default = OutputFormat.Text)) {
        OutputFormat.Text -> manifest.manifest
        OutputFormat.Json -> manifest.toCoreJsonString()
    }
    writeOutput(outputText, findOptionalOption(options, "output-file"))
}

private fun runWorkspaceRes(options: Map<String, List<String>>) {
    val listing = withSuppressedThirdPartyOutput {
        requireWorkspaceManager(options).resources()
    }
    val outputText = when (requireOutputFormat(options, default = OutputFormat.Text)) {
        OutputFormat.Text -> formatWorkspaceResources(listing)
        OutputFormat.Json -> listing.toCoreJsonString()
    }
    writeOutput(outputText, findOptionalOption(options, "output-file"))
}

private fun parseOptions(args: List<String>): Map<String, List<String>> {
    if (args.isEmpty()) return emptyMap()

    val options = linkedMapOf<String, MutableList<String>>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
        require(key.startsWith("--")) { "参数格式错误: $key" }
        require(index + 1 < args.size) { "参数缺少值: $key" }
        options.getOrPut(key.removePrefix("--")) { mutableListOf() }.add(args[index + 1])
        index += 2
    }
    return options
}

private fun requireOption(
    options: Map<String, List<String>>,
    key: String,
): String {
    return findOptionalOption(options, key) ?: error("缺少参数 --$key")
}

private fun findOptionalOption(
    options: Map<String, List<String>>,
    key: String,
): String? {
    val values = options[key].orEmpty()
        .map(String::trim)
        .filter(String::isNotEmpty)
    if (values.isEmpty()) return null
    require(values.size == 1) { "参数 --$key 只能传一次" }
    return values.single()
}

private fun requireQueryText(options: Map<String, List<String>>): String {
    val queryFile = findOptionalOption(options, "query-file")
    val queryJson = findOptionalOption(options, "query-json")
    require((queryFile == null) != (queryJson == null)) { "参数 --query-file 与 --query-json 必须且只能传一个" }

    return when {
        queryFile != null -> requireExistingFile(queryFile).readText(Charsets.UTF_8)
        queryJson != null -> queryJson
        else -> error("缺少查询参数")
    }
}

private fun requireDexInput(options: Map<String, List<String>>): String {
    val inputFile = requireExistingFile(requireOption(options, "input"))
    require(inputFile.extension.equals("dex", ignoreCase = true)) { "当前命令仅支持单个 dex 文件输入" }

    val normalizedPath = inputFile.absolutePath
    require(DexEngine.isDex(normalizedPath)) { "输入文件不是有效的 dex: $normalizedPath" }
    return normalizedPath
}

private fun requireInspectOrSearchInputs(options: Map<String, List<String>>): List<String> {
    val inputs = options["input"].orEmpty()
        .map(String::trim)
        .filter(String::isNotEmpty)
    require(inputs.isNotEmpty()) { "缺少参数 --input" }

    val normalizedInputs = inputs.map { input ->
        requireExistingFile(input).absolutePath
    }

    if (normalizedInputs.size == 1) {
        val singleInput = normalizedInputs.single()
        if (singleInput.endsWith(".apk", ignoreCase = true)) {
            return normalizedInputs
        }
        require(DexEngine.isDex(singleInput)) { "输入文件不是有效的 dex: $singleInput" }
        return normalizedInputs
    }

    normalizedInputs.forEach { input ->
        require(!input.endsWith(".apk", ignoreCase = true)) {
            "多输入模式暂不支持 apk，请仅传多个 dex 文件"
        }
        require(DexEngine.isDex(input)) { "输入文件不是有效的 dex: $input" }
    }
    return normalizedInputs
}

private fun requireWorkspaceInitInputs(options: Map<String, List<String>>): List<String> {
    val inputs = options["input"].orEmpty()
        .map(String::trim)
        .filter(String::isNotEmpty)
    require(inputs.isNotEmpty()) { "缺少参数 --input" }
    return inputs
}

private inline fun <reified T> runAdvancedSearch(
    options: Map<String, List<String>>,
    inputProvider: (Map<String, List<String>>) -> List<String>,
    executor: (DexEngine, List<String>) -> List<T>,
) {
    val inputs = inputProvider(options)
    val outputFormat = requireOutputFormat(options, default = OutputFormat.Json)
    val limit = requireOptionalPositiveLimit(options)

    val results = withSuppressedThirdPartyOutput {
        DexEngine(inputs).useEngine { dexEngine ->
            executor(dexEngine, inputs)
        }
    }
    val limitedResults = limit?.let(results::take) ?: results
    val outputText = when (outputFormat) {
        OutputFormat.Json -> limitedResults.toCoreJsonString()
        OutputFormat.Text -> formatAdvancedSearchResults(limitedResults, inputs.size)
    }
    writeOutput(
        text = outputText,
        outputPath = findOptionalOption(options, "output-file"),
    )
}

private inline fun <reified T> runWorkspaceAdvancedSearch(
    options: Map<String, List<String>>,
    executor: (WorkspaceManager) -> List<T>,
) {
    val manager = requireWorkspaceManager(options)
    val inputCount = manager.status().metadata.input.binding.resolvedEntries.size
    val outputFormat = requireOutputFormat(options, default = OutputFormat.Json)
    val limit = requireOptionalPositiveLimit(options)
    val results = withSuppressedThirdPartyOutput { executor(manager) }
    val limitedResults = limit?.let(results::take) ?: results
    val outputText = when (outputFormat) {
        OutputFormat.Json -> limitedResults.toCoreJsonString()
        OutputFormat.Text -> formatAdvancedSearchResults(limitedResults, inputCount)
    }
    writeOutput(
        text = outputText,
        outputPath = findOptionalOption(options, "output-file"),
    )
}

private inline fun <T> DexEngine.useEngine(block: (DexEngine) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}

private fun requireExistingFile(path: String): File {
    val file = File(path)
    require(file.exists()) { "输入文件不存在: ${file.absolutePath}" }
    require(file.isFile) { "输入路径必须是文件: ${file.absolutePath}" }
    return file
}

private fun requireWorkspaceManager(options: Map<String, List<String>>): WorkspaceManager {
    return WorkspaceManager(
        workspaceDir = File(requireOption(options, "workspace")).absoluteFile,
        toolVersion = currentVersion(),
    )
}

private fun ensureParentDirectory(path: String) {
    File(path).absoluteFile.parentFile?.mkdirs()
}

private fun requireOutputFormat(
    options: Map<String, List<String>>,
    default: OutputFormat = OutputFormat.Text,
): OutputFormat {
    val value = findOptionalOption(options, "output-format") ?: return default
    return when (value.lowercase()) {
        "text" -> OutputFormat.Text
        "json" -> OutputFormat.Json
        else -> error("不支持的输出格式: $value")
    }
}

private fun requireOptionalPositiveLimit(options: Map<String, List<String>>): Int? {
    val value = findOptionalOption(options, "limit") ?: return null
    return value.toIntOrNull()?.takeIf { it > 0 } ?: error("参数 --limit 必须是正整数")
}

private fun cliOutput(): PrintStream = cliStreams.get()?.output ?: System.out

private fun cliError(): PrintStream = cliStreams.get()?.error ?: System.err

private inline fun <T> withSuppressedThirdPartyOutput(block: () -> T): T {
    val originalOut = System.out
    val originalErr = System.err
    val capturedStdout = ByteArrayOutputStream()
    val capturedStderr = ByteArrayOutputStream()
    val temporaryOut = PrintStream(capturedStdout, true, Charsets.UTF_8)
    val temporaryErr = PrintStream(capturedStderr, true, Charsets.UTF_8)
    System.setOut(temporaryOut)
    System.setErr(temporaryErr)
    return try {
        block()
    } catch (throwable: Throwable) {
        replayCapturedLogs(capturedStdout, capturedStderr)
        throw throwable
    } finally {
        temporaryOut.flush()
        temporaryErr.flush()
        temporaryOut.close()
        temporaryErr.close()
        System.setOut(originalOut)
        System.setErr(originalErr)
    }
}

private fun replayCapturedLogs(
    capturedStdout: ByteArrayOutputStream,
    capturedStderr: ByteArrayOutputStream,
) {
    val stderr = cliError()
    val stdoutText = capturedStdout.toString(Charsets.UTF_8).trimEnd()
    val stderrText = capturedStderr.toString(Charsets.UTF_8).trimEnd()
    if (stdoutText.isNotEmpty()) {
        stderr.println(stdoutText)
    }
    if (stderrText.isNotEmpty()) {
        stderr.println(stderrText)
    }
}

private fun parseWorkspaceInputKind(value: String?): WorkspaceInputKind? {
    if (value == null) return null
    return when (value.lowercase()) {
        "apk" -> WorkspaceInputKind.Apk
        "dex" -> WorkspaceInputKind.Dex
        "dexs" -> WorkspaceInputKind.Dexs
        "auto" -> null
        else -> error("不支持的 workspace 输入类型: $value")
    }
}

private fun formatAdvancedSearchResults(
    results: List<*>,
    inputCount: Int,
): String {
    val rows = when {
        results.isEmpty() -> emptyList()
        results.first() is DexClassHit -> {
            @Suppress("UNCHECKED_CAST")
            formatClassRows(results as List<DexClassHit>, inputCount)
        }

        results.first() is DexMethodHit -> {
            @Suppress("UNCHECKED_CAST")
            formatMethodRows(results as List<DexMethodHit>, inputCount)
        }

        results.first() is DexFieldHit -> {
            @Suppress("UNCHECKED_CAST")
            formatFieldRows(results as List<DexFieldHit>, inputCount)
        }

        else -> error("不支持的结果类型")
    }

    return buildString {
        appendLine("count=${results.size}")
        appendLine("shown=${results.size}")
        rows.forEach { row ->
            appendLine(row)
        }
    }.trimEnd()
}

private fun formatClassRows(
    results: List<DexClassHit>,
    inputCount: Int,
): List<String> {
    return results.map { result ->
        formatHitRow(
            inputCount = inputCount,
            columns = listOf(result.name, result.descriptor),
            sourceDexPath = result.sourceDexPath,
        )
    }
}

private fun formatMethodRows(
    results: List<DexMethodHit>,
    inputCount: Int,
): List<String> {
    return results.map { result ->
        formatHitRow(
            inputCount = inputCount,
            columns = listOf(result.className, result.name, result.descriptor),
            sourceDexPath = result.sourceDexPath,
        )
    }
}

private fun formatFieldRows(
    results: List<DexFieldHit>,
    inputCount: Int,
): List<String> {
    return results.map { result ->
        formatHitRow(
            inputCount = inputCount,
            columns = listOf(result.className, result.name, result.descriptor),
            sourceDexPath = result.sourceDexPath,
        )
    }
}

private fun formatHitRow(
    inputCount: Int,
    columns: List<String>,
    sourceDexPath: String?,
): String {
    return if (inputCount == 1) {
        columns.joinToString(separator = "\t")
    } else {
        (columns + sourceDexPath.orEmpty()).joinToString(separator = "\t")
    }
}

private fun inferInspectOutputKind(
    inputs: List<String>,
    archiveInfo: DexArchiveInfo,
): WorkspaceInputKind {
    return when {
        inputs.size > 1 -> WorkspaceInputKind.Dexs
        archiveInfo.kind == DexInputKind.Apk -> WorkspaceInputKind.Apk
        else -> WorkspaceInputKind.Dex
    }
}

private fun buildInspectJsonOutput(
    inputKind: WorkspaceInputKind,
    archiveInfo: DexArchiveInfo,
): kotlinx.serialization.json.JsonObject {
    return buildJsonObject {
        put("inputType", inputKind.name.lowercase())
        put("archiveInfo", buildJsonObject {
            put("kind", archiveInfo.kind.name.lowercase())
            put("inputs", buildJsonArray {
                archiveInfo.inputs.forEach { input ->
                    add(buildJsonObject {
                        put("path", input.path)
                    })
                }
            })
            put("dexCount", archiveInfo.dexCount)
            archiveInfo.classCount?.let { put("classCount", it) }
        })
    }
}

private fun formatInspectOutput(
    inputKind: WorkspaceInputKind,
    inputs: List<String>,
    archiveInfo: DexArchiveInfo,
) : String {
    return when (inputKind) {
        WorkspaceInputKind.Apk -> {
            buildString {
                appendLine("type=apk")
                appendLine("input=${inputs.single()}")
                appendLine("dexCount=${archiveInfo.dexCount}")
            }.trimEnd()
        }

        WorkspaceInputKind.Dex -> {
            buildString {
                appendLine("type=dex")
                appendLine("input=${inputs.single()}")
                appendLine("dexCount=${archiveInfo.dexCount}")
                appendLine("classCount=${archiveInfo.classCount ?: 0}")
            }.trimEnd()
        }

        WorkspaceInputKind.Dexs -> {
            buildString {
                appendLine("type=dexs")
                appendLine("inputCount=${inputs.size}")
                inputs.forEach { input ->
                    appendLine("input=$input")
                }
                appendLine("dexCount=${archiveInfo.dexCount}")
                appendLine("classCount=${archiveInfo.classCount ?: 0}")
            }.trimEnd()
        }
    }
}

private fun formatWorkspaceStatus(status: WorkspaceStatus): String {
    return buildString {
        appendLine("workspaceId=${status.metadata.workspaceId}")
        appendLine("inputType=${status.metadata.input.type.name.lowercase()}")
        appendLine("bindingKind=${status.metadata.input.binding.kind}")
        appendLine("entryCount=${status.metadata.input.binding.resolvedEntries.size}")
        appendLine("fingerprint=${status.metadata.input.fingerprint}")
        appendLine("schemaVersion=${status.metadata.schemaVersion}")
        appendLine("layoutVersion=${status.metadata.layoutVersion}")
        appendLine("toolVersion=${status.metadata.toolVersion}")
        appendLine("cachePresent=${status.cachePresent}")
        appendLine("runsPresent=${status.runsPresent}")
    }.trimEnd()
}

private fun formatWorkspaceInspect(
    inputKind: WorkspaceInputKind,
    archiveInfo: DexArchiveInfo,
): String {
    return buildString {
        appendLine("type=${inputKind.name.lowercase()}")
        appendLine("dexCount=${archiveInfo.dexCount}")
        archiveInfo.classCount?.let { appendLine("classCount=$it") }
        archiveInfo.inputs.forEach { input ->
            appendLine("input=${input.path}")
        }
    }.trimEnd()
}

private fun formatWorkspaceCapabilities(capabilities: WorkspaceCapabilities): String {
    return buildString {
        appendLine("inspect=${capabilities.inspect}")
        appendLine("findClass=${capabilities.findClass}")
        appendLine("findMethod=${capabilities.findMethod}")
        appendLine("findField=${capabilities.findField}")
        appendLine("exportDex=${capabilities.exportDex}")
        appendLine("exportSmali=${capabilities.exportSmali}")
        appendLine("exportJava=${capabilities.exportJava}")
        appendLine("manifest=${capabilities.manifest}")
        appendLine("res=${capabilities.res}")
    }.trimEnd()
}

private fun formatWorkspaceResources(listing: WorkspaceResourceListing): String {
    return buildString {
        appendLine("resourcesArscPresent=${listing.resourcesArscPresent}")
        appendLine("entryCount=${listing.entries.size}")
        listing.entries.forEach { entry ->
            appendLine("entry=${entry.path}")
        }
    }.trimEnd()
}

private fun writeOutput(
    text: String,
    outputPath: String?,
) {
    if (outputPath == null) {
        cliOutput().println(text)
        return
    }

    ensureParentDirectory(outputPath)
    val outputFile = File(outputPath).absoluteFile
    outputFile.writeText(text, Charsets.UTF_8)
    cliOutput().println("output=${outputFile.absolutePath}")
}

private fun handleHelpCommand(
    args: List<String>,
    output: PrintStream = System.out,
) {
    if (args.isEmpty()) {
        printUsage(output)
        return
    }

    if (args.first() == WORKSPACE_ROOT_COMMAND) {
        val subcommand = args.drop(1).firstOrNull()
        if (subcommand == null) {
            printWorkspaceUsage(output)
            return
        }
        val command = findWorkspaceCommand(subcommand)
            ?: failWithRootUsage("不支持的 workspace 子命令: $subcommand")
        printWorkspaceCommandUsage(command, output)
        return
    }

    val commandName = args.first()
    val command = findCommand(commandName) ?: failWithRootUsage("不支持的命令: $commandName")
    printCommandUsage(command, output)
}

private fun shouldPrintCommandHelp(args: List<String>): Boolean {
    if (args.isEmpty()) return false
    if (args.size == 1 && args.single() in COMMAND_HELP_COMMANDS) return true
    return args.last() in OPTION_HELP_COMMANDS
}

private fun printUsage(output: PrintStream = System.out) {
    output.println(rootUsageText())
}

private fun printWorkspaceUsage(output: PrintStream = System.out) {
    output.println(workspaceUsageText())
}

private fun printCommandUsage(
    command: CommandSpec,
    output: PrintStream = System.out,
) {
    output.println(commandUsageText(command))
}

private fun printWorkspaceCommandUsage(
    command: WorkspaceCommandSpec,
    output: PrintStream = System.out,
) {
    output.println(workspaceCommandUsageText(command))
}

private fun failWithRootUsage(message: String): Nothing {
    throw CliUsageException(message, rootUsageText())
}

private fun failWithCommandUsage(
    command: CommandSpec,
    message: String,
): Nothing {
    throw CliUsageException(message, commandUsageText(command))
}

private fun failWithWorkspaceCommandUsage(
    command: WorkspaceCommandSpec,
    message: String,
): Nothing {
    throw CliUsageException(message, workspaceCommandUsageText(command))
}

private fun printVersion(output: PrintStream = System.out) {
    output.println("$CLI_NAME ${currentVersion()}")
}

private fun rootUsageText(): String {
    return buildString {
        appendLine("用法:")
        appendLine("  $CLI_NAME <命令> [参数]")
        appendLine("  $CLI_NAME workspace <子命令> [参数]")
        appendLine("  $CLI_NAME help <命令>")
        appendLine("  $CLI_NAME help workspace <子命令>")
        appendLine("  $CLI_NAME --help")
        appendLine("  $CLI_NAME --version")
        appendLine()
        appendLine("命令:")
        COMMANDS.forEach { command ->
            appendLine("  ${command.name.padEnd(18)} ${command.summary}")
        }
        appendLine("  ${WORKSPACE_ROOT_COMMAND.padEnd(18)} 工程化工作区命令")
        appendLine()
        appendLine("全局说明:")
        appendLine("  运行要求：Java 21")
        appendLine("  inspect/find 在多输入模式下仅支持多个 dex 文件，不支持混合传入 apk")
        appendLine("  导出命令当前仍只支持单个 dex 输入")
        appendLine("  workspace 模式为显式有状态模式，顶层命令保持无状态")
    }.trimEnd()
}

private fun workspaceUsageText(): String {
    return buildString {
        appendLine("命令:")
        appendLine("  $WORKSPACE_ROOT_COMMAND")
        appendLine("说明:")
        appendLine("  工程化工作区命令。工作区状态固定写入 <workspace>/.dexclub-cli/。")
        appendLine()
        appendLine("用法:")
        appendLine("  $CLI_NAME workspace <子命令> [参数]")
        appendLine("  $CLI_NAME help workspace <子命令>")
        appendLine()
        appendLine("子命令:")
        WORKSPACE_COMMANDS.forEach { command ->
            appendLine("  ${command.name.padEnd(18)} ${command.summary}")
        }
    }.trimEnd()
}

private fun commandUsageText(command: CommandSpec): String {
    return buildString {
        appendLine("命令:")
        appendLine("  ${command.name}")
        appendLine("说明:")
        appendLine("  ${command.summary}")
        appendLine()
        appendLine("用法:")
        appendLine("  $CLI_NAME ${command.name} ${command.argumentsUsage}".trimEnd())
        if (command.details.isNotEmpty()) {
            appendLine()
            appendLine("补充:")
            command.details.forEach { detail ->
                appendLine("  $detail")
            }
        }
    }.trimEnd()
}

private fun workspaceCommandUsageText(command: WorkspaceCommandSpec): String {
    return buildString {
        appendLine("命令:")
        appendLine("  $WORKSPACE_ROOT_COMMAND ${command.name}")
        appendLine("说明:")
        appendLine("  ${command.summary}")
        appendLine()
        appendLine("用法:")
        appendLine("  $CLI_NAME $WORKSPACE_ROOT_COMMAND ${command.name} ${command.argumentsUsage}".trimEnd())
        if (command.details.isNotEmpty()) {
            appendLine()
            appendLine("补充:")
            command.details.forEach { detail ->
                appendLine("  $detail")
            }
        }
    }.trimEnd()
}

private suspend fun runSingleDexExport(
    options: Map<String, List<String>>,
    exporter: suspend (dexEngine: DexEngine, input: String, className: String, output: String) -> String,
) {
    val input = requireDexInput(options)
    val className = requireOption(options, "class")
    val output = requireOption(options, "output")
    ensureParentDirectory(output)

    val outputPath = withSuppressedThirdPartyOutput {
        DexEngine(listOf(input)).useEngine { dexEngine ->
            exporter(dexEngine, input, className, output)
        }
    }
    cliOutput().println("output=$outputPath")
}

private fun findCommand(token: String): CommandSpec? = COMMANDS.firstOrNull { it.name == token }

private fun findWorkspaceCommand(token: String): WorkspaceCommandSpec? =
    WORKSPACE_COMMANDS.firstOrNull { it.name == token }

private fun currentVersion(): String {
    val implementationVersion = object {}.javaClass.`package`?.implementationVersion
    return implementationVersion?.takeIf(String::isNotBlank) ?: "dev"
}

private val HELP_COMMANDS = setOf(
    "help",
    "--help",
    "-h",
)

private val COMMAND_HELP_COMMANDS = HELP_COMMANDS

private val OPTION_HELP_COMMANDS = setOf(
    "--help",
    "-h",
)

private val VERSION_COMMANDS = setOf(
    "--version",
    "version",
)

private val COMMANDS = listOf(
    CommandSpec(
        name = "inspect",
        summary = "检查 apk 或 dex 输入，输出基本统计信息",
        argumentsUsage = "--input <apk|dex> [--input <dex> ...] [--output-format text|json] [--output-file <文件>]",
        details = listOf(
            "--input 可以重复传入；单输入支持 apk 或 dex，多输入仅支持多个 dex。",
            "--output-format 默认为 text。",
        ),
        handler = ::runInspect,
    ),
    CommandSpec(
        name = "find-class",
        summary = "按 JSON 查询条件查找类",
        argumentsUsage = "--input <apk|dex> [--input <dex> ...] (--query-file <文件> | --query-json <JSON>) [--output-format text|json] [--output-file <文件>] [--limit <数量>]",
        details = listOf(
            "--query-file 与 --query-json 必须且只能传一个。",
            "--output-format 默认为 json。",
            "--limit 未指定时输出全部结果。",
        ),
        handler = ::runFindClass,
    ),
    CommandSpec(
        name = "find-method",
        summary = "按 JSON 查询条件查找方法",
        argumentsUsage = "--input <apk|dex> [--input <dex> ...] (--query-file <文件> | --query-json <JSON>) [--output-format text|json] [--output-file <文件>] [--limit <数量>]",
        details = listOf(
            "--query-file 与 --query-json 必须且只能传一个。",
            "--output-format 默认为 json。",
            "--limit 未指定时输出全部结果。",
        ),
        handler = ::runFindMethod,
    ),
    CommandSpec(
        name = "find-field",
        summary = "按 JSON 查询条件查找字段",
        argumentsUsage = "--input <apk|dex> [--input <dex> ...] (--query-file <文件> | --query-json <JSON>) [--output-format text|json] [--output-file <文件>] [--limit <数量>]",
        details = listOf(
            "--query-file 与 --query-json 必须且只能传一个。",
            "--output-format 默认为 json。",
            "--limit 未指定时输出全部结果。",
        ),
        handler = ::runFindField,
    ),
    CommandSpec(
        name = "export-dex",
        summary = "导出目标类为单类 dex",
        argumentsUsage = "--input <dex> --class <类名> --output <输出 dex>",
        details = listOf(
            "当前仅支持单个 dex 输入。",
        ),
        handler = ::runExportDex,
    ),
    CommandSpec(
        name = "export-smali",
        summary = "导出目标类的 smali",
        argumentsUsage = "--input <dex> --class <类名> --output <输出 smali> [--auto-unicode-decode true|false]",
        details = listOf(
            "当前仅支持单个 dex 输入。",
            "--auto-unicode-decode 默认为 true。",
        ),
        handler = ::runExportSmali,
    ),
    CommandSpec(
        name = "export-java",
        summary = "反编译目标类为 Java",
        argumentsUsage = "--input <dex> --class <类名> --output <输出 java>",
        details = listOf(
            "当前仅支持单个 dex 输入。",
        ),
        handler = ::runExportJava,
    ),
)

private val WORKSPACE_COMMANDS = listOf(
    WorkspaceCommandSpec(
        name = "init",
        summary = "初始化 workspace 并写入最小工作区元数据",
        argumentsUsage = "--workspace <目录> --input <apk|dex|目录> [--input <dex> ...] [--type apk|dex|dexs|auto] [--output-format text|json] [--output-file <文件>]",
        details = listOf(
            "--workspace 指向项目目录，状态写入 <workspace>/.dexclub-cli/。",
            "--type 未指定时自动推断：单 apk -> apk，单 dex -> dex，目录或多个 dex -> dexs。",
        ),
        handler = ::runWorkspaceInit,
    ),
    WorkspaceCommandSpec(
        name = "status",
        summary = "查看 workspace 身份与状态摘要",
        argumentsUsage = "--workspace <目录> [--output-format text|json] [--output-file <文件>]",
        details = listOf(
            "status 只展示工作区身份、版本、输入绑定与状态摘要，不做输入深度分析。",
        ),
        handler = ::runWorkspaceStatus,
    ),
    WorkspaceCommandSpec(
        name = "inspect",
        summary = "查看当前 workspace 绑定输入的分析摘要",
        argumentsUsage = "--workspace <目录> [--output-format text|json] [--output-file <文件>]",
        details = listOf(
            "inspect 语义对齐顶层 inspect，但输入来源固定为当前 workspace。",
        ),
        handler = ::runWorkspaceInspect,
    ),
    WorkspaceCommandSpec(
        name = "capabilities",
        summary = "查看当前 workspace 输入类型支持的能力矩阵",
        argumentsUsage = "--workspace <目录> [--output-format text|json] [--output-file <文件>]",
        handler = ::runWorkspaceCapabilities,
    ),
    WorkspaceCommandSpec(
        name = "find-class",
        summary = "在 workspace 绑定输入上按 JSON 查询条件查找类",
        argumentsUsage = "--workspace <目录> (--query-file <文件> | --query-json <JSON>) [--output-format text|json] [--output-file <文件>] [--limit <数量>]",
        handler = ::runWorkspaceFindClass,
    ),
    WorkspaceCommandSpec(
        name = "find-method",
        summary = "在 workspace 绑定输入上按 JSON 查询条件查找方法",
        argumentsUsage = "--workspace <目录> (--query-file <文件> | --query-json <JSON>) [--output-format text|json] [--output-file <文件>] [--limit <数量>]",
        handler = ::runWorkspaceFindMethod,
    ),
    WorkspaceCommandSpec(
        name = "find-field",
        summary = "在 workspace 绑定输入上按 JSON 查询条件查找字段",
        argumentsUsage = "--workspace <目录> (--query-file <文件> | --query-json <JSON>) [--output-format text|json] [--output-file <文件>] [--limit <数量>]",
        handler = ::runWorkspaceFindField,
    ),
    WorkspaceCommandSpec(
        name = "export-dex",
        summary = "从 workspace 绑定输入导出目标类为单类 dex",
        argumentsUsage = "--workspace <目录> --class <类名> --output <输出 dex>",
        handler = ::runWorkspaceExportDex,
    ),
    WorkspaceCommandSpec(
        name = "export-smali",
        summary = "从 workspace 绑定输入导出目标类的 smali",
        argumentsUsage = "--workspace <目录> --class <类名> --output <输出 smali> [--auto-unicode-decode true|false]",
        handler = ::runWorkspaceExportSmali,
    ),
    WorkspaceCommandSpec(
        name = "export-java",
        summary = "从 workspace 绑定输入导出目标类的 Java",
        argumentsUsage = "--workspace <目录> --class <类名> --output <输出 java>",
        handler = ::runWorkspaceExportJava,
    ),
    WorkspaceCommandSpec(
        name = "manifest",
        summary = "读取 apk workspace 的 AndroidManifest.xml",
        argumentsUsage = "--workspace <目录> [--output-format text|json] [--output-file <文件>]",
        details = listOf(
            "仅支持 apk workspace；dex 与 dexs 会显式失败。",
        ),
        handler = ::runWorkspaceManifest,
    ),
    WorkspaceCommandSpec(
        name = "res",
        summary = "列出 apk workspace 的资源文件入口",
        argumentsUsage = "--workspace <目录> [--output-format text|json] [--output-file <文件>]",
        details = listOf(
            "仅支持 apk workspace；dex 与 dexs 会显式失败。",
        ),
        handler = ::runWorkspaceRes,
    ),
)

private const val CLI_NAME = "dexclub-cli"
private const val WORKSPACE_ROOT_COMMAND = "workspace"
