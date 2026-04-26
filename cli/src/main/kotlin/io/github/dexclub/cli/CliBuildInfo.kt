package io.github.dexclub.cli

internal object CliBuildInfo {
    val version: String by lazy {
        CliBuildInfo::class.java
            .getResourceAsStream("/dexclub-cli-version.txt")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText().trim() }
            .orEmpty()
            .ifBlank { "dev" }
    }
}
