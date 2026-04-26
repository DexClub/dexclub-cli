package io.github.dexclub.core.api.workspace

enum class WorkspaceInitErrorReason {
    InvalidInput,
    MissingInput,
    NoRecognizableMaterials,
    InvalidInputPath,
    StateWriteFailed,
}

class WorkspaceInitError(
    val reason: WorkspaceInitErrorReason,
    val path: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

enum class WorkspaceResolveErrorReason {
    InvalidWorkdir,
    NotInitialized,
    InvalidWorkspaceMetadata,
    MissingActiveTarget,
    InvalidTargetMetadata,
    MissingBoundInput,
    InvalidSnapshot,
    UnrecognizedMaterials,
}

class WorkspaceResolveError(
    val reason: WorkspaceResolveErrorReason,
    val workdir: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
