package io.github.dexclub.mcp

import io.github.dexclub.core.app.createSessionAppRuntime
import io.github.dexclub.core.app.session.TargetSessionService
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

fun main() {
    configureSlf4jSimpleDefaults()
    disableKotlinLoggingStartupMessage()
    DexKitMcpBootstrap.configureNativeLibraryDir()

    val config = loadHttpServerConfig()
    McpRuntimeDiagnostics.install(config)
    nonLoopbackHostWarning(config.host)?.let(McpRuntimeDiagnostics::startupConsole)
    val app = McpApp(
        runtime = createSessionAppRuntime(
            sessionService = TargetSessionService(
                idleTimeout = config.sessionIdleTimeout,
                maxSessions = config.maxSessions,
                maxHandlesPerSession = config.maxHandlesPerSession,
            ),
        ),
        queryFilePolicy = McpQueryFilePolicy.fromConfig(config),
    )
    val server = app.createServer()

    McpRuntimeDiagnostics.startupConsole(
        "DexClub MCP listening on http://${config.host}:${config.port}${config.path} " +
                "(stateless streamable HTTP, trace=${config.traceEnabled})",
    )

    try {
        createHttpServer(config, server)
            .start(wait = true)
    } finally {
        app.close()
    }
}

data class HttpServerConfig(
    val host: String,
    val port: Int,
    val path: String,
    val traceEnabled: Boolean,
    val stderrEnabled: Boolean = false,
    val traceLogFile: Path?,
    val runtimeFilesDir: Path = Paths.get("").toAbsolutePath().normalize(),
    val sessionIdleTimeout: Duration = Duration.ofMinutes(10),
    val maxSessions: Int = 5,
    val maxHandlesPerSession: Int = 1_000,
    val maxTraceArchives: Int = 10,
    val queryRoots: List<Path> = emptyList(),
)

private object McpEnv {
    const val HOST = "DEXCLUB_MCP_HOST"
    const val PORT = "DEXCLUB_MCP_PORT"
    const val PATH = "DEXCLUB_MCP_PATH"
    const val TRACE = "DEXCLUB_MCP_TRACE"
    const val STDERR = "DEXCLUB_MCP_STDERR"
    const val SESSION_IDLE_TIMEOUT_MINUTES = "DEXCLUB_MCP_SESSION_IDLE_TIMEOUT_MINUTES"
    const val MAX_SESSIONS = "DEXCLUB_MCP_MAX_SESSIONS"
    const val MAX_HANDLES_PER_SESSION = "DEXCLUB_MCP_MAX_HANDLES_PER_SESSION"
    const val MAX_TRACE_ARCHIVES = "DEXCLUB_MCP_MAX_TRACE_ARCHIVES"
    const val QUERY_ROOTS = "DEXCLUB_MCP_QUERY_ROOTS"

    const val DEFAULT_SESSION_IDLE_TIMEOUT_MINUTES = 10L
    const val DEFAULT_MAX_SESSIONS = 5
    const val DEFAULT_MAX_HANDLES_PER_SESSION = 1_000
    const val DEFAULT_MAX_TRACE_ARCHIVES = 10
}

internal fun loadHttpServerConfig(): HttpServerConfig {
    val runtimeFilesDir = resolveRuntimeFilesDir()
    val traceEnabled = System.getenv(McpEnv.TRACE)
        ?.trim()
        ?.equals("false", ignoreCase = true)
        ?.not()
        ?: true
    return HttpServerConfig(
        host = System.getenv(McpEnv.HOST)?.trim()?.ifEmpty { null } ?: "127.0.0.1",
        port = System.getenv(McpEnv.PORT)?.trim()?.toIntOrNull() ?: 8787,
        path = normalizePath(System.getenv(McpEnv.PATH)),
        traceEnabled = traceEnabled,
        stderrEnabled = readBooleanEnv(McpEnv.STDERR, defaultValue = false),
        traceLogFile = if (traceEnabled) runtimeFilesDir.resolve(Paths.get("logs", "mcp.log")) else null,
        runtimeFilesDir = runtimeFilesDir,
        sessionIdleTimeout = Duration.ofMinutes(
            readPositiveLongEnv(
                name = McpEnv.SESSION_IDLE_TIMEOUT_MINUTES,
                defaultValue = McpEnv.DEFAULT_SESSION_IDLE_TIMEOUT_MINUTES,
            ),
        ),
        maxSessions = readPositiveIntEnv(
            name = McpEnv.MAX_SESSIONS,
            defaultValue = McpEnv.DEFAULT_MAX_SESSIONS,
        ),
        maxHandlesPerSession = readPositiveIntEnv(
            name = McpEnv.MAX_HANDLES_PER_SESSION,
            defaultValue = McpEnv.DEFAULT_MAX_HANDLES_PER_SESSION,
        ),
        maxTraceArchives = readPositiveIntEnv(
            name = McpEnv.MAX_TRACE_ARCHIVES,
            defaultValue = McpEnv.DEFAULT_MAX_TRACE_ARCHIVES,
        ),
        queryRoots = System.getenv(McpEnv.QUERY_ROOTS)
            ?.split(java.io.File.pathSeparator)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.mapNotNull { raw -> runCatching { Paths.get(raw) }.getOrNull() }
            .orEmpty(),
    )
}

private fun readPositiveLongEnv(name: String, defaultValue: Long): Long =
    System.getenv(name)
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?: defaultValue

private fun readPositiveIntEnv(name: String, defaultValue: Int): Int =
    System.getenv(name)
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: defaultValue

private fun readBooleanEnv(name: String, defaultValue: Boolean): Boolean =
    System.getenv(name)
        ?.trim()
        ?.lowercase()
        ?.let { value ->
            when (value) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        ?: defaultValue

private fun normalizePath(rawPath: String?): String {
    val path = rawPath?.trim()?.ifEmpty { null } ?: "/mcp"
    return if (path.startsWith('/')) path else "/$path"
}

internal fun nonLoopbackHostWarning(host: String): String? =
    if (isLoopbackHost(host)) {
        null
    } else {
        "WARNING: DexClub MCP is listening on non-loopback host '$host'. Only use this on a trusted network."
    }

internal fun isLoopbackHost(host: String): Boolean {
    val normalized = host.trim().lowercase().removePrefix("[").removeSuffix("]")
    if (normalized == "localhost" || normalized == "::1" || normalized == "0:0:0:0:0:0:0:1") {
        return true
    }
    val ipv4Parts = normalized.split('.')
    return ipv4Parts.size == 4 &&
            ipv4Parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true } &&
            ipv4Parts.first() == "127"
}

private const val MCP_RUNTIME_FILES_DIR_PROPERTY = "dexclub.mcp.runtime.files.dir"

internal fun resolveRuntimeFilesDir(
    configuredDirRaw: String? = System.getProperty(MCP_RUNTIME_FILES_DIR_PROPERTY),
    fallbackWorkingDir: Path = Paths.get("").toAbsolutePath().normalize(),
): Path =
    configuredDirRaw
        ?.trim()
        ?.ifEmpty { null }
        ?.let { raw ->
            runCatching {
                Paths.get(raw)
                    .toAbsolutePath()
                    .normalize()
            }.getOrNull()
        }
        ?: fallbackWorkingDir

private fun configureSlf4jSimpleDefaults() {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error")
}

private fun disableKotlinLoggingStartupMessage() {
    runCatching {
        val configClass = Class.forName("io.github.oshai.kotlinlogging.KotlinLoggingConfiguration")
        val instance = configClass.getField("INSTANCE").get(null)
        configClass.getMethod("setLogStartupMessage", Boolean::class.javaPrimitiveType)
            .invoke(instance, false)
    }
}
