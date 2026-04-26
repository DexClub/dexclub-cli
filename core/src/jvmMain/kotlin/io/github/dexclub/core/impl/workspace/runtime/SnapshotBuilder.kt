package io.github.dexclub.core.impl.workspace.runtime

import io.github.dexclub.core.api.shared.WorkspaceKind
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.core.impl.workspace.model.SnapshotRecord
import io.github.dexclub.core.impl.workspace.model.TargetRecord
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

internal class SnapshotBuilder(
    private val capabilityResolver: CapabilityResolver,
    private val nowProvider: () -> Instant = { Instant.now() },
) {
    fun build(workdirPath: Path, target: TargetRecord, inventory: MaterialInventory): SnapshotRecord {
        val kind = resolveKind(inventory)
        val capabilities = capabilityResolver.resolve(inventory)
        return SnapshotRecord(
            generatedAt = nowProvider().toString(),
            targetId = target.targetId,
            inputPath = target.inputPath,
            kind = kind,
            inventory = inventory,
            inventoryFingerprint = inventoryFingerprint(inventory),
            contentFingerprint = contentFingerprint(workdirPath, inventory),
            capabilities = capabilities,
        )
    }

    private fun resolveKind(inventory: MaterialInventory): WorkspaceKind {
        val counts = inventory.counts()
        return when {
            counts.apkCount == 1 -> WorkspaceKind.Apk
            counts.dexCount == 1 -> WorkspaceKind.Dex
            counts.manifestCount == 1 -> WorkspaceKind.Manifest
            counts.arscCount == 1 -> WorkspaceKind.Arsc
            counts.binaryXmlCount == 1 -> WorkspaceKind.Axml
            else -> throw IllegalArgumentException("single-file inventory produced an unsupported kind: $counts")
        }
    }

    private fun inventoryFingerprint(inventory: MaterialInventory): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inventoryEntries(inventory).forEach { (bucket, path) ->
            digest.update(bucket.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(path.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun contentFingerprint(workdirPath: Path, inventory: MaterialInventory): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inventoryEntries(inventory).forEach { (_, path) ->
            digest.update(path.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            val materialPath = when (path) {
                "." -> workdirPath
                else -> workdirPath.resolve(path).normalize()
            }
            digest.update(digestMaterial(materialPath).toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun digestMaterial(path: Path): String =
        if (Files.isDirectory(path)) {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.walk(path).use { walk ->
                walk.filter { Files.isRegularFile(it) }
                    .sorted()
                    .forEach { file ->
                        val relative = path.relativize(file).toString().replace('\\', '/')
                        digest.update(relative.toByteArray(StandardCharsets.UTF_8))
                        digest.update(0)
                        digest.update(sha256Hex(Files.readAllBytes(file)).toByteArray(StandardCharsets.UTF_8))
                        digest.update(0)
                    }
            }
            digest.digest().joinToString(separator = "") { "%02x".format(it) }
        } else {
            sha256Hex(Files.readAllBytes(path))
        }

    private fun inventoryEntries(inventory: MaterialInventory): List<Pair<String, String>> =
        buildList {
            inventory.apkFiles.forEach { add("apkFiles" to it) }
            inventory.dexFiles.forEach { add("dexFiles" to it) }
            inventory.manifestFiles.forEach { add("manifestFiles" to it) }
            inventory.arscFiles.forEach { add("arscFiles" to it) }
            inventory.binaryXmlFiles.forEach { add("binaryXmlFiles" to it) }
        }
}
