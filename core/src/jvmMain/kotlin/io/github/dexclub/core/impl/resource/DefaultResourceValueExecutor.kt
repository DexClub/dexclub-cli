package io.github.dexclub.core.impl.resource

import io.github.dexclub.core.api.resource.FindResourcesRequest
import io.github.dexclub.core.api.resource.ResolveResourceRequest
import io.github.dexclub.core.api.resource.ResourceDecodeError
import io.github.dexclub.core.api.resource.ResourceDecodeErrorReason
import io.github.dexclub.core.api.resource.ResourceEntryValueHit
import io.github.dexclub.core.api.resource.ResourceValue
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.core.impl.workspace.model.ResourceTableCacheRecord
import io.github.dexclub.core.impl.workspace.store.WorkspaceStore

internal class DefaultResourceValueExecutor(
    private val store: WorkspaceStore,
    private val tableLoader: ResourceTableLoader,
    private val queryParser: ResourceSearchQueryParser,
    private val toolVersion: String,
) : ResourceValueExecutor {
    override fun resolveResourceValue(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ResolveResourceRequest,
    ): ResourceValue {
        loadValidCache(workspace, inventory)?.let { cache ->
            return resolveFromCache(cache, request)
        }

        val loaded = tableLoader.load(workspace, inventory)
        val resource = when {
            request.resourceId != null -> {
                val resourceId = parseResourceId(request.resourceId)
                loaded.tableBlock.getResource(resourceId)
            }

            request.type != null && request.name != null -> {
                loaded.tableBlock.resources
                    .asSequence()
                    .filter { it.type == request.type && it.name == request.name }
                    .toList()
                    .let { matches ->
                        when (matches.size) {
                            0 -> null
                            1 -> matches.single()
                            else -> throw ResourceDecodeError(
                                reason = ResourceDecodeErrorReason.ResourceValueAmbiguous,
                                sourcePath = loaded.sourcePath,
                                message = "Resource is ambiguous: ${request.type}/${request.name}",
                            )
                        }
                    }
            }

            else -> throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.ResourceValueInvalidSelector,
                message = "Resolve resource request is missing a valid selector",
            )
        } ?: throw ResourceDecodeError(
            reason = ResourceDecodeErrorReason.ResourceValueNotFound,
            sourcePath = loaded.sourcePath,
            message = buildNotFoundMessage(request),
        )

        val entry = resource.any()
            ?: throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.ResourceValueNotFound,
                sourcePath = loaded.sourcePath,
                message = buildNotFoundMessage(request),
            )

        return ResourceValue(
            resourceId = resource.hexId,
            type = resource.type,
            name = resource.name,
            value = entry.toDisplayValue(),
        )
    }

    override fun findResourceEntries(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: FindResourcesRequest,
    ): List<ResourceEntryValueHit> {
        loadValidCache(workspace, inventory)?.let { cache ->
            return findFromCache(cache, request)
        }

        val loaded = tableLoader.load(workspace, inventory)
        val query = queryParser.parse(request.queryText)
        return loaded.tableBlock.resources
            .asSequence()
            .filter { resource -> resource.type == query.type }
            .mapNotNull { resource ->
                val entry = resource.any() ?: return@mapNotNull null
                val value = entry.toDisplayValue() ?: return@mapNotNull null
                if (!matches(value, query)) {
                    return@mapNotNull null
                }
                ResourceEntryValueHit(
                    resourceId = resource.hexId,
                    type = resource.type,
                    name = resource.name,
                    value = value,
                    sourcePath = loaded.sourcePath,
                    sourceEntry = loaded.sourceEntry,
                )
            }
            .toList()
    }

    private fun loadValidCache(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
    ): ResourceTableCacheRecord? {
        val source = loaderOrNull(workspace, inventory) ?: return null
        val sourceFingerprint = resourceSourceFingerprint(workspace.workdir, source.sourcePath)
        return store.loadResourceTableCache(workspace.workdir, workspace.activeTargetId)
            ?.takeIf {
                it.toolVersion == toolVersion &&
                it.sourcePath == source.sourcePath &&
                    it.sourceEntry == source.sourceEntry &&
                    it.sourceFingerprint == sourceFingerprint
            }
    }

    private fun loaderOrNull(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
    ): ResourceTableSource? =
        try {
            tableLoader.resolveSource(workspace, inventory)
        } catch (_: ResourceDecodeError) {
            null
        }

    private fun resolveFromCache(
        cache: ResourceTableCacheRecord,
        request: ResolveResourceRequest,
    ): ResourceValue {
        val valueRecord = when {
            request.resourceId != null -> {
                val normalizedResourceId = normalizeResourceId(request.resourceId)
                cache.payload.values.firstOrNull { it.resourceId.equals(normalizedResourceId, ignoreCase = true) }
            }

            request.type != null && request.name != null -> {
                cache.payload.values
                    .filter { it.type == request.type && it.name == request.name }
                    .let { matches ->
                        when (matches.size) {
                            0 -> null
                            1 -> matches.single()
                            else -> throw ResourceDecodeError(
                                reason = ResourceDecodeErrorReason.ResourceValueAmbiguous,
                                sourcePath = cache.sourcePath,
                                message = "Resource is ambiguous: ${request.type}/${request.name}",
                            )
                        }
                    }
            }

            else -> throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.ResourceValueInvalidSelector,
                message = "Resolve resource request is missing a valid selector",
            )
        } ?: throw ResourceDecodeError(
            reason = ResourceDecodeErrorReason.ResourceValueNotFound,
            sourcePath = cache.sourcePath,
            message = buildNotFoundMessage(request),
        )

        return ResourceValue(
            resourceId = valueRecord.resourceId,
            type = valueRecord.type ?: request.type.orEmpty(),
            name = valueRecord.name ?: request.name.orEmpty(),
            value = valueRecord.value,
        )
    }

    private fun findFromCache(
        cache: ResourceTableCacheRecord,
        request: FindResourcesRequest,
    ): List<ResourceEntryValueHit> {
        val query = queryParser.parse(request.queryText)
        return cache.payload.values
            .asSequence()
            .filter { it.type == query.type }
            .filter { it.value != null }
            .filter { matches(it.value!!, query) }
            .map { valueRecord ->
                ResourceEntryValueHit(
                    resourceId = valueRecord.resourceId,
                    type = valueRecord.type,
                    name = valueRecord.name,
                    value = valueRecord.value,
                    sourcePath = cache.sourcePath,
                    sourceEntry = cache.sourceEntry,
                )
            }
            .toList()
    }

    private fun parseResourceId(text: String): Int =
        runCatching {
            text.removePrefix("0x").toInt(16)
        }.getOrElse {
            throw ResourceDecodeError(
                reason = ResourceDecodeErrorReason.ResourceValueInvalidSelector,
                message = "Invalid resource id: $text",
            )
        }

    private fun normalizeResourceId(text: String): String =
        buildString {
            append("0x")
            append(parseResourceId(text).toUInt().toString(16).padStart(8, '0'))
        }

    private fun buildNotFoundMessage(request: ResolveResourceRequest): String =
        when {
            request.resourceId != null -> "Resource not found: ${request.resourceId}"
            request.type != null && request.name != null -> "Resource not found: ${request.type}/${request.name}"
            else -> "Resource not found"
        }

    private fun matches(value: String, query: ResourceSearchQuery): Boolean =
        if (query.contains) {
            value.contains(query.value, ignoreCase = query.ignoreCase)
        } else {
            value.equals(query.value, ignoreCase = query.ignoreCase)
        }
}
