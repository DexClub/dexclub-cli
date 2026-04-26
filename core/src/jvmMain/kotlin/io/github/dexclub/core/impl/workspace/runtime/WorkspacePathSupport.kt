package io.github.dexclub.core.impl.workspace.runtime

import io.github.dexclub.core.api.shared.InputType
import io.github.dexclub.core.api.workspace.WorkspaceInitError
import io.github.dexclub.core.api.workspace.WorkspaceInitErrorReason
import io.github.dexclub.core.api.workspace.WorkspaceResolveError
import io.github.dexclub.core.api.workspace.WorkspaceResolveErrorReason
import io.github.dexclub.core.api.workspace.WorkspaceRef
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

internal data class ResolvedInput(
    val ref: WorkspaceRef,
    val workdirPath: Path,
    val absoluteInputPath: Path,
    val inputType: InputType,
    val inputPath: String,
    val targetId: String,
)

internal class WorkspaceInputResolver {
    fun resolveInitializationInput(rawInput: String): ResolvedInput {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            throw WorkspaceInitError(
                reason = WorkspaceInitErrorReason.InvalidInput,
                path = rawInput,
                message = "Initialization input is empty",
            )
        }
        val absoluteInput = Paths.get(trimmed).toAbsolutePath().normalize()
        if (!Files.exists(absoluteInput)) {
            throw WorkspaceInitError(
                reason = WorkspaceInitErrorReason.MissingInput,
                path = absoluteInput.toString(),
                message = "Initialization input does not exist: $absoluteInput",
            )
        }
        return when {
            Files.isDirectory(absoluteInput) ->
                throw WorkspaceInitError(
                    reason = WorkspaceInitErrorReason.InvalidInput,
                    path = absoluteInput.toString(),
                    message = "Initialization failed: directory input is not supported: $absoluteInput",
                )

            Files.isRegularFile(absoluteInput) -> {
                val workdirPath = absoluteInput.parent
                    ?: throw WorkspaceInitError(
                        reason = WorkspaceInitErrorReason.InvalidInput,
                        path = absoluteInput.toString(),
                        message = "Initialization input has no parent directory: $absoluteInput",
                    )
                val inputPath = normalizeRelativePath(workdirPath.relativize(absoluteInput))
                ResolvedInput(
                    ref = WorkspaceRef(workdirPath.toString()),
                    workdirPath = workdirPath,
                    absoluteInputPath = absoluteInput,
                    inputType = InputType.File,
                    inputPath = inputPath,
                    targetId = sha256Hex("file\u0000$inputPath".toByteArray(StandardCharsets.UTF_8)),
                )
            }

            else -> throw WorkspaceInitError(
                reason = WorkspaceInitErrorReason.InvalidInput,
                path = absoluteInput.toString(),
                message = "Initialization input is neither a file nor a directory: $absoluteInput",
            )
        }
    }

    fun resolveRuntimeWorkdir(ref: WorkspaceRef): Path {
        val workdirPath = Paths.get(ref.workdir).toAbsolutePath().normalize()
        if (!Files.isDirectory(workdirPath)) {
            throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.InvalidWorkdir,
                workdir = workdirPath.toString(),
                message = "Target workspace directory does not exist: $workdirPath",
            )
        }
        return workdirPath
    }

    fun resolveBoundInput(workdirPath: Path, inputPath: String, inputType: InputType): Path {
        val resolved = workdirPath.resolve(inputPath).normalize()
        if (inputType != InputType.File || !Files.isRegularFile(resolved)) {
            throw WorkspaceResolveError(
                reason = WorkspaceResolveErrorReason.MissingBoundInput,
                workdir = workdirPath.toString(),
                message = "Active target input is missing: $resolved",
            )
        }
        return resolved
    }

    fun normalizeRelativePath(path: Path): String {
        val normalized = path.normalize()
        val text = normalized.toString().replace('\\', '/')
        if (text.isEmpty() || text == ".") return "."
        if (text.startsWith("../") || text == ".." || text.contains("/../")) {
            throw WorkspaceInitError(
                reason = WorkspaceInitErrorReason.InvalidInputPath,
                path = text,
                message = "Workspace input path must stay within the workdir: $text",
            )
        }
        if (text == ".dexclub" || text.startsWith(".dexclub/")) {
            throw WorkspaceInitError(
                reason = WorkspaceInitErrorReason.InvalidInputPath,
                path = text,
                message = "Workspace input path cannot point into .dexclub: $text",
            )
        }
        return text
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { "%02x".format(it) }
