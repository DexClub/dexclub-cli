package io.github.dexclub.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class McpErrorDetail(
    val code: String,
    val message: String,
    val details: JsonObject? = null,
)

@Serializable
internal data class McpErrorEnvelope(
    val error: McpErrorDetail,
)

@Serializable
internal data class ServerInfoResult(
    val version: String,
    @SerialName("mcp_contract_version")
    val mcpContractVersion: Int,
    val commit: String,
    val dirty: Boolean,
)

@Serializable
internal data class SkillCompatibilityResult(
    val compatible: Boolean,
    @SerialName("skill_version") val skillVersion: String,
    @SerialName("skill_contract_version") val skillContractVersion: Int,
    @SerialName("server_version") val serverVersion: String,
    @SerialName("server_contract_version") val serverContractVersion: Int,
)

@Serializable
internal data class OpenTargetSessionResult(
    val sessionId: String,
    val createdAt: String,
    val workspace: WorkspaceContextView,
)

@Serializable
internal data class ListTargetSessionsResult(
    val total: Int,
    val items: List<TargetSessionView>,
)

@Serializable
internal data class CloseTargetSessionResult(
    val closed: Boolean,
    val session: TargetSessionView? = null,
)

@Serializable
internal data class RefreshTargetSessionResult(
    val session: TargetSessionView,
    val handlesCleared: Boolean,
)

@Serializable
internal data class DiagnoseTargetSessionsResult(
    val now: String,
    val idleTimeoutSeconds: Long? = null,
    val maxSessions: Int,
    val maxHandlesPerSession: Int,
    val sessionCount: Int,
    val methodHandleCount: Int,
    val classHandleCount: Int,
    val sessions: List<TargetSessionDiagnosticsView>,
)

@Serializable
internal data class InspectMethodResult(
    val sessionId: String? = null,
    val detail: MethodDetailView,
)

@Serializable
internal data class ManifestDecodeResult(
    val sessionId: String? = null,
    val manifest: ManifestInspectionView,
)

@Serializable
internal data class FindClassesResult(
    val sessionId: String? = null,
    val total: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val items: List<JsonObject>,
)

@Serializable
internal data class ExportTextResult(
    val sessionId: String? = null,
    val descriptor: String,
    val view: String,
    val text: String,
)

@Serializable
internal data class FindFieldsResult(
    val sessionId: String? = null,
    val total: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val items: List<JsonObject>,
)

@Serializable
internal data class FindMethodsResult(
    val sessionId: String? = null,
    val total: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val items: List<JsonObject>,
)

@Serializable
internal data class ResolveResourceResult(
    val sessionId: String? = null,
    val resource: ResourceValueView,
)

@Serializable
internal data class ListResourcesResult(
    val sessionId: String? = null,
    val total: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val items: List<JsonObject>,
)

@Serializable
internal data class FindResourcesResult(
    val sessionId: String? = null,
    val total: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val items: List<JsonObject>,
)

@Serializable
internal data class DecodeXmlResult(
    val sessionId: String? = null,
    val xml: DecodedXmlView,
)
