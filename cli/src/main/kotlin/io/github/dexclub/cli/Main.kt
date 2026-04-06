package io.github.dexclub.cli

import io.github.dexclub.core.DexEngine
import io.github.dexclub.core.config.SmaliRenderConfig
import io.github.dexclub.core.model.DexInputKind
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.SmaliExportRequest
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty() || args.first() in HELP_COMMANDS) {
        printUsage()
        return
    }

    runCatching {
        when (val command = args.first()) {
            "inspect" -> runInspect(parseOptions(args.drop(1)))
            "export-dex" -> runExportDex(parseOptions(args.drop(1)))
            "export-smali" -> runExportSmali(parseOptions(args.drop(1)))
            "export-java" -> runExportJava(parseOptions(args.drop(1)))
            "search-class" -> runSearchClass(parseOptions(args.drop(1)))
            "search-string" -> runSearchString(parseOptions(args.drop(1)))
            else -> error("不支持的命令: $command")
        }
    }.onFailure { throwable ->
        System.err.println("执行失败: ${throwable.message ?: throwable::class.simpleName.orEmpty()}")
        exitProcess(1)
    }
}

private fun runInspect(options: Map<String, String>) {
    val input = requireOption(options, "input")
    val inputFile = File(input)
    require(inputFile.exists()) { "输入文件不存在: ${inputFile.absolutePath}" }
    require(inputFile.isFile) { "输入路径必须是文件: ${inputFile.absolutePath}" }

    val normalizedPath = inputFile.absolutePath
    DexEngine(listOf(normalizedPath)).useEngine { dexEngine ->
        val archiveInfo = dexEngine.inspect()
        when (archiveInfo.kind) {
            DexInputKind.Apk -> {
                println("type=apk")
                println("input=$normalizedPath")
                println("dexCount=${archiveInfo.dexCount}")
            }

            DexInputKind.Dex -> {
                println("type=dex")
                println("input=$normalizedPath")
                println("dexCount=${archiveInfo.dexCount}")
                println("classCount=${archiveInfo.classCount ?: 0}")
            }

            DexInputKind.Unknown -> {
                require(DexEngine.isDex(normalizedPath)) { "输入文件不是有效的 dex: $normalizedPath" }
                println("type=dex")
                println("input=$normalizedPath")
                println("dexCount=${dexEngine.dexCount()}")
                println("classCount=${dexEngine.classCount()}")
            }
        }
    }
}

private fun runExportDex(options: Map<String, String>) = runBlocking {
    val input = requireDexInput(options)
    val className = requireOption(options, "class")
    val output = requireOption(options, "output")
    ensureParentDirectory(output)

    val dexEngine = DexEngine(listOf(input))
    try {
        val exported = dexEngine.exportDex(
            DexExportRequest(
                className = className,
                sourceDexPath = input,
                outputPath = output,
            ),
        )
        println("output=${exported.outputPath}")
    } finally {
        dexEngine.close()
    }
}

private fun runExportSmali(options: Map<String, String>) = runBlocking {
    val input = requireDexInput(options)
    val className = requireOption(options, "class")
    val output = requireOption(options, "output")
    val autoUnicodeDecode = options["auto-unicode-decode"]?.toBooleanStrictOrNull() ?: true
    ensureParentDirectory(output)

    val dexEngine = DexEngine(listOf(input))
    try {
        val exported = dexEngine.exportSmali(
            SmaliExportRequest(
                className = className,
                sourceDexPath = input,
                outputPath = output,
                config = SmaliRenderConfig(autoUnicodeDecode = autoUnicodeDecode),
            ),
        )
        println("output=${exported.outputPath}")
    } finally {
        dexEngine.close()
    }
}

private fun runExportJava(options: Map<String, String>) = runBlocking {
    val input = requireDexInput(options)
    val className = requireOption(options, "class")
    val output = requireOption(options, "output")
    ensureParentDirectory(output)

    val dexEngine = DexEngine(listOf(input))
    try {
        val exported = dexEngine.exportJava(
            JavaExportRequest(
                className = className,
                sourceDexPath = input,
                outputPath = output,
            ),
        )
        println("output=${exported.outputPath}")
    } finally {
        dexEngine.close()
    }
}

private fun runSearchClass(options: Map<String, String>) {
    val input = requireOption(options, "input")
    val keyword = requireOption(options, "keyword")
    val limit = options["limit"]?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_SEARCH_LIMIT
    DexEngine(listOf(input)).useEngine { dexEngine ->
        val results = dexEngine.searchClassHitsByName(keyword)
        println("count=${results.size}")
        println("shown=${minOf(results.size, limit)}")
        results.take(limit).forEach { result ->
            println("${result.name}\t${result.descriptor}")
        }
    }
}

private fun runSearchString(options: Map<String, String>) {
    val input = requireOption(options, "input")
    val keyword = requireOption(options, "keyword")
    val limit = options["limit"]?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_SEARCH_LIMIT
    DexEngine(listOf(input)).useEngine { dexEngine ->
        val results = dexEngine.searchMethodHitsByString(keyword)
        println("count=${results.size}")
        println("shown=${minOf(results.size, limit)}")
        results.take(limit).forEach { result ->
            println("${result.className}\t${result.name}\t${result.descriptor}")
        }
    }
}

private fun parseOptions(args: List<String>): Map<String, String> {
    if (args.isEmpty()) return emptyMap()

    val options = linkedMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
        require(key.startsWith("--")) { "参数格式错误: $key" }
        require(index + 1 < args.size) { "参数缺少值: $key" }
        options[key.removePrefix("--")] = args[index + 1]
        index += 2
    }
    return options
}

private fun requireOption(
    options: Map<String, String>,
    key: String,
): String {
    return options[key]?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("缺少参数 --$key")
}

private fun requireDexInput(options: Map<String, String>): String {
    val input = requireOption(options, "input")
    val inputFile = File(input)
    require(inputFile.exists()) { "输入文件不存在: ${inputFile.absolutePath}" }
    require(inputFile.isFile) { "输入路径必须是文件: ${inputFile.absolutePath}" }
    require(inputFile.extension.equals("dex", ignoreCase = true)) { "当前命令仅支持单个 dex 文件输入" }

    val normalizedPath = inputFile.absolutePath
    require(DexEngine.isDex(normalizedPath)) { "输入文件不是有效的 dex: $normalizedPath" }
    return normalizedPath
}

private inline fun DexEngine.useEngine(block: (DexEngine) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}

private fun ensureParentDirectory(path: String) {
    File(path).absoluteFile.parentFile?.mkdirs()
}

private fun printUsage() {
    println(
        """
        用法:
          运行要求：Java 21
          inspect --input <apk|dex>
          export-dex --input <dex> --class <类名> --output <输出 dex>
          export-smali --input <dex> --class <类名> --output <输出 smali> [--auto-unicode-decode true|false]
          export-java --input <dex> --class <类名> --output <输出 java>
          search-class --input <apk|dex> --keyword <关键词> [--limit 100]
          search-string --input <apk|dex> --keyword <关键词> [--limit 100]
        """.trimIndent()
    )
}

private val HELP_COMMANDS = setOf(
    "help",
    "--help",
    "-h",
)

private const val DEFAULT_SEARCH_LIMIT = 100
