package io.github.dexclub.core.api.resource

enum class ResourceDecodeErrorReason {
    ManifestSourceMissing,
    AmbiguousManifestSource,
    ManifestEntryMissing,
    ManifestTextInvalid,
    ManifestDecodeFailed,
    ResourceTableSourceMissing,
    AmbiguousResourceTableSource,
    ResourceTableEntryMissing,
    ResourceTableDecodeFailed,
    XmlPathNotFound,
    AmbiguousXmlPath,
    XmlDecodeFailed,
    ResourceValueInvalidSelector,
    ResourceValueNotFound,
    ResourceValueAmbiguous,
    ResourceQueryInvalid,
}

class ResourceDecodeError(
    val reason: ResourceDecodeErrorReason,
    val sourcePath: String? = null,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
