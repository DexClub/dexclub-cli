package io.github.dexclub.core.impl.workspace.model

import io.github.dexclub.core.api.shared.CapabilitySet
import io.github.dexclub.core.api.shared.InputType
import io.github.dexclub.core.api.workspace.TargetHandle
import io.github.dexclub.core.api.workspace.TargetSnapshotSummary
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.api.workspace.WorkspaceRef

internal const val workspaceSchemaVersion: Int = 1
internal const val workspaceLayoutVersion: Int = 1
internal const val targetSchemaVersion: Int = 1
internal const val snapshotSchemaVersion: Int = 1
internal const val classSourceMapSchemaVersion: Int = 2
internal const val manifestCacheSchemaVersion: Int = 1
internal const val resourceTableCacheSchemaVersion: Int = 1
internal const val decodedXmlCacheSchemaVersion: Int = 1
internal const val resourceEntryIndexSchemaVersion: Int = 1
internal const val classSourceMapFormat: String = "class-source-map-v2"
internal const val manifestFormat: String = "xml-text"
internal const val resourceTableFormat: String = "resource-table-v1"
internal const val decodedXmlFormat: String = "xml-text"
internal const val resourceEntryIndexFormat: String = "resource-entry-index-v1"

internal data class WorkspaceRecord(
    val schemaVersion: Int = workspaceSchemaVersion,
    val layoutVersion: Int = workspaceLayoutVersion,
    val workspaceId: String,
    val createdAt: String,
    val updatedAt: String,
    val toolVersion: String,
    val activeTargetId: String,
)

internal data class TargetRecord(
    val schemaVersion: Int = targetSchemaVersion,
    val targetId: String,
    val createdAt: String,
    val updatedAt: String,
    val inputType: InputType,
    val inputPath: String,
) {
    fun toHandle(): TargetHandle =
        TargetHandle(
            targetId = targetId,
            inputType = inputType,
            inputPath = inputPath,
        )
}

internal data class SnapshotRecord(
    val schemaVersion: Int = snapshotSchemaVersion,
    val generatedAt: String,
    val targetId: String,
    val inputPath: String,
    val kind: io.github.dexclub.core.api.shared.WorkspaceKind,
    val inventory: MaterialInventory,
    val inventoryFingerprint: String,
    val contentFingerprint: String,
    val capabilities: CapabilitySet,
) {
    fun toSummary(): TargetSnapshotSummary =
        TargetSnapshotSummary(
            kind = kind,
            inventoryFingerprint = inventoryFingerprint,
            contentFingerprint = contentFingerprint,
            capabilities = capabilities,
            inventoryCounts = inventory.counts(),
        )
}

internal data class PreparedWorkspace(
    val ref: WorkspaceRef,
    val workspace: WorkspaceRecord,
    val target: TargetRecord,
    val snapshot: SnapshotRecord,
)

internal data class ClassSourceRefRecord(
    val id: Int,
    val sourcePath: String,
    val sourceEntry: String? = null,
)

internal data class ClassSourceMapRecord(
    val schemaVersion: Int = classSourceMapSchemaVersion,
    val generatedAt: String,
    val targetId: String,
    val toolVersion: String,
    val contentFingerprint: String,
    val format: String = classSourceMapFormat,
    val sources: List<ClassSourceRefRecord>,
    val mappings: Map<String, Int>,
) {
    fun sourceOf(classSignature: String): ClassSourceRefRecord? {
        val sourceId = mappings[classSignature] ?: return null
        return sources.firstOrNull { it.id == sourceId }
    }
}

internal data class ManifestCacheRecord(
    val schemaVersion: Int = manifestCacheSchemaVersion,
    val generatedAt: String,
    val targetId: String,
    val toolVersion: String,
    val sourcePath: String,
    val sourceEntry: String? = null,
    val sourceFingerprint: String,
    val format: String = manifestFormat,
    val text: String,
)

internal data class ResourceTableCacheRecord(
    val schemaVersion: Int = resourceTableCacheSchemaVersion,
    val generatedAt: String,
    val targetId: String,
    val toolVersion: String,
    val sourcePath: String,
    val sourceEntry: String? = null,
    val sourceFingerprint: String,
    val format: String = resourceTableFormat,
    val payload: ResourceTablePayloadRecord,
)

internal data class ResourceTablePayloadRecord(
    val packages: List<String>,
    val typeCount: Int,
    val entries: List<io.github.dexclub.core.api.resource.ResourceEntry>,
    val values: List<ResourceTableValueRecord> = emptyList(),
)

internal data class ResourceTableValueRecord(
    val resourceId: String? = null,
    val type: String? = null,
    val name: String? = null,
    val value: String? = null,
)

internal data class DecodedXmlCacheRecord(
    val schemaVersion: Int = decodedXmlCacheSchemaVersion,
    val generatedAt: String,
    val targetId: String,
    val toolVersion: String,
    val sourcePath: String,
    val sourceEntry: String? = null,
    val sourceFingerprint: String,
    val format: String = decodedXmlFormat,
    val text: String,
)

internal data class ResourceEntryIndexRecord(
    val schemaVersion: Int = resourceEntryIndexSchemaVersion,
    val generatedAt: String,
    val targetId: String,
    val toolVersion: String,
    val contentFingerprint: String,
    val format: String = resourceEntryIndexFormat,
    val entries: List<io.github.dexclub.core.api.resource.ResourceEntry>,
)

internal fun WorkspaceRecord.toContext(
    workdir: String,
    dexclubDir: String,
    target: TargetRecord,
    snapshot: SnapshotRecord,
): WorkspaceContext =
    WorkspaceContext(
        workdir = workdir,
        dexclubDir = dexclubDir,
        workspaceId = workspaceId,
        activeTargetId = activeTargetId,
        activeTarget = target.toHandle(),
        snapshot = snapshot.toSummary(),
    )
