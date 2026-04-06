package io.github.dexclub.core.model

data class DexExportResult(
    val outputPath: String,
    val format: DexExportFormat,
    val className: String,
)
