package io.github.dexclub.mcp

import io.github.dexclub.core.api.shared.Services
import io.github.dexclub.core.app.session.TargetSessionService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.EmbeddedServer
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class McpHttpSmokeTest {
    private val json = Json { ignoreUnknownKeys = true }
    private var engine: EmbeddedServer<*, *>? = null

    @AfterTest
    fun tearDown() {
        engine?.stop(0, 0)
        engine = null
    }

    @Test
    fun httpServerSupportsInitializeToolsListAndOpenTargetSession() = runBlocking {
        smoke(traceEnabled = false)
    }

    @Test
    fun httpServerSupportsInitializeToolsListAndOpenTargetSessionWithTraceEnabled() = runBlocking {
        smoke(traceEnabled = true)
    }

    @Test
    fun traceLogArchivesPreviousProcessLogOnInstall() {
        val traceDir = createTempDirectory("mcp-trace-archive-test")
        val traceLog = traceDir.resolve("mcp.log")
        traceLog.writeText("previous process log\n")

        McpRuntimeDiagnostics.install(
            HttpServerConfig(
                host = "127.0.0.1",
                port = 0,
                path = "/mcp",
                traceEnabled = true,
                traceLogFile = traceLog,
            ),
        )

        val archived = traceDir.resolve("archive").listDirectoryEntries("mcp_*.log")
        assertEquals(1, archived.size)
        assertEquals("previous process log\n", archived.single().readText())
        val currentLog = traceLog.readText()
        assertTrue(currentLog.contains("PROCESS START: DexClub MCP process started"))
        assertTrue(currentLog.contains("version=${McpBuildInfo.VERSION}"))
        assertTrue(currentLog.contains("contractVersion=${McpBuildInfo.MCP_CONTRACT_VERSION}"))
        assertTrue(currentLog.contains("commit=${McpBuildInfo.COMMIT.take(12)}"))
        assertTrue(currentLog.contains("dirty=${McpBuildInfo.DIRTY}"))
        assertTrue(!currentLog.contains("previous process log"))
    }

    @Test
    fun traceLogArchiveRetentionDeletesOldestFiles() {
        val traceDir = createTempDirectory("mcp-trace-retention-test")
        val traceLog = traceDir.resolve("mcp.log")
        val archiveDir = traceDir.resolve("archive").also { it.createDirectories() }
        repeat(3) { index ->
            val archive = archiveDir.resolve("mcp_20260522_20415$index.log")
            archive.writeText("archive-$index")
            Files.setLastModifiedTime(archive, FileTime.fromMillis(index.toLong()))
        }
        traceLog.writeText("previous process log\n")

        McpRuntimeDiagnostics.install(
            HttpServerConfig(
                host = "127.0.0.1",
                port = 0,
                path = "/mcp",
                traceEnabled = true,
                traceLogFile = traceLog,
                maxTraceArchives = 2,
            ),
        )

        assertEquals(2, archiveDir.listDirectoryEntries("mcp_*.log").size)
    }

    private suspend fun smoke(traceEnabled: Boolean) {
        val workspace = fakeWorkspaceContext()
        val workspaceService = FakeWorkspaceService(workspace)
        val dexService = FakeDexAnalysisService()
        val app = McpApp(
            services = Services(
                workspace = workspaceService,
                dex = dexService,
                resource = FakeResourceService(),
            ),
            sessionStore = TargetSessionService(),
        )
        val port = allocateTestPort()
        val server = app.createServer()
        engine = createHttpServer(
            config = HttpServerConfig(
                host = "127.0.0.1",
                port = port,
                path = "/mcp",
                traceEnabled = traceEnabled,
                traceLogFile = if (traceEnabled) createTempDirectory("mcp-trace-test").resolve("mcp.log") else null,
            ),
            server = server,
        ).also { it.start(wait = false) }

        val baseUrl = "http://127.0.0.1:$port/mcp"
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        client.use {
            val initializeResponse = it.post(baseUrl) {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                headers.append("Mcp-Protocol-Version", "2025-11-25")
                setBody(
                    """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"smoke-test","version":"dev"}}}
                    """.trimIndent(),
                )
            }
            assertEquals(HttpStatusCode.OK, initializeResponse.status)
            assertEquals(ContentType.Application.Json.toString(), initializeResponse.headers[HttpHeaders.ContentType])
            val initializeBody = json.parseToJsonElement(initializeResponse.bodyAsText()).jsonObject
            assertEquals("2.0", initializeBody["jsonrpc"]!!.jsonPrimitive.content)
            assertEquals("dexclub-mcp", initializeBody["result"]!!.jsonObject["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals(McpBuildInfo.VERSION, initializeBody["result"]!!.jsonObject["serverInfo"]!!.jsonObject["version"]!!.jsonPrimitive.content)

            val listToolsResponse = it.post(baseUrl) {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                headers.append("Mcp-Protocol-Version", "2025-11-25")
                setBody("""{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""")
            }
            assertEquals(HttpStatusCode.OK, listToolsResponse.status)
            val listToolsBody = json.parseToJsonElement(listToolsResponse.bodyAsText()).jsonObject
            val tools = listToolsBody["result"]!!
                .jsonObject["tools"]!!
                .jsonArray
            val toolNames = tools
                .map { tool -> tool.jsonObject["name"]!!.jsonPrimitive.content }
                .toSet()
            assertEquals(readSkillToolNames(), toolNames)
            assertTrue("find_classes_using_strings" !in toolNames)
            assertTrue("find_methods_using_strings" !in toolNames)
            assertMcpToolInputContracts(tools)

            // System metadata must not inspect target-shaped arguments.
            val serverInfoResponse = it.post(baseUrl) {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                headers.append("Mcp-Protocol-Version", "2025-11-25")
                setBody("""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"get_server_info","arguments":{"session_id":{}}}}""")
            }
            assertEquals(HttpStatusCode.OK, serverInfoResponse.status)
            val serverInfoText = json.parseToJsonElement(serverInfoResponse.bodyAsText()).jsonObject["result"]!!
                .jsonObject["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
            val serverInfo = json.parseToJsonElement(serverInfoText).jsonObject
            assertEquals(McpBuildInfo.VERSION, serverInfo["version"]!!.jsonPrimitive.content)
            assertEquals(McpBuildInfo.MCP_CONTRACT_VERSION, serverInfo["mcp_contract_version"]!!.jsonPrimitive.content.toInt())
            assertEquals(McpBuildInfo.COMMIT, serverInfo["commit"]!!.jsonPrimitive.content)
            assertEquals(McpBuildInfo.DIRTY, serverInfo["dirty"]!!.jsonPrimitive.content.toBooleanStrict())

            val mismatchedResponse = it.post(baseUrl) {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                headers.append("Mcp-Protocol-Version", "2025-11-25")
                setBody(
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 8)
                        put("method", "tools/call")
                        put("params", buildJsonObject {
                            put("name", "find_methods")
                            put("arguments", buildJsonObject {
                                put("version", "wrong")
                                put("workdir", workspace.workdir)
                            })
                        })
                    }.toString(),
                )
            }
            assertEquals(HttpStatusCode.OK, mismatchedResponse.status)
            assertTrue(mismatchedResponse.bodyAsText().contains("version_mismatch"))
            assertNull(workspaceService.openedRef)
            assertNull(dexService.lastFindMethodsRequest)

            val openSessionResponse = it.post(baseUrl) {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                headers.append("Mcp-Protocol-Version", "2025-11-25")
                setBody(
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 3)
                        put("method", "tools/call")
                        put(
                            "params",
                            buildJsonObject {
                                put("name", "open_target_session")
                                put(
                                    "arguments",
                                    buildJsonObject {
                                        put("version", McpBuildInfo.VERSION)
                                        put("input", "sample.apk")
                                    },
                                )
                            },
                        )
                    }.toString(),
                )
            }
            assertEquals(HttpStatusCode.OK, openSessionResponse.status)
            val openSessionBody = json.parseToJsonElement(openSessionResponse.bodyAsText()).jsonObject
            val openSessionText = openSessionBody["result"]!!
                .jsonObject["content"]!!
                .jsonArray
                .single()
                .jsonObject["text"]!!
                .jsonPrimitive.content
            val openSessionPayload = json.parseToJsonElement(openSessionText).jsonObject
            val sessionId = openSessionPayload["sessionId"]!!.jsonPrimitive.content
            assertTrue(openSessionText.contains("workspaceId"))
            assertTrue(openSessionText.contains("sample.apk"))

            val getSessionResponse = it.post(baseUrl) {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                headers.append("Mcp-Protocol-Version", "2025-11-25")
                setBody(
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 4)
                        put("method", "tools/call")
                        put(
                            "params",
                            buildJsonObject {
                                put("name", "get_target_session")
                                put("arguments", buildJsonObject {
                                    put("version", McpBuildInfo.VERSION)
                                    put("session_id", sessionId)
                                })
                            },
                        )
                    }.toString(),
                )
            }
            assertEquals(HttpStatusCode.OK, getSessionResponse.status)
            assertTrue(getSessionResponse.bodyAsText().contains(sessionId))

            val closeSessionResponse = it.post(baseUrl) {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                headers.append("Mcp-Protocol-Version", "2025-11-25")
                setBody(
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 5)
                        put("method", "tools/call")
                        put(
                            "params",
                            buildJsonObject {
                                put("name", "close_target_session")
                                put("arguments", buildJsonObject {
                                    put("version", McpBuildInfo.VERSION)
                                    put("session_id", sessionId)
                                })
                            },
                        )
                    }.toString(),
                )
            }
            assertEquals(HttpStatusCode.OK, closeSessionResponse.status)
            val closeSessionBody = json.parseToJsonElement(closeSessionResponse.bodyAsText()).jsonObject
            val closeSessionText = closeSessionBody["result"]!!
                .jsonObject["content"]!!
                .jsonArray
                .single()
                .jsonObject["text"]!!
                .jsonPrimitive.content
            assertTrue(closeSessionText.contains("\"closed\":true"))

            val missingSessionResponse = it.post(baseUrl) {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                accept(ContentType.Application.Json)
                headers.append("Mcp-Protocol-Version", "2025-11-25")
                setBody(
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 6)
                        put("method", "tools/call")
                        put(
                            "params",
                            buildJsonObject {
                                put("name", "get_target_session")
                                put("arguments", buildJsonObject {
                                    put("version", McpBuildInfo.VERSION)
                                    put("session_id", sessionId)
                                })
                            },
                        )
                    }.toString(),
                )
            }
            assertEquals(HttpStatusCode.OK, missingSessionResponse.status)
            assertTrue(missingSessionResponse.bodyAsText().contains("session_id not found: $sessionId"))
        }
    }

    private fun allocateTestPort(): Int =
        ServerSocket(0).use { it.localPort }

    private fun readSkillToolNames(): Set<String> {
        val repoRoot = Paths.get(checkNotNull(System.getProperty("dexclub.repo.root")))
        val skill = repoRoot.resolve("skills/dexclub-analysis/SKILL.md").readText()
        val toolSurface = skill.substringAfter("## Useful MCP Surface")
        return Regex("(?m)^- `([a-z_]+)`$")
            .findAll(toolSurface)
            .map { it.groupValues[1] }
            .toSet()
    }
}
