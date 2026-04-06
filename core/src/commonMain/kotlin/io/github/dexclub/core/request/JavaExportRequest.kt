package io.github.dexclub.core.request

import io.github.dexclub.core.config.JavaDecompileConfig

data class JavaExportRequest(
    val className: String,
    val sourceDexPath: String,
    val outputPath: String,
    val config: JavaDecompileConfig? = null,
)
