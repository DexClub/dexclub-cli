package io.github.dexclub.core.impl.workspace.store

import io.github.dexclub.core.api.workspace.GcResult
import io.github.dexclub.core.api.workspace.WorkspaceResolveError
import io.github.dexclub.core.api.workspace.WorkspaceResolveErrorReason
import io.github.dexclub.core.impl.shared.workspaceJson
import io.github.dexclub.core.impl.workspace.model.ClassSourceMapRecord
import io.github.dexclub.core.impl.workspace.model.DecodedXmlCacheRecord
import io.github.dexclub.core.impl.workspace.model.ManifestCacheRecord
import io.github.dexclub.core.impl.workspace.model.ResourceEntryIndexRecord
import io.github.dexclub.core.impl.workspace.model.ResourceTableCacheRecord
import io.github.dexclub.core.impl.workspace.model.SnapshotRecord
import io.github.dexclub.core.impl.workspace.model.TargetRecord
import io.github.dexclub.core.impl.workspace.model.WorkspaceRecord
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import kotlinx.serialization.SerializationException

internal class DefaultWorkspaceStore : WorkspaceStore {
    override fun exists(workdir: String): Boolean = Files.isDirectory(dexclubDirPath(workdir))

    override fun dexclubDir(workdir: String): String = dexclubDirPath(workdir).toString()

    override fun initialize(
        workdir: String,
        workspace: WorkspaceRecord,
        target: TargetRecord,
        snapshot: SnapshotRecord,
    ) {
        val targetDir = targetDir(workdir, target.targetId)
        writeState {
            Files.createDirectories(targetDir.resolve("cache/decoded"))
            Files.createDirectories(targetDir.resolve("cache/indexes"))
            writeJson(workspaceFile(workdir), workspace.toDto())
            writeJson(targetDir.resolve("target.json"), target.toDto())
            writeJson(targetDir.resolve("snapshot.json"), snapshot.toDto())
        }
    }

    override fun loadWorkspace(workdir: String): WorkspaceRecord =
        readJson<WorkspaceDto>(
            path = workspaceFile(workdir),
            reason = WorkspaceResolveErrorReason.InvalidWorkspaceMetadata,
            workdir = workdir,
            missingMessage = "Workspace metadata is missing: ${workspaceFile(workdir)}",
        ).toRecord()

    override fun saveWorkspace(workdir: String, workspace: WorkspaceRecord) {
        writeState {
            writeJson(workspaceFile(workdir), workspace.toDto())
        }
    }

    override fun loadTarget(workdir: String, targetId: String): TargetRecord =
        readJson<TargetDto>(
            path = targetDir(workdir, targetId).resolve("target.json"),
            reason = WorkspaceResolveErrorReason.InvalidTargetMetadata,
            workdir = workdir,
            missingMessage = "Target metadata is missing: ${targetDir(workdir, targetId).resolve("target.json")}",
        ).toRecord()

    override fun saveTarget(workdir: String, target: TargetRecord) {
        writeState {
            Files.createDirectories(targetDir(workdir, target.targetId))
            writeJson(targetDir(workdir, target.targetId).resolve("target.json"), target.toDto())
        }
    }

    override fun loadSnapshot(workdir: String, targetId: String): SnapshotRecord? {
        val snapshotPath = targetDir(workdir, targetId).resolve("snapshot.json")
        if (!Files.isRegularFile(snapshotPath)) return null
        return readJson<SnapshotDto>(
            path = snapshotPath,
            reason = WorkspaceResolveErrorReason.InvalidSnapshot,
            workdir = workdir,
            missingMessage = "Snapshot is missing: $snapshotPath",
        ).toRecord()
    }

    override fun saveSnapshot(workdir: String, targetId: String, snapshot: SnapshotRecord) {
        writeState {
            Files.createDirectories(targetDir(workdir, targetId))
            writeJson(targetDir(workdir, targetId).resolve("snapshot.json"), snapshot.toDto())
        }
    }

    override fun loadClassSourceMap(workdir: String, targetId: String): ClassSourceMapRecord? {
        val indexPath = targetDir(workdir, targetId).resolve("cache/indexes/class-source-map.json")
        if (!Files.isRegularFile(indexPath)) return null
        return try {
            workspaceJson.decodeFromString<ClassSourceMapDto>(Files.readString(indexPath)).toRecord()
        } catch (_: Exception) {
            null
        }
    }

    override fun saveClassSourceMap(workdir: String, targetId: String, classSourceMap: ClassSourceMapRecord) {
        writeState {
            Files.createDirectories(targetDir(workdir, targetId).resolve("cache/indexes"))
            writeJson(
                targetDir(workdir, targetId).resolve("cache/indexes/class-source-map.json"),
                classSourceMap.toDto(),
            )
        }
    }

    override fun loadManifestCache(workdir: String, targetId: String): ManifestCacheRecord? {
        val cachePath = targetDir(workdir, targetId).resolve("cache/decoded/manifest.json")
        if (!Files.isRegularFile(cachePath)) return null
        return try {
            workspaceJson.decodeFromString<ManifestCacheDto>(Files.readString(cachePath)).toRecord()
        } catch (_: Exception) {
            null
        }
    }

    override fun saveManifestCache(workdir: String, targetId: String, manifestCache: ManifestCacheRecord) {
        writeState {
            Files.createDirectories(targetDir(workdir, targetId).resolve("cache/decoded"))
            writeJson(
                targetDir(workdir, targetId).resolve("cache/decoded/manifest.json"),
                manifestCache.toDto(),
            )
        }
    }

    override fun loadResourceTableCache(workdir: String, targetId: String): ResourceTableCacheRecord? {
        val cachePath = targetDir(workdir, targetId).resolve("cache/decoded/resource-table.json")
        if (!Files.isRegularFile(cachePath)) return null
        return try {
            workspaceJson.decodeFromString<ResourceTableCacheDto>(Files.readString(cachePath)).toRecord()
        } catch (_: Exception) {
            null
        }
    }

    override fun saveResourceTableCache(workdir: String, targetId: String, resourceTableCache: ResourceTableCacheRecord) {
        writeState {
            Files.createDirectories(targetDir(workdir, targetId).resolve("cache/decoded"))
            writeJson(
                targetDir(workdir, targetId).resolve("cache/decoded/resource-table.json"),
                resourceTableCache.toDto(),
            )
        }
    }

    override fun loadDecodedXmlCache(workdir: String, targetId: String, xmlId: String): DecodedXmlCacheRecord? {
        val cachePath = targetDir(workdir, targetId).resolve("cache/decoded/xml/$xmlId.json")
        if (!Files.isRegularFile(cachePath)) return null
        return try {
            workspaceJson.decodeFromString<DecodedXmlCacheDto>(Files.readString(cachePath)).toRecord()
        } catch (_: Exception) {
            null
        }
    }

    override fun saveDecodedXmlCache(
        workdir: String,
        targetId: String,
        xmlId: String,
        decodedXmlCache: DecodedXmlCacheRecord,
    ) {
        writeState {
            Files.createDirectories(targetDir(workdir, targetId).resolve("cache/decoded/xml"))
            writeJson(
                targetDir(workdir, targetId).resolve("cache/decoded/xml/$xmlId.json"),
                decodedXmlCache.toDto(),
            )
        }
    }

    override fun loadResourceEntryIndex(workdir: String, targetId: String): ResourceEntryIndexRecord? {
        val indexPath = targetDir(workdir, targetId).resolve("cache/indexes/resource-entry-index.json")
        if (!Files.isRegularFile(indexPath)) return null
        return try {
            workspaceJson.decodeFromString<ResourceEntryIndexDto>(Files.readString(indexPath)).toRecord()
        } catch (_: Exception) {
            null
        }
    }

    override fun saveResourceEntryIndex(workdir: String, targetId: String, resourceEntryIndex: ResourceEntryIndexRecord) {
        writeState {
            Files.createDirectories(targetDir(workdir, targetId).resolve("cache/indexes"))
            writeJson(
                targetDir(workdir, targetId).resolve("cache/indexes/resource-entry-index.json"),
                resourceEntryIndex.toDto(),
            )
        }
    }

    override fun clearTargetCache(workdir: String, targetId: String): GcResult {
        val decodedDir = targetDir(workdir, targetId).resolve("cache/decoded")
        val indexesDir = targetDir(workdir, targetId).resolve("cache/indexes")
        val deleted = deleteDirectoryContents(decodedDir) + deleteDirectoryContents(indexesDir)
        writeState {
            Files.createDirectories(decodedDir)
            Files.createDirectories(indexesDir)
        }
        return GcResult(
            workdir = normalizedWorkdir(workdir).toString(),
            targetId = targetId,
            deletedFiles = deleted.deletedFiles,
            deletedBytes = deleted.deletedBytes,
        )
    }

    private inline fun <reified T> readJson(
        path: Path,
        reason: WorkspaceResolveErrorReason,
        workdir: String,
        missingMessage: String,
    ): T {
        if (!Files.isRegularFile(path)) {
            throw WorkspaceResolveError(
                reason = reason,
                workdir = normalizedWorkdir(workdir).toString(),
                message = missingMessage,
            )
        }
        return try {
            workspaceJson.decodeFromString<T>(Files.readString(path))
        } catch (cause: SerializationException) {
            throw WorkspaceResolveError(
                reason = reason,
                workdir = normalizedWorkdir(workdir).toString(),
                message = "Invalid workspace state file: $path",
                cause = cause,
            )
        } catch (cause: IllegalArgumentException) {
            throw WorkspaceResolveError(
                reason = reason,
                workdir = normalizedWorkdir(workdir).toString(),
                message = "Invalid workspace state value: $path",
                cause = cause,
            )
        } catch (cause: IOException) {
            throw WorkspaceResolveError(
                reason = reason,
                workdir = normalizedWorkdir(workdir).toString(),
                message = "Failed to read workspace state file: $path",
                cause = cause,
            )
        }
    }

    private fun writeJson(path: Path, value: Any) {
        Files.createDirectories(path.parent)
        val content = when (value) {
            is WorkspaceDto -> workspaceJson.encodeToString(WorkspaceDto.serializer(), value)
            is TargetDto -> workspaceJson.encodeToString(TargetDto.serializer(), value)
            is SnapshotDto -> workspaceJson.encodeToString(SnapshotDto.serializer(), value)
            is ClassSourceMapDto -> workspaceJson.encodeToString(ClassSourceMapDto.serializer(), value)
            is ManifestCacheDto -> workspaceJson.encodeToString(ManifestCacheDto.serializer(), value)
            is ResourceTableCacheDto -> workspaceJson.encodeToString(ResourceTableCacheDto.serializer(), value)
            is DecodedXmlCacheDto -> workspaceJson.encodeToString(DecodedXmlCacheDto.serializer(), value)
            is ResourceEntryIndexDto -> workspaceJson.encodeToString(ResourceEntryIndexDto.serializer(), value)
            else -> error("Unsupported workspace dto: ${value::class.qualifiedName}")
        }
        Files.writeString(path, content)
    }

    private fun writeState(action: () -> Unit) {
        try {
            action()
        } catch (cause: IOException) {
            throw IllegalStateException("Failed to write workspace state", cause)
        }
    }

    private fun workspaceFile(workdir: String): Path = dexclubDirPath(workdir).resolve("workspace.json")

    private fun targetDir(workdir: String, targetId: String): Path =
        dexclubDirPath(workdir).resolve("targets").resolve(targetId)

    private fun dexclubDirPath(workdir: String): Path = normalizedWorkdir(workdir).resolve(".dexclub")

    private fun normalizedWorkdir(workdir: String): Path = Paths.get(workdir).toAbsolutePath().normalize()

    private fun deleteDirectoryContents(path: Path): DeletedContent {
        if (!Files.exists(path)) return DeletedContent()
        val files = mutableListOf<Path>()
        Files.walk(path).use { walk ->
            walk.filter { it != path }
                .sorted(Comparator.reverseOrder())
                .forEach(files::add)
        }
        var deletedFiles = 0
        var deletedBytes = 0L
        files.forEach { item ->
            if (Files.isRegularFile(item)) {
                deletedFiles += 1
                deletedBytes += Files.size(item)
            }
            Files.deleteIfExists(item)
        }
        return DeletedContent(
            deletedFiles = deletedFiles,
            deletedBytes = deletedBytes,
        )
    }

    private data class DeletedContent(
        val deletedFiles: Int = 0,
        val deletedBytes: Long = 0L,
    ) {
        operator fun plus(other: DeletedContent): DeletedContent =
            DeletedContent(
                deletedFiles = deletedFiles + other.deletedFiles,
                deletedBytes = deletedBytes + other.deletedBytes,
            )
    }
}
