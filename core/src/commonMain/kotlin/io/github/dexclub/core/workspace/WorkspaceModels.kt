package io.github.dexclub.core.workspace

import kotlinx.serialization.Serializable

@Serializable
enum class WorkspaceInputKind {
    Apk,
    Dex,
    Dexs,
}

@Serializable
data class WorkspaceInputBinding(
    val kind: String = "resolved_entries",
    val resolvedEntries: List<String>,
)

@Serializable
data class WorkspaceInputSnapshot(
    val type: WorkspaceInputKind,
    val binding: WorkspaceInputBinding,
    val fingerprint: String,
)

@Serializable
data class WorkspaceMetadata(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val layoutVersion: Int = CURRENT_LAYOUT_VERSION,
    val workspaceId: String,
    val createdAt: String,
    val updatedAt: String,
    val toolVersion: String,
    val input: WorkspaceInputSnapshot,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val CURRENT_LAYOUT_VERSION = 1
    }
}

@Serializable
data class WorkspaceCapabilities(
    val inspect: Boolean = true,
    val findClass: Boolean = true,
    val findMethod: Boolean = true,
    val findField: Boolean = true,
    val exportDex: Boolean = true,
    val exportSmali: Boolean = true,
    val exportJava: Boolean = true,
    val manifest: Boolean,
    val res: Boolean,
)

@Serializable
data class WorkspaceStatus(
    val metadata: WorkspaceMetadata,
    val capabilities: WorkspaceCapabilities,
    val cachePresent: Boolean,
    val runsPresent: Boolean,
)

@Serializable
data class WorkspaceManifest(
    val manifest: String,
)

@Serializable
data class WorkspaceResourceEntry(
    val path: String,
)

@Serializable
data class WorkspaceResourceListing(
    val resourcesArscPresent: Boolean,
    val entries: List<WorkspaceResourceEntry>,
)
