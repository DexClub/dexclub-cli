package io.github.dexclub.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server

internal fun McpApp.registerSystemTools(server: Server) {
    registerCatalogTool(server, McpSystemToolCatalog.require("get_server_info")) {
        successResult(
            ServerInfoResult.serializer(),
            ServerInfoResult(
                version = McpBuildInfo.VERSION,
                mcpContractVersion = McpBuildInfo.MCP_CONTRACT_VERSION,
                commit = McpBuildInfo.COMMIT,
                dirty = McpBuildInfo.DIRTY,
            ),
        )
    }
}
