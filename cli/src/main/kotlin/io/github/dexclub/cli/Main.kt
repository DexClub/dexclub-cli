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
            "extract-class-dex" -> runExtractClassDex(parseOptions(args.drop(1)))
            "render-smali" -> runRenderSmali(parseOptions(args.drop(1)))
            "decompile-java" -> runDecompileJava(parseOptions(args.drop(1)))
            "search-class" -> runSearchClass(parseOptions(args.drop(1)))
            "search-string" -> runSearchString(parseOptions(args.drop(1)))
            else -> error("不支持的命令: $command")
        }
    }.onFailure { throwable ->
        System.err.println("执行失败: ${throwable.message ?: throwable::class.simpleName.orEmpty()}")
        exitProcess(1)
    }
}

private fun runInspect(options: Map<String, List<String>>) {
    val inputs = requireInspectOrSearchInputs(options)
    DexEngine(inputs).useEngine { dexEngine ->
        val archiveInfo = dexEngine.inspect()
        when (archiveInfo.kind) {
            DexInputKind.Apk -> {
                val input = inputs.single()
                println("type=apk")
                println("input=$input")
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

private fun runExtractClassDex(options: Map<String, List<String>>) = runBlocking {
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

private fun runRenderSmali(options: Map<String, List<String>>) = runBlocking {
    val input = requireDexInput(options)
    val className = requireOption(options, "class")
    val output = requireOption(options, "output")
    val autoUnicodeDecode = findOptionalOption(options, "auto-unicode-decode")?.toBooleanStrictOrNull() ?: true
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

private fun runDecompileJava(options: Map<String, List<String>>) = runBlocking {
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

private fun runSearchClass(options: Map<String, List<String>>) {
    val inputs = requireInspectOrSearchInputs(options)
    val keyword = requireOption(options, "keyword")
    val limit = findOptionalOption(options, "limit")?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_SEARCH_LIMIT
    DexEngine(inputs).useEngine { dexEngine ->
        val results = dexEngine.searchClassHitsByName(keyword)
        println("count=${results.size}")
        println("shown=${minOf(results.size, limit)}")
        results.take(limit).forEach { result ->
            if (inputs.size == 1) {
                println("${result.name}\t${result.descriptor}")
            } else {
                println("${result.name}\t${result.descriptor}\t${result.sourceDexPath.orEmpty()}")
            }
        }
    }
}

private fun runSearchString(options: Map<String, List<String>>) {
    val inputs = requireInspectOrSearchInputs(options)
    val keyword = requireOption(options, "keyword")
    val limit = findOptionalOption(options, "limit")?.toIntOrNull()?.coerceAtLeast(1) ?: DEFAULT_SEARCH_LIMIT
    DexEngine(inputs).useEngine { dexEngine ->
        val results = dexEngine.searchMethodHitsByString(keyword)
        println("count=${results.size}")
        println("shown=${minOf(results.size, limit)}")
        results.take(limit).forEach { result ->
            if (inputs.size == 1) {
                println("${result.className}\t${result.name}\t${result.descriptor}")
            } else {
                println("${result.className}\t${result.name}\t${result.descriptor}\t${result.sourceDexPath.orEmpty()}")
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
        val normalizedKey = key.removePrefix("--")
        options.getOrPut(normalizedKey) { mutableListOf() }.add(args[index + 1])
        index += 2
    }
    return options
}

private fun requireOption(
    options: Map<String, List<String>>,
    key: String,
): String {
    return findOptionalOption(options, key)
        ?: error("缺少参数 --$key")
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
    val input = requireOption(options, "input")
    val inputFile = File(input)
    require(inputFile.exists()) { "输入文件不存在: ${inputFile.absolutePath}" }
    require(inputFile.isFile) { "输入路径必须是文件: ${inputFile.absolutePath}" }
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
        val inputFile = File(input)
        require(inputFile.exists()) { "输入文件不存在: ${inputFile.absolutePath}" }
        require(inputFile.isFile) { "输入路径必须是文件: ${inputFile.absolutePath}" }
        inputFile.absolutePath
    }

    if (normalizedInputs.size == 1) {
        val singleInput = normalizedInputs.single()
        val inputFile = File(singleInput)
        if (inputFile.extension.equals("apk", ignoreCase = true)) {
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
          inspect --input <apk|dex> [--input <dex> ...]
          extract-class-dex --input <dex> --class <类名> --output <输出 dex>
          render-smali --input <dex> --class <类名> --output <输出 smali> [--auto-unicode-decode true|false]
          decompile-java --input <dex> --class <类名> --output <输出 java>
          search-class --input <apk|dex> [--input <dex> ...] --keyword <关键词> [--limit 100]
          search-string --input <apk|dex> [--input <dex> ...] --keyword <关键词> [--limit 100]

        说明:
          inspect/search 在多输入模式下仅支持多个 dex 文件，不支持混合传入 apk
          extract-class-dex、render-smali、decompile-java 当前仍只支持单个 dex 输入
        """.trimIndent()
    )
}

private val HELP_COMMANDS = setOf(
    "help",
    "--help",
    "-h",
)

private const val DEFAULT_SEARCH_LIMIT = 100
