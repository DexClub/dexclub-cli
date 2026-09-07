package io.github.dexclub.mcp

internal object McpSystemToolCatalog {
    val tools: List<McpToolMetadata> = listOf(
        McpToolMetadata(
            name = "get_server_info",
            description = "Return the DexClub MCP build and tool contract metadata.",
            requiresVersion = false,
            acquiresContextLease = false,
        ),
    )

    fun require(name: String): McpToolMetadata = tools.single { it.name == name }
}
