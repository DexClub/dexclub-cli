package io.github.dexclub.mcp

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put

class McpQueryFileLoaderTest {
    @Test
    fun rejectsInvalidJsonSeparatelyFromInvalidEnvelope() {
        assertEquals("invalid_query_json", load("{").code)
        assertEquals("invalid_query_document", load("{}").code)
    }

    @Test
    fun rejectsUnknownEnvelopeFields() {
        val error = load(document(extra = ",\"extra\":true"))
        assertEquals("invalid_query_document", error.code)
    }

    @Test
    fun rejectsLegacyQueryJsonAndKindMismatch() {
        val legacy = callToolRequest("find_methods", buildJsonObject { put("query_json", "{}") })
        assertEquals("query_contract_removed", assertFailsWith<McpQueryFileException> {
            createTestApp().loadQueryFile(legacy, "find_methods")
        }.code)

        val file = createTempFile()
        file.writeText(document().replace("\"find_methods\"", "\"find_fields\""))
        assertEquals("query_kind_mismatch", assertFailsWith<McpQueryFileException> {
            createTestApp().loadQueryFile(request(file), "find_methods")
        }.code)
    }

    @Test
    fun acceptsUtf8BomAndDoesNotDoubleDecodeStringValues() {
        val file = createTempFile()
        file.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + document().toByteArray())
        val query = createTestApp().loadQueryFile(request(file), "find_methods")
        assertEquals("{\"event\":\"login\"}", query["matcher"]!!.jsonObject["usingStrings"]!!.jsonArray.single().jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun rejectsFilesOverOneMiB() {
        val file = createTempFile()
        file.writeBytes(ByteArray(MCP_QUERY_FILE_MAX_BYTES + 1) { 'x'.code.toByte() })
        val error = assertFailsWith<McpQueryFileException> {
            createTestApp().loadQueryFile(request(file), "find_methods")
        }
        assertEquals("query_file_too_large", error.code)
        assertEquals(MCP_QUERY_FILE_MAX_BYTES, error.details!!["max_bytes"]!!.jsonPrimitive.int)
        assertEquals(MCP_QUERY_FILE_MAX_BYTES + 1, error.details["read_bytes"]!!.jsonPrimitive.int)
    }

    @Test
    fun rejectsRelativePathsAndInvalidUtf8() {
        val relative = callToolRequest("find_methods", buildJsonObject { put("query_file", "query.json") })
        assertEquals("invalid_query_path", assertFailsWith<McpQueryFileException> {
            createTestApp().loadQueryFile(relative, "find_methods")
        }.code)

        val file = createTempFile()
        file.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        assertEquals("invalid_query_encoding", assertFailsWith<McpQueryFileException> {
            createTestApp().loadQueryFile(request(file), "find_methods")
        }.code)
    }

    @Test
    fun nonLoopbackRequiresConfiguredRealPathRoot() {
        val root = createTempDirectory()
        val file = root.resolve("query.json")
        file.writeText(document())
        val policy = McpQueryFilePolicy(loopback = false, allowedRoots = listOf(root))
        val query = createTestApp(queryFilePolicy = policy).loadQueryFile(request(file), "find_methods")
        assertEquals("{\"event\":\"login\"}", query["matcher"]!!.jsonObject["usingStrings"]!!.jsonArray.single().jsonObject["value"]!!.jsonPrimitive.content)

        val outside = createTempFile()
        outside.writeText(document())
        assertEquals("query_path_not_allowed", assertFailsWith<McpQueryFileException> {
            createTestApp(queryFilePolicy = policy).loadQueryFile(request(outside), "find_methods")
        }.code)
    }

    private fun request(file: java.nio.file.Path) =
        callToolRequest("find_methods", buildJsonObject {
            put("query_file", file.toAbsolutePath().toString())
        })

    private fun load(text: String): McpQueryFileException {
        val file = createTempFile()
        file.writeText(text)
        return assertFailsWith { createTestApp().loadQueryFile(request(file), "find_methods") }
    }

    private fun document(extra: String = "") =
        """{"format":"dexclub-query","formatVersion":1,"kind":"find_methods","query":{"matcher":{"usingStrings":[{"value":"{\"event\":\"login\"}"}]}}$extra}"""
}
