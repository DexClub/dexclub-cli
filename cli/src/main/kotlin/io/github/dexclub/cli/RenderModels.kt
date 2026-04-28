package io.github.dexclub.cli

import io.github.dexclub.core.api.dex.ClassHit
import io.github.dexclub.core.api.dex.ExportResult
import io.github.dexclub.core.api.dex.FieldHit
import io.github.dexclub.core.api.dex.MethodDetail
import io.github.dexclub.core.api.dex.MethodFieldUsage
import io.github.dexclub.core.api.dex.MethodHit
import io.github.dexclub.core.api.resource.DecodedXmlResult
import io.github.dexclub.core.api.resource.ManifestResult
import io.github.dexclub.core.api.resource.ResourceEntry
import io.github.dexclub.core.api.resource.ResourceEntryValueHit
import io.github.dexclub.core.api.resource.ResourceResolution
import io.github.dexclub.core.api.resource.ResourceTableResult
import io.github.dexclub.core.api.resource.ResourceValue
import io.github.dexclub.core.api.shared.CacheState
import io.github.dexclub.core.api.shared.CapabilitySet
import io.github.dexclub.core.api.shared.InputType
import io.github.dexclub.core.api.shared.WorkspaceIssueSeverity
import io.github.dexclub.core.api.shared.WorkspaceKind
import io.github.dexclub.core.api.shared.WorkspaceState
import io.github.dexclub.core.api.workspace.GcResult
import io.github.dexclub.core.api.workspace.InspectResult
import io.github.dexclub.core.api.workspace.WorkspaceStatus
import kotlinx.serialization.Serializable

internal sealed interface RenderPayload {
    data class Help(val text: String) : RenderPayload
    data class Version(val text: String) : RenderPayload
    data class Status(val view: StatusView) : RenderPayload
    data class Gc(val view: GcView) : RenderPayload
    data class Inspect(val view: InspectView) : RenderPayload
    data class Manifest(val view: ManifestView) : RenderPayload
    data class ResourceTable(val view: ResourceTableView) : RenderPayload
    data class DecodedXml(val view: DecodedXmlView) : RenderPayload
    data class ResourceEntries(val entries: List<ResourceEntryView>) : RenderPayload
    data class ResourceValue(val view: ResourceValueView) : RenderPayload
    data class ResourceValueHits(val hits: List<ResourceEntryValueHitView>) : RenderPayload
    data class ClassHits(val hits: List<ClassHitView>) : RenderPayload
    data class MethodHits(val hits: List<MethodHitView>) : RenderPayload
    data class MethodDetail(val view: MethodDetailView) : RenderPayload
    data class FieldHits(val hits: List<FieldHitView>) : RenderPayload
    data class Export(val view: ExportView) : RenderPayload
}

@Serializable
internal data class ManifestView(
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
    val text: String,
) {
    companion object {
        fun from(result: ManifestResult): ManifestView =
            ManifestView(
                sourcePath = result.sourcePath,
                sourceEntry = result.sourceEntry,
                text = result.text,
            )
    }
}

@Serializable
internal data class ResourceTableEntryView(
    val resourceId: String? = null,
    val type: String? = null,
    val name: String? = null,
    val filePath: String? = null,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
    val resolution: String,
) {
    companion object {
        fun from(entry: ResourceEntry): ResourceTableEntryView =
            ResourceTableEntryView(
                resourceId = entry.resourceId,
                type = entry.type,
                name = entry.name,
                filePath = entry.filePath,
                sourcePath = entry.sourcePath,
                sourceEntry = entry.sourceEntry,
                resolution = entry.resolution.toCliValue(),
            )
    }
}

@Serializable
internal data class ResourceEntryView(
    val resourceId: String? = null,
    val type: String? = null,
    val name: String? = null,
    val filePath: String? = null,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
    val resolution: String,
) {
    companion object {
        fun from(entry: io.github.dexclub.core.api.resource.ResourceEntry): ResourceEntryView =
            ResourceEntryView(
                resourceId = entry.resourceId,
                type = entry.type,
                name = entry.name,
                filePath = entry.filePath,
                sourcePath = entry.sourcePath,
                sourceEntry = entry.sourceEntry,
                resolution = entry.resolution.toCliValue(),
            )
    }
}

@Serializable
internal data class ResourceTableView(
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
    val packageCount: Int,
    val typeCount: Int,
    val entryCount: Int,
    val entries: List<ResourceTableEntryView> = emptyList(),
) {
    companion object {
        fun from(result: ResourceTableResult): ResourceTableView =
            ResourceTableView(
                sourcePath = result.sourcePath,
                sourceEntry = result.sourceEntry,
                packageCount = result.packageCount,
                typeCount = result.typeCount,
                entryCount = result.entryCount,
                entries = result.entries.map(ResourceTableEntryView::from),
            )
    }
}

@Serializable
internal data class DecodedXmlView(
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
    val text: String,
) {
    companion object {
        fun from(result: DecodedXmlResult): DecodedXmlView =
            DecodedXmlView(
                sourcePath = result.sourcePath,
                sourceEntry = result.sourceEntry,
                text = result.text,
            )
    }
}

@Serializable
internal data class ResourceValueView(
    val resourceId: String? = null,
    val type: String,
    val name: String,
    val value: String? = null,
) {
    companion object {
        fun from(result: ResourceValue): ResourceValueView =
            ResourceValueView(
                resourceId = result.resourceId,
                type = result.type,
                name = result.name,
                value = result.value,
            )
    }
}

@Serializable
internal data class ResourceEntryValueHitView(
    val resourceId: String? = null,
    val type: String? = null,
    val name: String? = null,
    val value: String? = null,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
) {
    companion object {
        fun from(hit: ResourceEntryValueHit): ResourceEntryValueHitView =
            ResourceEntryValueHitView(
                resourceId = hit.resourceId,
                type = hit.type,
                name = hit.name,
                value = hit.value,
                sourcePath = hit.sourcePath,
                sourceEntry = hit.sourceEntry,
            )
    }
}

@Serializable
internal data class IssueView(
    val severity: String,
    val code: String,
    val message: String,
)

@Serializable
internal data class StatusView(
    val workdir: String,
    val workspaceId: String,
    val activeTargetId: String,
    val state: String,
    val inputType: String,
    val inputPath: String,
    val kind: String? = null,
    val inventoryFingerprint: String? = null,
    val contentFingerprint: String? = null,
    val capabilities: List<String> = emptyList(),
    val cacheState: String,
    val issueCount: Int,
    val issues: List<IssueView> = emptyList(),
) {
    companion object {
        fun from(workdir: String, status: WorkspaceStatus): StatusView =
            StatusView(
                workdir = workdir,
                workspaceId = status.workspaceId,
                activeTargetId = status.activeTargetId,
                state = status.state.toCliValue(),
                inputType = status.activeTarget.inputType.toCliValue(),
                inputPath = status.activeTarget.inputPath,
                kind = status.snapshot?.kind?.toCliValue(),
                inventoryFingerprint = status.snapshot?.inventoryFingerprint,
                contentFingerprint = status.snapshot?.contentFingerprint,
                capabilities = status.snapshot?.capabilities?.enabledCapabilities().orEmpty(),
                cacheState = status.cacheState.toCliValue(),
                issueCount = status.issues.size,
                issues = status.issues.map {
                    IssueView(
                        severity = it.severity.toCliValue(),
                        code = it.code,
                        message = it.message,
                    )
                },
            )
    }
}

@Serializable
internal data class GcView(
    val workdir: String,
    val targetId: String,
    val deletedFiles: Int,
    val deletedBytes: Long,
) {
    companion object {
        fun from(result: GcResult): GcView =
            GcView(
                workdir = result.workdir,
                targetId = result.targetId,
                deletedFiles = result.deletedFiles,
                deletedBytes = result.deletedBytes,
            )
    }
}

@Serializable
internal data class InspectView(
    val kind: String,
    val inputType: String,
    val inputPath: String,
    val apkCount: Int,
    val dexCount: Int,
    val manifestCount: Int,
    val arscCount: Int,
    val binaryXmlCount: Int,
    val classCount: Int? = null,
    val capabilities: List<String> = emptyList(),
) {
    companion object {
        fun from(result: InspectResult): InspectView =
            InspectView(
                kind = result.snapshot.kind.toCliValue(),
                inputType = result.target.inputType.toCliValue(),
                inputPath = result.target.inputPath,
                apkCount = result.snapshot.inventoryCounts.apkCount,
                dexCount = result.snapshot.inventoryCounts.dexCount,
                manifestCount = result.snapshot.inventoryCounts.manifestCount,
                arscCount = result.snapshot.inventoryCounts.arscCount,
                binaryXmlCount = result.snapshot.inventoryCounts.binaryXmlCount,
                classCount = result.classCount,
                capabilities = result.snapshot.capabilities.enabledCapabilities(),
            )
    }
}

@Serializable
internal data class ClassHitView(
    val className: String,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
) {
    companion object {
        fun from(hit: ClassHit): ClassHitView =
            ClassHitView(
                className = hit.className,
                sourcePath = hit.sourcePath,
                sourceEntry = hit.sourceEntry,
            )
    }
}

@Serializable
internal data class MethodHitView(
    val className: String,
    val methodName: String,
    val descriptor: String,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
) {
    companion object {
        fun from(hit: MethodHit): MethodHitView =
            MethodHitView(
                className = hit.className,
                methodName = hit.methodName,
                descriptor = hit.descriptor,
                sourcePath = hit.sourcePath,
                sourceEntry = hit.sourceEntry,
            )
    }
}

@Serializable
internal data class MethodFieldUsageView(
    val usingType: String,
    val field: FieldHitView,
) {
    companion object {
        fun from(usage: MethodFieldUsage): MethodFieldUsageView =
            MethodFieldUsageView(
                usingType = usage.usingType.name,
                field = FieldHitView.from(usage.field),
            )
    }
}

@Serializable
internal data class MethodDetailView(
    val method: MethodHitView,
    val usingFields: List<MethodFieldUsageView>? = null,
    val callers: List<MethodHitView>? = null,
    val invokes: List<MethodHitView>? = null,
) {
    companion object {
        fun from(detail: MethodDetail): MethodDetailView =
            MethodDetailView(
                method = MethodHitView.from(detail.method),
                usingFields = detail.usingFields?.map(MethodFieldUsageView::from),
                callers = detail.callers?.map(MethodHitView::from),
                invokes = detail.invokes?.map(MethodHitView::from),
            )
    }
}

@Serializable
internal data class FieldHitView(
    val className: String,
    val fieldName: String,
    val descriptor: String,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
) {
    companion object {
        fun from(hit: FieldHit): FieldHitView =
            FieldHitView(
                className = hit.className,
                fieldName = hit.fieldName,
                descriptor = hit.descriptor,
                sourcePath = hit.sourcePath,
                sourceEntry = hit.sourceEntry,
            )
    }
}

@Serializable
internal data class ExportView(
    val outputPath: String,
) {
    companion object {
        fun from(result: ExportResult): ExportView =
            ExportView(outputPath = result.outputPath)
    }
}

private fun WorkspaceState.toCliValue(): String =
    when (this) {
        WorkspaceState.Healthy -> "healthy"
        WorkspaceState.Degraded -> "degraded"
        WorkspaceState.Broken -> "broken"
    }

private fun WorkspaceKind.toCliValue(): String =
    when (this) {
        WorkspaceKind.Apk -> "apk"
        WorkspaceKind.Dex -> "dex"
        WorkspaceKind.Manifest -> "manifest"
        WorkspaceKind.Arsc -> "arsc"
        WorkspaceKind.Axml -> "axml"
    }

private fun InputType.toCliValue(): String =
    when (this) {
        InputType.File -> "file"
    }

private fun CacheState.toCliValue(): String =
    when (this) {
        CacheState.Present -> "present"
        CacheState.Partial -> "partial"
        CacheState.Missing -> "missing"
    }

private fun WorkspaceIssueSeverity.toCliValue(): String =
    when (this) {
        WorkspaceIssueSeverity.Warning -> "warning"
        WorkspaceIssueSeverity.Error -> "error"
    }

private fun ResourceResolution.toCliValue(): String =
    when (this) {
        ResourceResolution.TableBacked -> "table-backed"
        ResourceResolution.PathInferred -> "path-inferred"
        ResourceResolution.Unresolved -> "unresolved"
    }

private fun CapabilitySet.enabledCapabilities(): List<String> =
    buildList {
        if (inspect) add("inspect")
        if (findClass) add("findClass")
        if (findMethod) add("findMethod")
        if (findField) add("findField")
        if (exportDex) add("exportDex")
        if (exportSmali) add("exportSmali")
        if (exportJava) add("exportJava")
        if (manifestDecode) add("manifestDecode")
        if (resourceTableDecode) add("resourceTableDecode")
        if (xmlDecode) add("xmlDecode")
        if (resourceEntryList) add("resourceEntryList")
    }
