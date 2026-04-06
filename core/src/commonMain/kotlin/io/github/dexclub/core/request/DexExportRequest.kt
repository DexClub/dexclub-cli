package io.github.dexclub.core.request

data class DexExportRequest(
    val className: String,
    val sourceDexPath: String,
    val outputPath: String,
)
