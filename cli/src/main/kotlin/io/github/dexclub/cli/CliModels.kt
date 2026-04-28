package io.github.dexclub.cli

import io.github.dexclub.core.api.dex.MethodDetailSection
import io.github.dexclub.core.api.shared.PageWindow

enum class OutputFormat {
    Text,
    Json,
}

sealed interface QueryInput {
    data class Json(val text: String) : QueryInput

    data class File(val path: String) : QueryInput
}

sealed interface CliRequest {
    val outputFormat: OutputFormat

    data class Help(
        val command: String? = null,
        override val outputFormat: OutputFormat = OutputFormat.Text,
    ) : CliRequest

    data class Version(
        override val outputFormat: OutputFormat = OutputFormat.Text,
    ) : CliRequest

    data class Init(
        val input: String,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class Status(
        val workdir: String?,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class Gc(
        val workdir: String?,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class Inspect(
        val workdir: String?,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class Manifest(
        val workdir: String?,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class ResTable(
        val workdir: String?,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class DecodeXml(
        val workdir: String?,
        val path: String,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class ListRes(
        val workdir: String?,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class ResolveRes(
        val workdir: String?,
        val resourceId: String? = null,
        val type: String? = null,
        val name: String? = null,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class FindRes(
        val workdir: String?,
        val query: QueryInput,
        val window: PageWindow,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class FindClass(
        val workdir: String?,
        val query: QueryInput,
        val window: PageWindow,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class FindMethod(
        val workdir: String?,
        val query: QueryInput,
        val window: PageWindow,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class InspectMethod(
        val workdir: String?,
        val descriptor: String,
        val includes: Set<MethodDetailSection>,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class FindField(
        val workdir: String?,
        val query: QueryInput,
        val window: PageWindow,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class FindClassUsingStrings(
        val workdir: String?,
        val query: QueryInput,
        val window: PageWindow,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class FindMethodUsingStrings(
        val workdir: String?,
        val query: QueryInput,
        val window: PageWindow,
        override val outputFormat: OutputFormat,
    ) : CliRequest

    data class ExportClassSmali(
        val workdir: String?,
        val className: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
        val autoUnicodeDecode: Boolean,
        override val outputFormat: OutputFormat = OutputFormat.Text,
    ) : CliRequest

    data class ExportClassDex(
        val workdir: String?,
        val className: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
        override val outputFormat: OutputFormat = OutputFormat.Text,
    ) : CliRequest

    data class ExportClassJava(
        val workdir: String?,
        val className: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
        override val outputFormat: OutputFormat = OutputFormat.Text,
    ) : CliRequest

    data class ExportMethodSmali(
        val workdir: String?,
        val methodSignature: String,
        val sourcePath: String?,
        val sourceEntry: String?,
        val output: String,
        val autoUnicodeDecode: Boolean,
        val mode: String,
        override val outputFormat: OutputFormat = OutputFormat.Text,
    ) : CliRequest
}

class CliUsageError(
    override val message: String,
    val usage: String,
    val hint: String? = null,
) : RuntimeException(message)
