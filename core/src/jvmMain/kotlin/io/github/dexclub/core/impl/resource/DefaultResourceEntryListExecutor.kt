package io.github.dexclub.core.impl.resource

import com.reandroid.apk.ApkModule
import com.reandroid.apk.ResFile
import io.github.dexclub.core.api.resource.ResourceEntry
import io.github.dexclub.core.api.resource.ResourceResolution
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.core.impl.workspace.model.ResourceEntryIndexRecord
import io.github.dexclub.core.impl.workspace.store.WorkspaceStore
import java.nio.file.Path

internal class DefaultResourceEntryListExecutor(
    private val store: WorkspaceStore,
    private val toolVersion: String,
) : ResourceEntryListExecutor {
    override fun listResourceEntries(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
    ): List<ResourceEntry> {
        store.loadResourceEntryIndex(workspace.workdir, workspace.activeTargetId)
            ?.takeIf {
                it.toolVersion == toolVersion &&
                    it.contentFingerprint == workspace.snapshot.contentFingerprint
            }
            ?.let { return it.entries }

        val workdirPath = Path.of(workspace.workdir)
        val entries = buildList {
            inventory.apkFiles.forEach { apkSourcePath ->
                addAll(listApkEntries(workdirPath.resolve(apkSourcePath).normalize(), apkSourcePath))
            }
        }
        val normalizedEntries = collapseLogicalEntries(entries)
            .distinct()
            .sortedWith(
                compareBy<ResourceEntry>(
                    { it.type.orEmpty() },
                    { it.name.orEmpty() },
                    { it.filePath.orEmpty() },
                    { it.sourcePath.orEmpty() },
                    { it.sourceEntry.orEmpty() },
                    { it.resolution.name },
                    { it.resourceId.orEmpty() },
                ),
            )
        store.saveResourceEntryIndex(
            workdir = workspace.workdir,
            targetId = workspace.activeTargetId,
            resourceEntryIndex = ResourceEntryIndexRecord(
                generatedAt = resourceNowUtc(),
                targetId = workspace.activeTargetId,
                toolVersion = toolVersion,
                contentFingerprint = workspace.snapshot.contentFingerprint,
                entries = normalizedEntries,
            ),
        )
        return normalizedEntries
    }

    private fun listApkEntries(apkPath: Path, sourcePath: String): List<ResourceEntry> =
        ApkModule.loadApkFile(apkPath.toFile()).use { apk ->
            val mappedEntries = apk.listResFiles()
                .flatMap { resFile -> mapApkResFile(resFile, sourcePath) }
            val mappedKeys = mappedEntries
                .filter { it.resourceId != null || it.type != null || it.name != null }
                .mapTo(mutableSetOf()) {
                    TableBackedKey(
                        resourceId = it.resourceId,
                        type = it.type,
                        name = it.name,
                        sourcePath = it.sourcePath,
                    )
                }
            val unresolvedEntries = apk.tableBlock.resources
                .asSequence()
                .map { resource ->
                    ResourceEntry(
                        resourceId = resource.hexId,
                        type = resource.type,
                        name = resource.name,
                        filePath = null,
                        sourcePath = sourcePath,
                        sourceEntry = null,
                        resolution = ResourceResolution.Unresolved,
                    )
                }
                .filter { entry ->
                    TableBackedKey(
                        resourceId = entry.resourceId,
                        type = entry.type,
                        name = entry.name,
                        sourcePath = entry.sourcePath,
                    ) !in mappedKeys
                }
                .toList()
            mappedEntries + unresolvedEntries
        }

    private fun mapApkResFile(resFile: ResFile, sourcePath: String): List<ResourceEntry> {
        val filePath = resFile.filePath ?: return emptyList()
        val tableBacked = mutableListOf<ResourceEntry>()
        resFile.forEach { entry ->
            val resourceEntry = entry.resourceEntry
            tableBacked.add(
                ResourceEntry(
                    resourceId = resourceEntry.hexId,
                    type = entry.typeName,
                    name = entry.name,
                    filePath = filePath,
                    sourcePath = sourcePath,
                    sourceEntry = filePath,
                    resolution = ResourceResolution.TableBacked,
                ),
            )
        }
        if (tableBacked.isNotEmpty()) {
            return tableBacked
        }
        val inferred = inferFromResFilePath(filePath) ?: return emptyList()
        return listOf(
            ResourceEntry(
                resourceId = null,
                type = inferred.type,
                name = inferred.name,
                filePath = filePath,
                sourcePath = sourcePath,
                sourceEntry = filePath,
                resolution = ResourceResolution.PathInferred,
            ),
        )
    }

    private fun inferFromResFilePath(filePath: String): InferredResourcePath? {
        val normalized = filePath.replace('\\', '/')
        val parts = normalized.split('/')
        val resIndex = parts.indexOf("res")
        if (resIndex < 0 || resIndex + 2 >= parts.size) {
            return null
        }
        val typeDirectory = parts[resIndex + 1]
        val baseType = typeDirectory.substringBefore('-')
        if (baseType.isBlank() || baseType == "values") {
            return null
        }
        val fileName = parts.last()
        val extensionIndex = fileName.lastIndexOf('.')
        if (extensionIndex <= 0) {
            return null
        }
        val entryName = fileName.substring(0, extensionIndex)
        if (entryName.isBlank()) {
            return null
        }
        return InferredResourcePath(
            type = baseType,
            name = entryName,
        )
    }

    private fun collapseLogicalEntries(entries: List<ResourceEntry>): List<ResourceEntry> {
        val grouped = linkedMapOf<TableBackedKey, MutableList<ResourceEntry>>()
        val passthrough = mutableListOf<ResourceEntry>()
        entries.forEach { entry ->
            if (entry.resolution != ResourceResolution.TableBacked) {
                passthrough += entry
                return@forEach
            }
            val key = TableBackedKey(
                resourceId = entry.resourceId,
                type = entry.type,
                name = entry.name,
                sourcePath = entry.sourcePath,
            )
            grouped.getOrPut(key) { mutableListOf() } += entry
        }
        val collapsed = grouped.values.map { candidates ->
            candidates.minWithOrNull(compareBy<ResourceEntry>(
                { qualifierRank(it.filePath) },
                { it.filePath.orEmpty().length },
                { it.filePath.orEmpty() },
            )) ?: error("unexpected empty candidate set")
        }
        return collapsed + passthrough
    }

    private fun qualifierRank(filePath: String?): Int {
        if (filePath == null) return Int.MAX_VALUE
        val parts = filePath.replace('\\', '/').split('/')
        val resIndex = parts.indexOf("res")
        if (resIndex < 0 || resIndex + 1 >= parts.size) {
            return Int.MAX_VALUE
        }
        val typeDirectory = parts[resIndex + 1]
        return typeDirectory.count { it == '-' }
    }
}

private data class InferredResourcePath(
    val type: String,
    val name: String,
)

private data class TableBackedKey(
    val resourceId: String?,
    val type: String?,
    val name: String?,
    val sourcePath: String?,
)
