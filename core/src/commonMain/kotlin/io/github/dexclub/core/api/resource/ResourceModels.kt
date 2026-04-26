package io.github.dexclub.core.api.resource

import io.github.dexclub.core.api.shared.PageWindow

enum class ResourceResolution {
    TableBacked,
    PathInferred,
    Unresolved,
}

data class ManifestResult(
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
    val text: String,
)

data class ResourceEntry(
    val resourceId: String? = null,
    val type: String? = null,
    val name: String? = null,
    val filePath: String? = null,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
    val resolution: ResourceResolution = ResourceResolution.Unresolved,
)

data class ResourceTableResult(
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
    val packageCount: Int,
    val typeCount: Int,
    val entryCount: Int,
    val entries: List<ResourceEntry> = emptyList(),
)

data class DecodeXmlRequest(
    val path: String,
)

data class DecodedXmlResult(
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
    val text: String,
)

data class ResolveResourceRequest(
    val resourceId: String? = null,
    val type: String? = null,
    val name: String? = null,
)

data class ResourceValue(
    val resourceId: String? = null,
    val type: String,
    val name: String,
    val value: String? = null,
)

data class FindResourcesRequest(
    val queryText: String,
    val window: PageWindow = PageWindow(),
)

data class ResourceEntryValueHit(
    val resourceId: String? = null,
    val type: String? = null,
    val name: String? = null,
    val value: String? = null,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
)
