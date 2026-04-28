package io.github.dexclub.cli

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class Renderer {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun render(result: CommandResult): RenderedOutput =
        RenderedOutput(
            stdout = when (result.outputFormat) {
                OutputFormat.Text -> renderText(result.payload)
                OutputFormat.Json -> renderJson(result.payload)
            },
            stderr = null,
            exitCode = result.exitCode,
        )

    fun renderCliUsageError(error: CliUsageError): RenderedOutput =
        RenderedOutput(
            stdout = null,
            stderr = buildString {
                append("Error: ")
                append(error.message)
                if (error.hint != null) {
                    appendLine()
                    append(error.hint)
                } else {
                    appendLine()
                    appendLine()
                    appendLine("Usage:")
                    append("  ")
                    append(error.usage)
                }
            },
            exitCode = 1,
        )

    fun renderWorkspaceError(message: String, hint: String?): RenderedOutput =
        RenderedOutput(
            stdout = null,
            stderr = buildString {
                append("Error: ")
                append(message)
                if (hint != null) {
                    appendLine()
                    append(hint)
                }
            },
            exitCode = 2,
        )

    private fun renderText(payload: RenderPayload): String =
        when (payload) {
            is RenderPayload.Help -> payload.text
            is RenderPayload.Version -> payload.text
            is RenderPayload.Status -> renderStatus(payload.view)
            is RenderPayload.Gc -> renderGc(payload.view)
            is RenderPayload.Inspect -> renderInspect(payload.view)
            is RenderPayload.Manifest -> renderManifest(payload.view)
            is RenderPayload.ResourceTable -> renderResourceTable(payload.view)
            is RenderPayload.DecodedXml -> renderDecodedXml(payload.view)
            is RenderPayload.ResourceEntries -> renderResourceEntries(payload.entries)
            is RenderPayload.ResourceValue -> renderResourceValue(payload.view)
            is RenderPayload.ResourceValueHits -> renderResourceValueHits(payload.hits)
            is RenderPayload.ClassHits -> renderClassHits(payload.hits)
            is RenderPayload.MethodHits -> renderMethodHits(payload.hits)
            is RenderPayload.MethodDetail -> renderMethodDetail(payload.view)
            is RenderPayload.FieldHits -> renderFieldHits(payload.hits)
            is RenderPayload.Export -> renderExport(payload.view)
        }

    private fun renderJson(payload: RenderPayload): String =
        when (payload) {
            is RenderPayload.Help -> payload.text
            is RenderPayload.Version -> payload.text
            is RenderPayload.Status -> json.encodeToString(payload.view)
            is RenderPayload.Gc -> json.encodeToString(payload.view)
            is RenderPayload.Inspect -> json.encodeToString(payload.view)
            is RenderPayload.Manifest -> json.encodeToString(payload.view)
            is RenderPayload.ResourceTable -> json.encodeToString(payload.view)
            is RenderPayload.DecodedXml -> json.encodeToString(payload.view)
            is RenderPayload.ResourceEntries -> json.encodeToString(payload.entries)
            is RenderPayload.ResourceValue -> json.encodeToString(payload.view)
            is RenderPayload.ResourceValueHits -> json.encodeToString(payload.hits)
            is RenderPayload.ClassHits -> json.encodeToString(payload.hits)
            is RenderPayload.MethodHits -> json.encodeToString(payload.hits)
            is RenderPayload.MethodDetail -> json.encodeToString(payload.view)
            is RenderPayload.FieldHits -> json.encodeToString(payload.hits)
            is RenderPayload.Export -> json.encodeToString(payload.view)
        }

    private fun renderStatus(view: StatusView): String =
        buildString {
            appendLine("workdir=${view.workdir}")
            appendLine("state=${view.state}")
            appendLine("workspaceId=${view.workspaceId}")
            appendLine("activeTargetId=${view.activeTargetId}")
            appendLine("inputType=${view.inputType}")
            appendLine("inputPath=${view.inputPath}")
            if (view.kind != null) appendLine("kind=${view.kind}")
            if (view.inventoryFingerprint != null) appendLine("inventoryFingerprint=${view.inventoryFingerprint}")
            if (view.contentFingerprint != null) appendLine("contentFingerprint=${view.contentFingerprint}")
            appendLine("capabilities=${view.capabilities.joinToString(",")}")
            appendLine("cacheState=${view.cacheState}")
            append("issueCount=${view.issueCount}")
            view.issues.forEachIndexed { index, issue ->
                appendLine()
                append("issue[$index].severity=${issue.severity}")
                appendLine()
                append("issue[$index].code=${issue.code}")
                appendLine()
                append("issue[$index].message=${issue.message}")
            }
        }

    private fun renderGc(view: GcView): String =
        buildString {
            appendLine("workdir=${view.workdir}")
            appendLine("targetId=${view.targetId}")
            appendLine("deletedFiles=${view.deletedFiles}")
            append("deletedBytes=${view.deletedBytes}")
        }

    private fun renderInspect(view: InspectView): String =
        buildString {
            appendLine("kind=${view.kind}")
            appendLine("inputType=${view.inputType}")
            appendLine("inputPath=${view.inputPath}")
            appendLine("apkCount=${view.apkCount}")
            appendLine("dexCount=${view.dexCount}")
            appendLine("manifestCount=${view.manifestCount}")
            appendLine("arscCount=${view.arscCount}")
            appendLine("binaryXmlCount=${view.binaryXmlCount}")
            view.classCount?.let { appendLine("classCount=$it") }
            append("capabilities=${view.capabilities.joinToString(",")}")
        }

    private fun renderManifest(view: ManifestView): String = view.text

    private fun renderResourceTable(view: ResourceTableView): String =
        buildString {
            if (view.sourcePath != null) {
                appendLine("sourcePath=${view.sourcePath}")
            }
            if (view.sourceEntry != null) {
                appendLine("sourceEntry=${view.sourceEntry}")
            }
            appendLine("packageCount=${view.packageCount}")
            appendLine("typeCount=${view.typeCount}")
            append("entryCount=${view.entryCount}")
        }

    private fun renderDecodedXml(view: DecodedXmlView): String = view.text

    private fun renderResourceEntries(entries: List<ResourceEntryView>): String =
        buildString {
            append("resourceId\ttype\tname\tfilePath\tsourcePath\tsourceEntry\tresolution")
            entries.forEach { entry ->
                appendLine()
                append(entry.resourceId.orEmpty())
                append('\t')
                append(entry.type.orEmpty())
                append('\t')
                append(entry.name.orEmpty())
                append('\t')
                append(entry.filePath.orEmpty())
                append('\t')
                append(entry.sourcePath.orEmpty())
                append('\t')
                append(entry.sourceEntry.orEmpty())
                append('\t')
                append(entry.resolution)
            }
        }

    private fun renderResourceValue(view: ResourceValueView): String =
        buildString {
            if (view.resourceId != null) {
                appendLine("resourceId=${view.resourceId}")
            }
            appendLine("type=${view.type}")
            appendLine("name=${view.name}")
            append("value=${view.value.orEmpty()}")
        }

    private fun renderResourceValueHits(hits: List<ResourceEntryValueHitView>): String =
        buildString {
            append("resourceId\ttype\tname\tvalue\tsourcePath\tsourceEntry")
            hits.forEach { hit ->
                appendLine()
                append(hit.resourceId.orEmpty())
                append('\t')
                append(hit.type.orEmpty())
                append('\t')
                append(hit.name.orEmpty())
                append('\t')
                append(hit.value.orEmpty())
                append('\t')
                append(hit.sourcePath.orEmpty())
                append('\t')
                append(hit.sourceEntry.orEmpty())
            }
        }

    private fun renderClassHits(hits: List<ClassHitView>): String =
        buildString {
            append("className\tsourcePath\tsourceEntry")
            hits.forEach { hit ->
                appendLine()
                append(hit.className)
                append('\t')
                append(hit.sourcePath.orEmpty())
                append('\t')
                append(hit.sourceEntry.orEmpty())
            }
        }

    private fun renderMethodHits(hits: List<MethodHitView>): String =
        buildString {
            append("className\tmethodName\tdescriptor\tsourcePath\tsourceEntry")
            hits.forEach { hit ->
                appendLine()
                append(hit.className)
                append('\t')
                append(hit.methodName)
                append('\t')
                append(hit.descriptor)
                append('\t')
                append(hit.sourcePath.orEmpty())
                append('\t')
                append(hit.sourceEntry.orEmpty())
            }
        }

    private fun renderMethodDetail(view: MethodDetailView): String =
        buildString {
            appendLine("method.className=${view.method.className}")
            appendLine("method.methodName=${view.method.methodName}")
            appendLine("method.descriptor=${view.method.descriptor}")
            appendLine("method.sourcePath=${view.method.sourcePath.orEmpty()}")
            appendLine("method.sourceEntry=${view.method.sourceEntry.orEmpty()}")

            view.usingFields?.let { usages ->
                appendLine()
                appendLine("usingFields")
                append("usingType\tclassName\tfieldName\tdescriptor\tsourcePath\tsourceEntry")
                usages.forEach { usage ->
                    appendLine()
                    append(usage.usingType)
                    append('\t')
                    append(usage.field.className)
                    append('\t')
                    append(usage.field.fieldName)
                    append('\t')
                    append(usage.field.descriptor)
                    append('\t')
                    append(usage.field.sourcePath.orEmpty())
                    append('\t')
                    append(usage.field.sourceEntry.orEmpty())
                }
            }

            view.callers?.let { callers ->
                appendLine()
                appendLine()
                appendLine("callers")
                append("className\tmethodName\tdescriptor\tsourcePath\tsourceEntry")
                callers.forEach { caller ->
                    appendLine()
                    append(caller.className)
                    append('\t')
                    append(caller.methodName)
                    append('\t')
                    append(caller.descriptor)
                    append('\t')
                    append(caller.sourcePath.orEmpty())
                    append('\t')
                    append(caller.sourceEntry.orEmpty())
                }
            }

            view.invokes?.let { invokes ->
                appendLine()
                appendLine()
                appendLine("invokes")
                append("className\tmethodName\tdescriptor\tsourcePath\tsourceEntry")
                invokes.forEach { invoke ->
                    appendLine()
                    append(invoke.className)
                    append('\t')
                    append(invoke.methodName)
                    append('\t')
                    append(invoke.descriptor)
                    append('\t')
                    append(invoke.sourcePath.orEmpty())
                    append('\t')
                    append(invoke.sourceEntry.orEmpty())
                }
            }
        }

    private fun renderFieldHits(hits: List<FieldHitView>): String =
        buildString {
            append("className\tfieldName\tdescriptor\tsourcePath\tsourceEntry")
            hits.forEach { hit ->
                appendLine()
                append(hit.className)
                append('\t')
                append(hit.fieldName)
                append('\t')
                append(hit.descriptor)
                append('\t')
                append(hit.sourcePath.orEmpty())
                append('\t')
                append(hit.sourceEntry.orEmpty())
            }
        }

    private fun renderExport(view: ExportView): String = "output=${view.outputPath}"
}

internal data class RenderedOutput(
    val stdout: String?,
    val stderr: String?,
    val exitCode: Int,
)
