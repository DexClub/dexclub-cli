package io.github.dexclub.cli

import io.github.dexclub.core.api.workspace.WorkspaceRef

internal class WorkdirResolver(
    private val cwdProvider: () -> String,
) {
    fun resolve(workdir: String?): WorkspaceRef =
        WorkspaceRef(
            workdir = workdir?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: cwdProvider(),
        )
}
