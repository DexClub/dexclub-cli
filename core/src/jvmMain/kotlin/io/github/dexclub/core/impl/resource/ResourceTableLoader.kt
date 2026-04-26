package io.github.dexclub.core.impl.resource

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.TableBlock
import io.github.dexclub.core.api.resource.ResourceDecodeError
import io.github.dexclub.core.api.resource.ResourceDecodeErrorReason
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import java.nio.file.Path
import java.util.zip.ZipFile

private const val resourceTableEntryName: String = "resources.arsc"

internal class ResourceTableLoader {
    fun resolveSource(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
    ): ResourceTableSource {
        val sources = buildList {
            inventory.arscFiles.forEach { add(ResourceTableSource.File(it)) }
            inventory.apkFiles.forEach { add(ResourceTableSource.Apk(it)) }
        }
        return when (sources.size) {
            0 -> throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.ResourceTableSourceMissing,
                message = "Current workspace does not contain a resource table source",
            )

            1 -> sources.single()
            else -> throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.AmbiguousResourceTableSource,
                message = "Current workspace contains multiple resource table sources; initialize a single APK or resources.arsc workspace instead",
            )
        }
    }

    fun load(
        workspace: WorkspaceContext,
        source: ResourceTableSource,
    ): LoadedResourceTable {
        val workdirPath = Path.of(workspace.workdir)
        return when (source) {
            is ResourceTableSource.File -> {
                val sourcePath = source.sourcePath
                val tableBlock = try {
                    TableBlock.load(workdirPath.resolve(sourcePath).normalize().toFile())
                } catch (error: Exception) {
                    throw ResourceDecodeError(
                        reason = ResourceDecodeErrorReason.ResourceTableDecodeFailed,
                        sourcePath = sourcePath,
                        message = "Failed to decode resource table: $sourcePath",
                        cause = error,
                    )
                }
                LoadedResourceTable(
                    tableBlock = tableBlock,
                    sourcePath = sourcePath,
                    sourceEntry = null,
                )
            }

            is ResourceTableSource.Apk -> {
                val sourcePath = source.sourcePath
                val apkPath = workdirPath.resolve(sourcePath).normalize()
                ensureApkContainsResourceTable(apkPath, sourcePath)
                val tableBlock = try {
                    ApkModule.loadApkFile(apkPath.toFile()).use { apk ->
                        apk.tableBlock
                    }
                } catch (error: ResourceDecodeError) {
                    throw error
                } catch (error: Exception) {
                    throw ResourceDecodeError(
                        reason = ResourceDecodeErrorReason.ResourceTableDecodeFailed,
                        sourcePath = sourcePath,
                        message = "Failed to decode resource table from APK: $sourcePath",
                        cause = error,
                    )
                }
                LoadedResourceTable(
                    tableBlock = tableBlock,
                    sourcePath = sourcePath,
                    sourceEntry = resourceTableEntryName,
                )
            }
        }
    }

    fun load(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
    ): LoadedResourceTable = load(workspace, resolveSource(workspace, inventory))

    private fun ensureApkContainsResourceTable(apkPath: Path, sourcePath: String) {
        ZipFile(apkPath.toFile()).use { zip ->
            if (zip.getEntry(resourceTableEntryName) == null) {
                throw ResourceDecodeError(
                    reason = ResourceDecodeErrorReason.ResourceTableEntryMissing,
                    sourcePath = sourcePath,
                    message = "APK does not contain resources.arsc: $sourcePath",
                )
            }
        }
    }
}

internal data class LoadedResourceTable(
    val tableBlock: TableBlock,
    val sourcePath: String,
    val sourceEntry: String?,
)

internal sealed interface ResourceTableSource {
    val sourcePath: String
    val sourceEntry: String?

    data class File(override val sourcePath: String) : ResourceTableSource {
        override val sourceEntry: String? = null
    }

    data class Apk(override val sourcePath: String) : ResourceTableSource {
        override val sourceEntry: String = resourceTableEntryName
    }
}
