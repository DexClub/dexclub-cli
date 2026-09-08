package io.github.dexclub.mcp

import io.github.dexclub.core.app.session.TargetExecutionContext
import io.github.dexclub.core.app.session.DexContextLease
import io.github.dexclub.core.app.contract.ResourceDecodeError
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal sealed interface ExecutionContextResolution {
    data class Ready(val context: TargetExecutionContext) : ExecutionContextResolution

    data class Failed(val result: CallToolResult) : ExecutionContextResolution
}

internal fun McpApp.validateToolVersion(request: CallToolRequest): CallToolResult? {
    val value = request.arguments?.get("version")
        ?: return errorResult("version is required", code = "missing_argument")
    val received = (value as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
        ?.takeIf(String::isNotBlank)
        ?: return errorResult("version must be a non-empty string", code = "invalid_argument")
    val contractValue = request.arguments?.get("mcp_contract_version")
        ?: return errorResult("mcp_contract_version is required", code = "missing_argument")
    val receivedContractVersion = contractValue.contractVersionOrNull()
        ?: return errorResult("mcp_contract_version must be an integer", code = "invalid_argument")
    if (received != McpBuildInfo.VERSION) {
        return versionMismatchResult(received, receivedContractVersion)
    }
    if (receivedContractVersion != McpBuildInfo.MCP_CONTRACT_VERSION) {
        return versionMismatchResult(received, receivedContractVersion)
    }
    return null
}

private fun McpApp.versionMismatchResult(received: String, receivedContractVersion: Int?): CallToolResult =
    errorResult(
        message = "DexClub MCP version or contract version mismatch. Update the MCP server and dexclub-analysis skill from the same release.",
        code = "version_mismatch",
        details = buildJsonObject {
            put("expected_version", McpBuildInfo.VERSION)
            put("received_version", received)
            put("server_contract_version", McpBuildInfo.MCP_CONTRACT_VERSION)
            receivedContractVersion?.let { put("received_contract_version", it) }
        },
    )

private fun JsonElement?.contractVersionOrNull(): Int? =
    (this as? JsonPrimitive)
        ?.takeIf { !it.isString && it.content.toIntOrNull() != null }
        ?.content
        ?.toIntOrNull()

internal fun McpApp.validateSkillCompatibility(request: CallToolRequest): CallToolResult {
    val skillVersionValue = request.arguments?.get("skill_version")
        ?: return errorResult("skill_version is required", code = "missing_argument")
    val skillVersion = (skillVersionValue as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
        ?.takeIf(String::isNotBlank)
        ?: return errorResult("skill_version must be a non-empty string", code = "invalid_argument")
    val skillContractValue = request.arguments?.get("skill_contract_version")
        ?: return errorResult("skill_contract_version is required", code = "missing_argument")
    val skillContractVersion = skillContractValue.contractVersionOrNull()
        ?: return errorResult("skill_contract_version must be an integer", code = "invalid_argument")
    if (skillVersion == McpBuildInfo.VERSION && skillContractVersion == McpBuildInfo.MCP_CONTRACT_VERSION) {
        return successResult(
            SkillCompatibilityResult.serializer(),
            SkillCompatibilityResult(
                compatible = true,
                skillVersion = skillVersion,
                skillContractVersion = skillContractVersion,
                serverVersion = McpBuildInfo.VERSION,
                serverContractVersion = McpBuildInfo.MCP_CONTRACT_VERSION,
            ),
        )
    }

    return errorResult(
        message = "DexClub MCP and skill versions do not match. Stop the current analysis and update both from the same release.",
        code = "version_mismatch",
        details = buildJsonObject {
            put("server_version", McpBuildInfo.VERSION)
            put("skill_version", skillVersion)
            put("server_contract_version", McpBuildInfo.MCP_CONTRACT_VERSION)
            put("skill_contract_version", skillContractVersion)
        },
    )
}

internal fun McpApp.acquireToolContextLease(request: CallToolRequest): DexContextLease? {
    val sessionId = request.optionalStringArgument("session_id")
    val workdir = request.optionalStringArgument("workdir")
    val context = runCatching {
        sessionRuntime.resolveExecutionContext(
            sessionId = sessionId,
            workdir = workdir,
        )
    }.getOrNull() ?: return null
    return sessionRuntime.acquireDexContextForExecutionContext(context)
}

internal fun McpApp.executionContextOrFailureResult(request: CallToolRequest): ExecutionContextResolution {
    val sessionId = request.optionalStringArgument("session_id")
    val workdir = request.optionalStringArgument("workdir")
    if (sessionId == null && workdir == null) {
        return ExecutionContextResolution.Failed(missingSessionOrWorkdirResult())
    }
    return try {
        val context = sessionRuntime.resolveExecutionContext(
            sessionId = sessionId,
            workdir = workdir,
        )
        ExecutionContextResolution.Ready(context)
    } catch (_: NoSuchElementException) {
        ExecutionContextResolution.Failed(missingSessionResult(sessionId.orEmpty()))
    } catch (cause: IllegalArgumentException) {
        ExecutionContextResolution.Failed(errorResult(cause.message.orEmpty(), code = "invalid_argument"))
    } catch (cause: Exception) {
        ExecutionContextResolution.Failed(internalErrorResult(cause))
    }
}

internal fun McpApp.missingSessionOrWorkdirResult(): CallToolResult =
    errorResult("session_id or workdir is required", code = "missing_target")

internal fun McpApp.missingSessionResult(sessionId: String): CallToolResult =
    errorResult(staleSessionMessage(sessionId), code = "session_not_found")

internal fun McpApp.missingRequiredArgumentsResult(vararg names: String): CallToolResult =
    errorResult(missingRequiredArgumentsMessage(*names), code = "missing_argument")

internal fun McpApp.missingAnyOfRequiredArgumentsResult(vararg alternatives: String): CallToolResult =
    errorResult("${alternatives.joinToString(" or ")} is required", code = "missing_argument")

internal fun McpApp.missingRequiredArgumentsMessage(vararg names: String): String =
    when (names.size) {
        0 -> "required argument is missing"
        1 -> "${names.first()} is required"
        2 -> "${names[0]} and ${names[1]} are required"
        else -> "${names.dropLast(1).joinToString(", ")}, and ${names.last()} are required"
    }

internal fun McpApp.staleSessionMessage(sessionId: String): String =
    "session_id not found: $sessionId. The MCP process may have restarted, the session may have expired, or the chat may have been restored. Reopen the target with open_target_session, or switch to workdir for stateless calls"

// Keep this internal for tests: error messages may carry raw user input and must be JSON-serialized to avoid string injection.
internal fun McpApp.errorResult(message: String, code: String = "invalid_request", details: JsonObject? = null): CallToolResult =
    CallToolResult(
        content = listOf(TextContent(json.encodeToString(McpErrorEnvelope.serializer(), McpErrorEnvelope(McpErrorDetail(code, message, details))))),
        isError = true,
    )

internal fun McpApp.internalErrorResult(cause: Exception): CallToolResult {
    val message = cause.message?.takeIf(String::isNotBlank) ?: "Unexpected internal error"
    return errorResult(message, code = "internal_error")
}

internal fun McpApp.resourceErrorResult(cause: ResourceDecodeError): CallToolResult =
    errorResult(
        message = cause.message,
        code = cause.reason.name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase(),
        details = buildJsonObject {
            cause.sourcePath?.let { put("sourcePath", it) }
            if (cause.candidates.isNotEmpty()) {
                put("candidates", buildJsonArray {
                    cause.candidates.forEach { candidate ->
                        add(buildJsonObject {
                            candidate.resourceId?.let { put("resourceId", it) }
                            candidate.packageName?.let { put("packageName", it) }
                            candidate.type?.let { put("type", it) }
                            candidate.name?.let { put("name", it) }
                            candidate.sourcePath?.let { put("sourcePath", it) }
                            candidate.sourceEntry?.let { put("sourceEntry", it) }
                        })
                    }
                })
            }
        },
    )

internal fun CallToolRequest.optionalStringArgument(name: String): String? =
    arguments?.get(name)?.jsonPrimitive?.content?.trim()?.ifEmpty { null }

internal fun CallToolRequest.stringArrayArgument(name: String): List<String> =
    (arguments?.get(name) as? JsonArray)
        ?.jsonArray
        ?.map { it.jsonPrimitive.content }
        .orEmpty()

internal fun CallToolRequest.requiredJsonObjectArgument(name: String): JsonObject =
    arguments?.get(name) as? JsonObject ?: throw IllegalArgumentException("$name must be a JSON object")

internal fun CallToolRequest.intArgument(name: String): Int? =
    arguments?.get(name)?.jsonPrimitive?.content?.toIntOrNull()

internal fun CallToolRequest.booleanArgument(name: String): Boolean? =
    arguments?.get(name)?.jsonPrimitive?.content?.toBooleanStrictOrNull()
