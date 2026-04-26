package io.github.dexclub.core.impl.shared

internal object CoreBuildInfo {
    val version: String by lazy {
        CoreBuildInfo::class.java
            .getResourceAsStream("/dexclub-core-version.txt")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText().trim() }
            .orEmpty()
            .ifBlank { "dev" }
    }
}
