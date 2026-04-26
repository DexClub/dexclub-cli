package io.github.dexclub.cli

internal data class CommandResult(
    val payload: RenderPayload,
    val outputFormat: OutputFormat,
    val exitCode: Int,
)
