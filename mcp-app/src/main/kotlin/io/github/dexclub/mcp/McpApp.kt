package io.github.dexclub.mcp

import io.github.dexclub.core.app.contract.Services
import io.github.dexclub.core.app.SessionAppRuntime
import io.github.dexclub.core.app.createSessionAppRuntime
import io.github.dexclub.core.app.session.SessionStoreSnapshot
import io.github.dexclub.core.app.session.TargetSession
import io.github.dexclub.core.app.session.TargetSessionService
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.serialization.json.Json

class McpApp(
    internal val runtime: SessionAppRuntime = createSessionAppRuntime(),
    internal val queryFilePolicy: McpQueryFilePolicy = McpQueryFilePolicy(loopback = true, allowedRoots = emptyList()),
) {
    constructor(
        services: Services,
        sessionStore: TargetSessionService = TargetSessionService(),
        queryFilePolicy: McpQueryFilePolicy = McpQueryFilePolicy(loopback = true, allowedRoots = emptyList()),
    ) : this(
        runtime = createSessionAppRuntime(
            services = services,
            sessionService = sessionStore,
        ),
        queryFilePolicy = queryFilePolicy,
    )

    internal val services: Services
        get() = runtime.services

    internal val sessionStore: TargetSessionService
        get() = runtime.sessionService

    internal val appUseCases = runtime.appUseCases

    internal val sessionRuntime = runtime.sessionRuntime

    internal val json = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
    }

    fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "dexclub-mcp",
                version = McpBuildInfo.VERSION,
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        )
        registerSystemTools(server)
        registerSessionTools(server)
        registerDexTools(server)
        registerResourceTools(server)

        return server
    }

    internal fun Server.addLoggedTool(
        name: String,
        description: String,
        inputSchema: io.modelcontextprotocol.kotlin.sdk.types.ToolSchema,
        requiresVersion: Boolean,
        acquiresContextLease: Boolean,
        handler: suspend (CallToolRequest) -> CallToolResult,
    ) {
        addTool(
            name = name,
            description = description,
            inputSchema = inputSchema,
        ) { request ->
            val versionFailure = if (requiresVersion) validateToolVersion(request) else null
            val summary = if (versionFailure == null && acquiresContextLease) {
                summarizeToolArguments(request.arguments)
            } else {
                ""
            }
            McpRuntimeDiagnostics.toolStarted(name, summary)
            if (versionFailure != null) {
                McpRuntimeDiagnostics.toolFinished(name, isError = true)
                return@addTool versionFailure
            }
            val contextLease = if (acquiresContextLease) acquireToolContextLease(request) else null
            try {
                handler(request).also { result ->
                    McpRuntimeDiagnostics.toolFinished(name, result.isError == true)
                }
            } catch (cause: Throwable) {
                McpRuntimeDiagnostics.toolFailed(name, cause)
                throw cause
            } finally {
                contextLease?.close()
            }
        }
    }

    internal fun <T> Server.addPreflightedLoggedTool(
        name: String,
        description: String,
        inputSchema: io.modelcontextprotocol.kotlin.sdk.types.ToolSchema,
        requiresVersion: Boolean,
        preflight: (CallToolRequest) -> T,
        handler: suspend (CallToolRequest, T) -> CallToolResult,
    ) {
        addTool(
            name = name,
            description = description,
            inputSchema = inputSchema,
        ) { request ->
            val versionFailure = if (requiresVersion) validateToolVersion(request) else null
            if (versionFailure != null) {
                McpRuntimeDiagnostics.toolStarted(name, "")
                McpRuntimeDiagnostics.toolFinished(name, isError = true)
                return@addTool versionFailure
            }
            val prepared = try {
                preflight(request)
            } catch (cause: McpQueryFileException) {
                McpRuntimeDiagnostics.toolStarted(name, "")
                McpRuntimeDiagnostics.toolFinished(name, isError = true)
                return@addTool errorResult(cause.message, code = cause.code, details = cause.details)
            } catch (cause: IllegalArgumentException) {
                McpRuntimeDiagnostics.toolStarted(name, "")
                McpRuntimeDiagnostics.toolFinished(name, isError = true)
                return@addTool errorResult(cause.message.orEmpty(), code = "invalid_argument")
            }
            val summary = summarizeToolArguments(request.arguments)
            McpRuntimeDiagnostics.toolStarted(name, summary)
            val contextLease = acquireToolContextLease(request)
            try {
                handler(request, prepared).also { result ->
                    McpRuntimeDiagnostics.toolFinished(name, result.isError == true)
                }
            } catch (cause: Throwable) {
                McpRuntimeDiagnostics.toolFailed(name, cause)
                throw cause
            } finally {
                contextLease?.close()
            }
        }
    }

    fun close() {
        runtime.close()
    }

    internal fun openTargetSession(input: String): TargetSession =
        sessionRuntime.openTargetSession(input)

    internal fun listTargetSessions(): List<TargetSession> =
        sessionRuntime.listTargetSessions()

    internal fun getTargetSession(sessionId: String): TargetSession? =
        sessionRuntime.getTargetSession(sessionId)

    internal fun closeTargetSession(sessionId: String): TargetSession? =
        sessionRuntime.closeTargetSession(sessionId)

    internal fun refreshTargetSession(sessionId: String): TargetSession? =
        sessionRuntime.refreshTargetSession(sessionId)

    internal fun diagnoseTargetSessions(): SessionStoreSnapshot =
        sessionRuntime.snapshot()
}

