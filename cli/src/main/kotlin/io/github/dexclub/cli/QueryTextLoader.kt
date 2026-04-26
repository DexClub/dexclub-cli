package io.github.dexclub.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.json.Json

internal class QueryTextLoader {
    private val json = Json

    fun load(query: QueryInput, usage: String): String {
        val text = when (query) {
            is QueryInput.Json -> query.text
            is QueryInput.File -> readQueryFile(query.path, usage)
        }.trim()
        if (text.isEmpty()) {
            throw CliUsageError(
                message = "query JSON must not be empty",
                usage = usage,
            )
        }
        parseJsonOrNull(text)?.let { return text }

        unwrapShellQuoted(text)
            ?.takeIf { parseJsonOrNull(it) != null }
            ?.let { return it }

        throw CliUsageError(
            message = "query JSON is not valid JSON",
            usage = usage,
        )
    }

    private fun readQueryFile(path: String, usage: String): String {
        val file = normalize(path)
        if (!Files.exists(file)) {
            throw CliUsageError(
                message = "query file does not exist: $file",
                usage = usage,
            )
        }
        if (!Files.isRegularFile(file)) {
            throw CliUsageError(
                message = "query file is not a regular file: $file",
                usage = usage,
            )
        }
        return Files.readString(file, StandardCharsets.UTF_8)
    }

    private fun parseJsonOrNull(text: String): Any? =
        runCatching { json.parseToJsonElement(text) }.getOrNull()

    private fun unwrapShellQuoted(text: String): String? {
        if (text.length < 2) return null
        val first = text.first()
        val last = text.last()
        if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return text.substring(1, text.length - 1).trim()
        }
        return null
    }

    private fun normalize(path: String): Path = Paths.get(path).toAbsolutePath().normalize()
}
