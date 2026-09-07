package io.github.dexclub.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class McpToolInputProperty(
    val name: String,
    val schema: JsonObject,
    val signature: String,
)

internal data class McpToolMetadata(
    val name: String,
    val description: String,
    val inputProperties: List<McpToolInputProperty> = emptyList(),
    val required: Set<String> = emptySet(),
    val defs: JsonObject? = null,
    val requiresVersion: Boolean = true,
    val acquiresContextLease: Boolean = true,
) {
    init {
        require(inputProperties.none { it.name == VERSION_ARGUMENT }) {
            "$VERSION_ARGUMENT is reserved for the DexClub MCP version gate: $name"
        }
    }

    val effectiveInputProperties: List<McpToolInputProperty>
        get() = if (requiresVersion) listOf(VERSION_INPUT_PROPERTY) + inputProperties else inputProperties

    val effectiveRequired: Set<String>
        get() = if (requiresVersion) required + VERSION_ARGUMENT else required

    fun toToolSchema(): ToolSchema =
        ToolSchema(
            properties = buildJsonObject {
                effectiveInputProperties.forEach { property ->
                    put(property.name, property.schema)
                }
            },
            required = effectiveRequired.toList(),
            defs = defs,
        )

    private companion object {
        const val VERSION_ARGUMENT = "version"
        val VERSION_INPUT_PROPERTY = McpToolInputProperty(
            name = VERSION_ARGUMENT,
            schema = buildJsonObject {
                put("type", "string")
                put("description", "DexClub MCP version embedded in the calling skill.")
            },
            signature = "string",
        )
    }
}

internal object McpToolInputProperties {
    fun string(name: String): McpToolInputProperty =
        McpToolInputProperty(
            name = name,
            schema = stringSchema(),
            signature = "string",
        )

    fun boolean(name: String): McpToolInputProperty =
        McpToolInputProperty(
            name = name,
            schema = booleanSchema(),
            signature = "boolean",
        )

    fun integer(name: String): McpToolInputProperty =
        McpToolInputProperty(
            name = name,
            schema = integerSchema(),
            signature = "integer",
        )

    fun integer(name: String, minimum: Int, maximum: Int? = null): McpToolInputProperty =
        McpToolInputProperty(
            name = name,
            schema = integerSchema(minimum = minimum, maximum = maximum),
            signature = "integer",
        )

    fun jsonObject(name: String, schema: JsonObject): McpToolInputProperty =
        McpToolInputProperty(
            name = name,
            schema = schema,
            signature = "object",
        )

    fun stringArray(name: String): McpToolInputProperty =
        McpToolInputProperty(
            name = name,
            schema = stringArraySchema(),
            signature = "array<string>",
        )

    fun enumString(name: String, values: Set<String>): McpToolInputProperty =
        McpToolInputProperty(
            name = name,
            schema = enumStringSchema(values),
            signature = "string:${values.sorted().joinToString(",")}",
        )

    fun enumStringArray(name: String, values: Set<String>): McpToolInputProperty =
        McpToolInputProperty(
            name = name,
            schema = enumStringArraySchema(values),
            signature = "array<string:${values.sorted().joinToString(",")}>",
        )
}

internal fun contextualInputProperties(vararg properties: McpToolInputProperty): List<McpToolInputProperty> =
    buildList {
        add(McpToolInputProperties.string("session_id"))
        add(McpToolInputProperties.string("workdir"))
        addAll(properties)
    }

internal object McpToolCatalogs {
    val tools: List<McpToolMetadata> =
        McpSystemToolCatalog.tools +
            McpSessionToolCatalog.tools +
            McpDexToolCatalog.tools +
            McpResourceToolCatalog.tools
}

internal fun McpApp.registerCatalogTool(
    server: Server,
    metadata: McpToolMetadata,
    handler: suspend (CallToolRequest) -> CallToolResult,
) {
    server.addLoggedTool(
        name = metadata.name,
        description = metadata.description,
        inputSchema = metadata.toToolSchema(),
        requiresVersion = metadata.requiresVersion,
        acquiresContextLease = metadata.acquiresContextLease,
        handler = handler,
    )
}
