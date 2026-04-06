package io.github.dexclub.cli

import io.github.dexclub.core.DexEngine
import io.github.dexclub.core.config.SmaliRenderConfig
import io.github.dexclub.core.model.DexInputKind
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.SmaliExportRequest
import kotlinx.coroutines.runBlocking
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

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    when (val firstArg = args.first()) {
        in HELP_COMMANDS -> {
            handleHelpCommand(args.drop(1))
            return
        }

        in VERSION_COMMANDS -> {
            printVersion()
            return
        }

        else -> runCommand(firstArg, args.drop(1))
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

private fun runInspect(options: Map<String, List<String>>) {
    val inputs = requireInspectOrSearchInputs(options)
    DexEngine(inputs).useEngine { dexEngine ->
        val archiveInfo = dexEngine.inspect()
        when (archiveInfo.kind) {
            DexInputKind.Apk -> {
                println("type=apk")
                println("input=${inputs.single()}")
                println("dexCount=${archiveInfo.dexCount}")
            }

            DexInputKind.Dex -> {
                if (inputs.size == 1) {
                    println("type=dex")
                    println("input=${inputs.single()}")
                } else {
                    println("type=dex-set")
                    println("inputCount=${inputs.size}")
                    inputs.forEach { input ->
                        println("input=$input")
                    }
                }
                println("dexCount=${archiveInfo.dexCount}")
                println("classCount=${archiveInfo.classCount ?: 0}")
            }

            DexInputKind.Unknown -> {
                error("输入类型无法识别")
            }
        }
    }
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

private fun runSearchClass(options: Map<String, List<String>>) {
    runSearch(options) { dexEngine, inputs, keyword, limit ->
        printSearchResults(
            results = dexEngine.searchClassHitsByName(keyword),
            limit = limit,
        ) { result ->
            if (inputs.size == 1) {
                "${result.name}\t${result.descriptor}"
            } else {
                "${result.name}\t${result.descriptor}\t${result.sourceDexPath.orEmpty()}"
            }
        }
    }
}

private fun runSearchString(options: Map<String, List<String>>) {
    runSearch(options) { dexEngine, inputs, keyword, limit ->
        printSearchResults(
            results = dexEngine.searchMethodHitsByString(keyword),
            limit = limit,
        ) { result ->
            if (inputs.size == 1) {
                "${result.className}\t${result.name}\t${result.descriptor}"
            } else {
                "${result.className}\t${result.name}\t${result.descriptor}\t${result.sourceDexPath.orEmpty()}"
            }
        }
    }
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

private fun ensureParentDirectory(path: String) {
    File(path).absoluteFile.parentFile?.mkdirs()
}

private fun handleHelpCommand(args: List<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    val commandName = args.first()
    val command = findCommand(commandName) ?: failWithRootUsage("不支持的命令: $commandName")
    printCommandUsage(command)
}

private fun shouldPrintCommandHelp(args: List<String>): Boolean {
    if (args.isEmpty()) return false
    if (args.size == 1 && args.single() in COMMAND_HELP_COMMANDS) return true
    return args.last() in OPTION_HELP_COMMANDS
}

private fun printUsage(output: PrintStream = System.out) {
    output.println(rootUsageText())
}

private fun printCommandUsage(
    command: CommandSpec,
    output: PrintStream = System.out,
) {
    output.println(commandUsageText(command))
}

private fun failWithRootUsage(message: String): Nothing {
    System.err.println("执行失败: $message")
    System.err.println()
    printUsage(System.err)
    exitProcess(1)
}

private fun failWithCommandUsage(
    command: CommandSpec,
    message: String,
): Nothing {
    System.err.println("执行失败: $message")
    System.err.println()
    printCommandUsage(command, System.err)
    exitProcess(1)
}

private fun printVersion() {
    println("$CLI_NAME ${currentVersion()}")
}

private fun rootUsageText(): String {
    return buildString {
        appendLine("用法:")
        appendLine("  $CLI_NAME <命令> [参数]")
        appendLine("  $CLI_NAME help <命令>")
        appendLine("  $CLI_NAME --help")
        appendLine("  $CLI_NAME --version")
        appendLine()
        appendLine("命令:")
        COMMANDS.forEach { command ->
            appendLine("  ${command.name.padEnd(18)} ${command.summary}")
        }
        appendLine()
        appendLine("全局说明:")
        appendLine("  运行要求：Java 21")
        appendLine("  inspect/search 在多输入模式下仅支持多个 dex 文件，不支持混合传入 apk")
        appendLine("  导出命令当前仍只支持单个 dex 输入")
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

private suspend fun runSingleDexExport(
    options: Map<String, List<String>>,
    exporter: suspend (dexEngine: DexEngine, input: String, className: String, output: String) -> String,
) {
    val input = requireDexInput(options)
    val className = requireOption(options, "class")
    val output = requireOption(options, "output")
    ensureParentDirectory(output)

    val outputPath = DexEngine(listOf(input)).useEngine { dexEngine ->
        exporter(dexEngine, input, className, output)
    }
    println("output=$outputPath")
}

private inline fun runSearch(
    options: Map<String, List<String>>,
    block: (dexEngine: DexEngine, inputs: List<String>, keyword: String, limit: Int) -> Unit,
) {
    val inputs = requireInspectOrSearchInputs(options)
    val keyword = requireOption(options, "keyword")
    val limit = requirePositiveLimit(options)
    DexEngine(inputs).useEngine { dexEngine ->
        block(dexEngine, inputs, keyword, limit)
    }
}

private fun requirePositiveLimit(options: Map<String, List<String>>): Int {
    return findOptionalOption(options, "limit")
        ?.toIntOrNull()
        ?.coerceAtLeast(1)
        ?: DEFAULT_SEARCH_LIMIT
}

private inline fun <T> printSearchResults(
    results: List<T>,
    limit: Int,
    rowFormatter: (T) -> String,
) {
    println("count=${results.size}")
    println("shown=${minOf(results.size, limit)}")
    results.take(limit).forEach { result ->
        println(rowFormatter(result))
    }
}

private fun findCommand(token: String): CommandSpec? = COMMANDS.firstOrNull { it.name == token }

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
        argumentsUsage = "--input <apk|dex> [--input <dex> ...]",
        details = listOf(
            "--input 可以重复传入；单输入支持 apk 或 dex，多输入仅支持多个 dex。",
        ),
        handler = ::runInspect,
    ),
    CommandSpec(
        name = "search-class",
        summary = "按类名关键词搜索类",
        argumentsUsage = "--input <apk|dex> [--input <dex> ...] --keyword <关键词> [--limit <数量>]",
        details = listOf(
            "--input 可以重复传入；单输入支持 apk 或 dex，多输入仅支持多个 dex。",
            "--limit 默认为 100。",
        ),
        handler = ::runSearchClass,
    ),
    CommandSpec(
        name = "search-string",
        summary = "按字符串常量搜索方法",
        argumentsUsage = "--input <apk|dex> [--input <dex> ...] --keyword <关键词> [--limit <数量>]",
        details = listOf(
            "--input 可以重复传入；单输入支持 apk 或 dex，多输入仅支持多个 dex。",
            "--limit 默认为 100。",
        ),
        handler = ::runSearchString,
    ),
    CommandSpec(
        name = "export-dex",
        summary = "导出目标类所在 dex",
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

private const val DEFAULT_SEARCH_LIMIT = 100
private const val CLI_NAME = "dexclub-cli"
