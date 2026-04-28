package io.github.dexclub.core.api.dex

enum class DexQueryErrorReason {
    InvalidQuery,
}

class DexQueryError(
    val reason: DexQueryErrorReason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

enum class DexInspectErrorReason {
    InvalidMethodDescriptor,
    MethodNotFound,
    AmbiguousMethod,
}

class DexInspectError(
    val reason: DexInspectErrorReason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

enum class DexExportErrorReason {
    InvalidSourceLocator,
    InvalidMethodSignature,
    SourceNotFound,
    ClassNotFound,
    MethodNotFound,
    AmbiguousClass,
}

class DexExportError(
    val reason: DexExportErrorReason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
