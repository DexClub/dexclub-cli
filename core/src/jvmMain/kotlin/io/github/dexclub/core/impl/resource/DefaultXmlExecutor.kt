package io.github.dexclub.core.impl.resource

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import io.github.dexclub.core.api.resource.DecodeXmlRequest
import io.github.dexclub.core.api.resource.DecodedXmlResult
import io.github.dexclub.core.api.resource.ResourceDecodeError
import io.github.dexclub.core.api.resource.ResourceDecodeErrorReason
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.DecodedXmlCacheRecord
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.core.impl.workspace.store.WorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

internal class DefaultXmlExecutor(
    private val store: WorkspaceStore,
    private val toolVersion: String,
) : XmlExecutor {
    override fun decodeXml(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: DecodeXmlRequest,
    ): DecodedXmlResult {
        val requestPath = request.path.trim().replace('\\', '/')
        val workdirPath = Path.of(workspace.workdir)
        val candidates = mutableListOf<XmlSource>()

        if (requestPath.isNotEmpty() && matchesWorkspaceFile(requestPath, inventory, workdirPath)) {
            candidates += XmlSource.File(requestPath)
        }

        inventory.apkFiles.forEach { apkSourcePath ->
            val apkPath = workdirPath.resolve(apkSourcePath).normalize()
            if (apkContainsEntry(apkPath, requestPath)) {
                candidates += XmlSource.Apk(apkSourcePath, requestPath)
            }
        }

        val source = when (candidates.size) {
            0 -> throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.XmlPathNotFound,
                message = "Current workspace does not contain XML path: $requestPath",
            )

            1 -> candidates.single()
            else -> throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.AmbiguousXmlPath,
                message = "Current workspace contains multiple XML sources for path: $requestPath",
            )
        }

        val sourceFingerprint = resourceSourceFingerprint(workspace.workdir, source.sourcePath)
        val xmlId = resourceXmlCacheId(source.sourcePath, source.sourceEntry)
        store.loadDecodedXmlCache(workspace.workdir, workspace.activeTargetId, xmlId)
            ?.takeIf {
                it.toolVersion == toolVersion &&
                it.sourcePath == source.sourcePath &&
                    it.sourceEntry == source.sourceEntry &&
                    it.sourceFingerprint == sourceFingerprint
            }
            ?.let { return DecodedXmlResult(sourcePath = it.sourcePath, sourceEntry = it.sourceEntry, text = it.text) }

        val result = when (source) {
            is XmlSource.File ->
                decodeWorkspaceFile(
                    workspaceFilePath = workdirPath.resolve(source.sourcePath).normalize(),
                    sourcePath = source.sourcePath,
                    inventory = inventory,
                    workdirPath = workdirPath,
                )

            is XmlSource.Apk ->
                decodeApkEntry(
                    apkPath = workdirPath.resolve(source.sourcePath).normalize(),
                    sourcePath = source.sourcePath,
                    sourceEntry = source.sourceEntry,
                )
        }
        store.saveDecodedXmlCache(
            workdir = workspace.workdir,
            targetId = workspace.activeTargetId,
            xmlId = xmlId,
            decodedXmlCache = DecodedXmlCacheRecord(
                generatedAt = resourceNowUtc(),
                targetId = workspace.activeTargetId,
                toolVersion = toolVersion,
                sourcePath = result.sourcePath ?: source.sourcePath,
                sourceEntry = result.sourceEntry,
                sourceFingerprint = sourceFingerprint,
                text = result.text,
            ),
        )
        return result
    }

    private fun decodeWorkspaceFile(
        workspaceFilePath: Path,
        sourcePath: String,
        inventory: MaterialInventory,
        workdirPath: Path,
    ): DecodedXmlResult {
        val bytes = Files.readAllBytes(workspaceFilePath)
        val text = ResourceXmlTextDecoder.decodeTextXmlOrNull(bytes)
            ?.trim()
            ?.takeIf { it.startsWith("<") }
            ?: decodeBinaryWorkspaceXml(
                workspaceFilePath = workspaceFilePath,
                sourcePath = sourcePath,
                inventory = inventory,
                workdirPath = workdirPath,
            )
        return DecodedXmlResult(
            sourcePath = sourcePath,
            sourceEntry = null,
            text = text,
        )
    }

    private fun decodeBinaryWorkspaceXml(
        workspaceFilePath: Path,
        sourcePath: String,
        inventory: MaterialInventory,
        workdirPath: Path,
    ): String =
        try {
            val document = ResXmlDocument().apply {
                readBytes(workspaceFilePath.toFile())
            }
            val tableBlock = loadStandaloneTableBlock(workdirPath, inventory)
            if (tableBlock != null) {
                ApkModule().use { apk ->
                    apk.setTableBlock(tableBlock)
                    document.setApkFile(apk)
                    document.serializeToXml().trim()
                }
            } else {
                document.serializeToXml().trim()
            }
        } catch (error: ResourceDecodeError) {
            throw error
        } catch (error: Exception) {
            throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.XmlDecodeFailed,
                sourcePath = sourcePath,
                message = "Failed to decode XML file: $sourcePath",
                cause = error,
            )
        }

    private fun decodeApkEntry(
        apkPath: Path,
        sourcePath: String,
        sourceEntry: String,
    ): DecodedXmlResult {
        val entryBytes = ZipFile(apkPath.toFile()).use { zip ->
            val xmlEntry = zip.getEntry(sourceEntry)
                ?: throw ResourceDecodeError(
                    reason = ResourceDecodeErrorReason.XmlPathNotFound,
                    sourcePath = sourcePath,
                    message = "APK does not contain XML path: $sourceEntry",
                )
            zip.getInputStream(xmlEntry).use { it.readBytes() }
        }
        val text = ResourceXmlTextDecoder.decodeTextXmlOrNull(entryBytes)
            ?.trim()
            ?.takeIf { it.startsWith("<") }
            ?: decodeBinaryApkXml(
                apkPath = apkPath,
                sourcePath = sourcePath,
                sourceEntry = sourceEntry,
            )
        return DecodedXmlResult(
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
            text = text,
        )
    }

    private fun decodeBinaryApkXml(
        apkPath: Path,
        sourcePath: String,
        sourceEntry: String,
    ): String =
        try {
            ApkModule.loadApkFile(apkPath.toFile()).use { apk ->
                val document = apk.loadResXmlDocument(sourceEntry)
                document.serializeToXml().trim()
            }
        } catch (error: ResourceDecodeError) {
            throw error
        } catch (error: Exception) {
            throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.XmlDecodeFailed,
                sourcePath = sourcePath,
                message = "Failed to decode XML path '$sourceEntry' from APK: $sourcePath",
                cause = error,
            )
        }

    private fun matchesWorkspaceFile(
        requestPath: String,
        inventory: MaterialInventory,
        workdirPath: Path,
    ): Boolean {
        val candidate = workdirPath.resolve(requestPath).normalize()
        if (!Files.isRegularFile(candidate)) {
            return false
        }
        if (requestPath in inventory.manifestFiles || requestPath in inventory.binaryXmlFiles) {
            return true
        }
        return false
    }

    private fun loadStandaloneTableBlock(workdirPath: Path, inventory: MaterialInventory): TableBlock? =
        when (inventory.arscFiles.size) {
            0 -> null
            1 -> TableBlock.load(workdirPath.resolve(inventory.arscFiles.single()).normalize().toFile())
            else -> throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.AmbiguousXmlPath,
                message = "Current workspace contains multiple resource tables; decode XML from a single-arsc or APK workspace instead",
            )
        }

    private fun apkContainsEntry(apkPath: Path, requestPath: String): Boolean =
        ZipFile(apkPath.toFile()).use { zip ->
            zip.getEntry(requestPath) != null
        }
}

private sealed interface XmlSource {
    val sourcePath: String
    val sourceEntry: String?

    data class File(override val sourcePath: String) : XmlSource {
        override val sourceEntry: String? = null
    }

    data class Apk(
        override val sourcePath: String,
        override val sourceEntry: String,
    ) : XmlSource
}
