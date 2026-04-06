package io.github.dexclub.core.request

import io.github.dexclub.core.config.SmaliRenderConfig

data class SmaliExportRequest(
    val className: String,
    val sourceDexPath: String,
    val outputPath: String,
    val config: SmaliRenderConfig? = null,
)
