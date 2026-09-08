package io.github.dexclub.mcp

import io.github.dexclub.core.api.dex.FindClassQuery
import io.github.dexclub.core.api.dex.FindFieldQuery
import io.github.dexclub.core.api.dex.FindMethodQuery
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

internal const val MCP_QUERY_FILE_MAX_BYTES = 1_048_576

data class McpQueryFilePolicy(
    val loopback: Boolean,
    val allowedRoots: List<Path>,
) {
    companion object {
        fun fromConfig(config: HttpServerConfig): McpQueryFilePolicy {
            return McpQueryFilePolicy(
                loopback = isLoopbackHost(config.host),
                allowedRoots = config.queryRoots,
            )
        }
    }
}

@Serializable
private data class McpQueryDocument(
    val format: String,
    val formatVersion: Int,
    val kind: String,
    val query: JsonObject,
)

internal class McpQueryFileException(
    val code: String,
    override val message: String,
    val details: JsonObject? = null,
) : Exception(message)

internal fun McpApp.loadQueryFile(
    request: CallToolRequest,
    expectedKind: String,
): JsonObject {
    val arguments = request.arguments
    if (arguments?.containsKey("query") == true || arguments?.containsKey("query_json") == true) {
        throw McpQueryFileException(
            code = "query_contract_removed",
            message = "query and query_json were removed; use query_file",
        )
    }

    val value = arguments?.get("query_file")
        ?: throw McpQueryFileException("missing_argument", "query_file is required")
    val queryFile = (value as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: throw McpQueryFileException("invalid_argument", "query_file must be a non-empty absolute path string")

    val inputPath = runCatching { Paths.get(queryFile) }.getOrElse {
        throw McpQueryFileException("invalid_query_path", "query_file must be an absolute path")
    }
    if (!inputPath.isAbsolute) {
        throw McpQueryFileException("invalid_query_path", "query_file must be an absolute path")
    }

    val path = resolveAllowedPath(inputPath)
    if (!Files.isRegularFile(path)) {
        throw McpQueryFileException("query_file_not_found", "query_file is not a readable regular file")
    }

    val snapshot = readUtf8Snapshot(path)
    McpRuntimeDiagnostics.queryFileRead(path, snapshot.bytes.size, snapshot.sha256)
    val text = snapshot.text
    val root = try {
        strictJson.parseToJsonElement(text)
    } catch (_: Exception) {
        throw McpQueryFileException("invalid_query_json", "query_file must contain valid JSON")
    }
    val document = try {
        strictJson.decodeFromJsonElement(McpQueryDocument.serializer(), root)
    } catch (_: Exception) {
        throw McpQueryFileException("invalid_query_document", "query_file must contain a valid DexClub query document")
    }
    if (document.format != "dexclub-query" || document.formatVersion != 1) {
        throw McpQueryFileException("invalid_query_document", "query_file format or formatVersion is unsupported")
    }
    if (document.kind != expectedKind) {
        throw McpQueryFileException(
            "query_kind_mismatch",
            "query_file kind does not match the requested tool",
            kotlinx.serialization.json.buildJsonObject {
                put("expected_kind", expectedKind)
                put("actual_kind", document.kind)
            },
        )
    }

    try {
        when (expectedKind) {
            "find_classes" -> strictJson.decodeFromJsonElement(FindClassQuery.serializer(), document.query)
            "find_methods" -> strictJson.decodeFromJsonElement(FindMethodQuery.serializer(), document.query)
            "find_fields" -> strictJson.decodeFromJsonElement(FindFieldQuery.serializer(), document.query)
            else -> error("unsupported query kind: $expectedKind")
        }
    } catch (_: Exception) {
        throw McpQueryFileException("invalid_query", "query_file query does not match the requested query schema")
    }
    return document.query
}

private fun McpApp.resolveAllowedPath(inputPath: Path): Path {
    val normalized = inputPath.toAbsolutePath().normalize()
    if (queryFilePolicy.loopback) return normalized
    if (queryFilePolicy.allowedRoots.isEmpty()) {
        throw McpQueryFileException("query_path_not_allowed", "query_file roots are not configured for non-loopback MCP")
    }
    val realPath = runCatching { normalized.toRealPath() }
        .getOrElse { throw McpQueryFileException("query_file_not_found", "query_file is not accessible") }
    val allowed = queryFilePolicy.allowedRoots.any { root ->
        val realRoot = runCatching { root.toAbsolutePath().normalize().toRealPath() }.getOrNull() ?: return@any false
        realPath == realRoot || realPath.startsWith(realRoot)
    }
    if (!allowed) throw McpQueryFileException("query_path_not_allowed", "query_file is outside the configured query roots")
    return realPath
}

private data class QueryFileSnapshot(
    val text: String,
    val bytes: ByteArray,
    val sha256: String,
)

private fun readUtf8Snapshot(path: Path): QueryFileSnapshot {
    val bytes = try {
        Files.newInputStream(path).use { input ->
            input.readNBytes(MCP_QUERY_FILE_MAX_BYTES + 1)
        }
    } catch (_: Exception) {
        throw McpQueryFileException("query_file_not_found", "query_file is not readable")
    }
    if (bytes.size > MCP_QUERY_FILE_MAX_BYTES) {
        throw McpQueryFileException(
            "query_file_too_large",
            "query_file exceeds the 1 MiB limit",
            kotlinx.serialization.json.buildJsonObject {
                put("max_bytes", MCP_QUERY_FILE_MAX_BYTES)
                put("read_bytes", bytes.size)
            },
        )
    }
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        QueryFileSnapshot(
            text = decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF"),
            bytes = bytes,
            sha256 = sha256(bytes),
        )
    } catch (_: Exception) {
        throw McpQueryFileException("invalid_query_encoding", "query_file must be valid UTF-8")
    }
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private val strictJson = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
}
