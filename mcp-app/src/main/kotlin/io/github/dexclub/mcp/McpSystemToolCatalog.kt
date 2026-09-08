package io.github.dexclub.mcp

internal object McpSystemToolCatalog {
    val tools: List<McpToolMetadata> = listOf(
        McpToolMetadata(
            name = "get_server_info",
            description = "Return the DexClub MCP build and tool contract metadata.",
            requiresVersion = false,
            acquiresContextLease = false,
        ),
        McpToolMetadata(
            name = "validate_skill_compatibility",
            description = "Validate the calling skill build and MCP contract versions before analysis.",
            inputProperties = listOf(
                McpToolInputProperties.string("skill_version"),
                McpToolInputProperties.integer("skill_contract_version"),
            ),
            required = setOf("skill_version", "skill_contract_version"),
            requiresVersion = false,
            acquiresContextLease = false,
        ),
    )

    fun require(name: String): McpToolMetadata = tools.single { it.name == name }
}
