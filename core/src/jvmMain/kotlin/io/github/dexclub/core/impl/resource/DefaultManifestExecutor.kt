package io.github.dexclub.core.impl.resource

import io.github.dexclub.core.api.resource.ResourceDecodeError
import io.github.dexclub.core.api.resource.ResourceDecodeErrorReason
import io.github.dexclub.core.api.resource.ManifestResult
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.core.impl.workspace.model.ManifestCacheRecord
import io.github.dexclub.core.impl.workspace.store.WorkspaceStore
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

internal class DefaultManifestExecutor(
    private val store: WorkspaceStore,
    private val toolVersion: String,
) : ManifestExecutor {
    override fun decodeManifest(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
    ): ManifestResult {
        val workdirPath = Path.of(workspace.workdir)
        val manifestSources = buildList {
            inventory.manifestFiles.forEach { add(ManifestSource.File(it)) }
            inventory.apkFiles.forEach { add(ManifestSource.Apk(it)) }
        }
        val source = when (manifestSources.size) {
            0 -> throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.ManifestSourceMissing,
                message = "Current workspace does not contain a manifest source",
            )

            1 -> manifestSources.single()
            else -> throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.AmbiguousManifestSource,
                message = "Current workspace contains multiple manifest sources; initialize a single APK or manifest file workspace instead",
            )
        }
        val sourceFingerprint = resourceSourceFingerprint(workspace.workdir, source.sourcePath)
        store.loadManifestCache(workspace.workdir, workspace.activeTargetId)
            ?.takeIf {
                it.toolVersion == toolVersion &&
                    it.sourcePath == source.sourcePath &&
                    it.sourceEntry == source.sourceEntry &&
                    it.sourceFingerprint == sourceFingerprint
            }
            ?.let {
                return ManifestResult(
                    sourcePath = it.sourcePath,
                    sourceEntry = it.sourceEntry,
                    text = it.text,
                )
            }
        val result = when (source) {
            is ManifestSource.File ->
                decodeManifestFile(
                    manifestPath = workdirPath.resolve(source.sourcePath).normalize(),
                    sourcePath = source.sourcePath,
                )

            is ManifestSource.Apk ->
                decodeManifestFromApk(
                    apkPath = workdirPath.resolve(source.sourcePath).normalize(),
                    sourcePath = source.sourcePath,
                )
        }
        store.saveManifestCache(
            workdir = workspace.workdir,
            targetId = workspace.activeTargetId,
            manifestCache = ManifestCacheRecord(
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

    private fun decodeManifestFile(manifestPath: Path, sourcePath: String): ManifestResult =
        ManifestResult(
            sourcePath = sourcePath,
            sourceEntry = null,
            text = decodeManifestFileText(manifestPath, sourcePath),
        )

    private fun decodeManifestFromApk(apkPath: Path, sourcePath: String): ManifestResult {
        val manifestBytes = ZipFile(apkPath.toFile()).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST_ENTRY_NAME)
                ?: throw ResourceDecodeError(
                    reason = ResourceDecodeErrorReason.ManifestEntryMissing,
                    sourcePath = sourcePath,
                    message = "APK does not contain AndroidManifest.xml: $sourcePath",
                )
            zip.getInputStream(manifestEntry).use { it.readBytes() }
        }
        val decodedText = ResourceXmlTextDecoder.decodeTextXmlOrNull(manifestBytes)
            ?.trim()
            ?.takeIf { it.startsWith("<") }
            ?: decodeBinaryManifestFromApk(apkPath, sourcePath)
        return ManifestResult(
            sourcePath = sourcePath,
            sourceEntry = MANIFEST_ENTRY_NAME,
            text = decodedText,
        )
    }

    private fun decodeManifestFileText(manifestPath: Path, sourcePath: String): String =
        ResourceXmlTextDecoder.decodeTextXmlOrNull(Files.readAllBytes(manifestPath))
            ?.trim()
            ?.takeIf { it.startsWith("<") }
            ?: decodeBinaryManifestFile(manifestPath, sourcePath)

    private fun decodeBinaryManifestFile(manifestPath: Path, sourcePath: String): String {
        return try {
            val manifestBlock = AndroidManifestBlock.load(manifestPath.toFile())
            ApkModule().use { apk ->
                apk.setManifest(manifestBlock)
                apk.ensureTableBlock()
                apk.refreshManifest()
                apk.initializeAndroidFramework(manifestBlock.toXml(false))
                manifestBlock.serializeToXml().trim()
            }
        } catch (error: ResourceDecodeError) {
            throw error
        } catch (error: Exception) {
            throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.ManifestDecodeFailed,
                sourcePath = sourcePath,
                message = "Failed to decode manifest file: $sourcePath",
                cause = error,
            )
        }
    }

    private fun decodeBinaryManifestFromApk(apkPath: Path, sourcePath: String): String =
        try {
            ApkModule.loadApkFile(apkPath.toFile()).use { apk ->
                val manifestBlock = apk.getAndroidManifest()
                    ?: throw ResourceDecodeError(
                        reason = ResourceDecodeErrorReason.ManifestDecodeFailed,
                        sourcePath = sourcePath,
                        message = "APK does not expose a decoded manifest resource: $sourcePath",
                    )
                apk.initializeAndroidFramework(manifestBlock.toXml(false))
                manifestBlock.serializeToXml().trim()
            }
        } catch (error: ResourceDecodeError) {
            throw error
        } catch (error: Exception) {
            throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.ManifestDecodeFailed,
                sourcePath = sourcePath,
                message = "Failed to decode manifest from APK: $sourcePath",
                cause = error,
            )
        }

    private companion object {
        const val MANIFEST_ENTRY_NAME = "AndroidManifest.xml"
    }
}

private sealed interface ManifestSource {
    val sourcePath: String
    val sourceEntry: String?

    data class File(override val sourcePath: String) : ManifestSource {
        override val sourceEntry: String? = null
    }

    data class Apk(override val sourcePath: String) : ManifestSource {
        override val sourceEntry: String = "AndroidManifest.xml"
    }
}
