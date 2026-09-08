package io.github.dexclub.mcp

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpFindQuerySchemaTest {
    @Test
    fun catalogExposesOnlyUnifiedFindTools() {
        val findNames = McpDexToolCatalog.tools.map { it.name }.filter { it.startsWith("find_") }

        assertEquals(listOf("find_classes", "find_methods", "find_fields"), findNames)
    }

    @Test
    fun findQuerySchemasUseAbsoluteQueryFile() {
        listOf("find_classes", "find_methods", "find_fields").forEach { toolName ->
            val metadata = McpDexToolCatalog.require(toolName)
            val schema = metadata.toToolSchema()

            assertEquals(setOf("query_file"), metadata.required)
            val queryFileSchema = schema.properties!!.getValue("query_file").jsonObject
            assertEquals("string", queryFileSchema.getValue("type").jsonPrimitive.content)
            assertTrue(queryFileSchema.getValue("description").jsonPrimitive.content.contains("absolute", ignoreCase = true))
            assertFalse("query" in schema.properties!!)
            assertFalse("query_json" in schema.properties!!)
            assertTrue(schema.defs == null)
        }
    }
}
