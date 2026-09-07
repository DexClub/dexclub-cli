package io.github.dexclub.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class ToolInputContract(
    val properties: Map<String, String>,
    val required: Set<String> = emptySet(),
)

fun assertMcpToolInputContracts(tools: JsonArray) {
    val actual = tools.associate { element ->
        val tool = element.jsonObject
        val name = tool.getValue("name").jsonPrimitive.content
        val inputSchema = tool.getValue("inputSchema").jsonObject
        name to ToolInputContract(
            properties = inputSchema.getValue("properties").jsonObject
                .mapValues { (_, schema) -> schema.jsonObject.signature() },
            required = (inputSchema["required"] as? JsonArray)
                ?.map { it.jsonPrimitive.content }
                ?.toSet()
                .orEmpty(),
        )
    }

    assertEquals(expectedToolInputContracts, actual)

    val serverInfoContract = actual.getValue("get_server_info")
    assertFalse("version" in serverInfoContract.properties)
    assertFalse("version" in serverInfoContract.required)
    actual.filterKeys { it != "get_server_info" }.forEach { (name, contract) ->
        assertEquals("string", contract.properties["version"], "$name must expose version as a string")
        assertTrue("version" in contract.required, "$name must require version")
    }
}

private fun JsonObject.signature(): String {
    if ("\$ref" in this) return "object"
    val type = getValue("type").jsonPrimitive.content
    if (type != "array") {
        val enumValues = (this["enum"] as? JsonArray)
            ?.map { it.jsonPrimitive.content }
            ?.sorted()
            .orEmpty()
        return if (enumValues.isEmpty()) type else "$type:${enumValues.joinToString(",")}"
    }

    val items = getValue("items").jsonObject
    val itemType = items.getValue("type").jsonPrimitive.content
    val enumValues = (items["enum"] as? JsonArray)
        ?.map { it.jsonPrimitive.content }
        ?.sorted()
        .orEmpty()
    return if (enumValues.isEmpty()) {
        "array<$itemType>"
    } else {
        "array<$itemType:${enumValues.joinToString(",")}>"
    }
}

private fun contract(
    properties: Map<String, String> = emptyMap(),
    required: Set<String> = emptySet(),
): ToolInputContract = ToolInputContract(properties = properties, required = required)

private val expectedToolInputContracts = mapOf(
    *McpToolCatalogs.tools.associate { tool ->
        tool.name to contract(
            properties = tool.effectiveInputProperties.associate { it.name to it.signature },
            required = tool.effectiveRequired,
        )
    }.toList().toTypedArray(),
)
