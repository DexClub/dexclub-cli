package io.github.dexclub.mcp

import io.github.dexclub.core.api.dex.ClassHit
import io.github.dexclub.core.api.dex.DexQueryError
import io.github.dexclub.core.api.dex.DexQueryErrorReason
import io.github.dexclub.core.api.dex.FieldHit
import io.github.dexclub.core.api.dex.MethodDetail
import io.github.dexclub.core.api.dex.MethodDetailSection
import io.github.dexclub.core.api.dex.MethodHit
import io.github.dexclub.core.api.shared.MethodSmaliMode
import io.github.dexclub.core.api.shared.SourceLocator
import io.github.dexclub.core.api.workspace.WorkspaceRef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class McpDexToolsTest {
    @Test
    fun findMethodsForwardsCompleteQueryAndAppliesWindow() {
        val workspace = fakeWorkspaceContext()
        val dexService = FakeDexAnalysisService(
            findMethodsResponse = listOf(
                MethodHit("Lsample/A;", "first", "Lsample/A;->first()V"),
                MethodHit("Lsample/A;", "second", "Lsample/A;->second()V"),
            ),
        )
        val app = createTestApp(workspace = workspace, dexService = dexService)
        val query = buildJsonObject {
            put("matcher", buildJsonObject {
                put("name", buildJsonObject { put("value", "second") })
            })
        }

        val result = app.findMethods(workspace, query, offset = 1, limit = 1)

        val forwarded = app.json.parseToJsonElement(dexService.lastFindMethodsRequest!!.queryText).jsonObject
        assertEquals("second", forwarded["matcher"]!!.jsonObject["name"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals(2, result.total)
        assertEquals("second", result.items.single().methodName)
        assertEquals(false, result.hasMore)
    }

    @Test
    fun findClassesAndFieldsUseTheirDedicatedUseCases() {
        val workspace = fakeWorkspaceContext()
        val dexService = FakeDexAnalysisService(
            findClassesResponse = listOf(ClassHit("Lsample/A;")),
            findFieldsResponse = listOf(FieldHit("Lsample/A;", "VALUE", "I")),
        )
        val app = createTestApp(workspace = workspace, dexService = dexService)
        val query = buildJsonObject { }

        val classes = app.findClasses(workspace, query)
        val fields = app.findFields(workspace, query)

        assertEquals("Lsample/A;", classes.items.single().className)
        assertEquals("VALUE", fields.items.single().fieldName)
        assertEquals("{}", dexService.lastFindClassesRequest!!.queryText)
        assertEquals("{}", dexService.lastFindFieldsRequest!!.queryText)
    }

    @Test
    fun findWindowDefaultsAndEnforcesMaximum() {
        val defaultRequest = callToolRequest("find_methods", buildJsonObject {})
        val tooLargeRequest = callToolRequest("find_methods", buildJsonObject { put("limit", 201) })

        assertEquals(0, defaultRequest.findOffset())
        assertEquals(50, defaultRequest.findLimit())
        assertEquals(
            "limit must be between 1 and 200",
            assertFailsWith<IllegalArgumentException> { tooLargeRequest.findLimit() }.message,
        )
    }

    @Test
    fun findWindowRejectsExplicitNonIntegerValues() {
        val stringOffset = callToolRequest("find_methods", buildJsonObject { put("offset", "1") })
        val decimalLimit = callToolRequest("find_methods", buildJsonObject { put("limit", 1.5) })

        assertEquals(
            "offset must be an integer",
            assertFailsWith<IllegalArgumentException> { stringOffset.findOffset() }.message,
        )
        assertEquals(
            "limit must be an integer",
            assertFailsWith<IllegalArgumentException> { decimalLimit.findLimit() }.message,
        )
    }

    @Test
    fun queryFileLoaderRejectsLegacyArguments() {
        val request = callToolRequest("find_methods", buildJsonObject { put("query", "{}") })

        val error = assertFailsWith<McpQueryFileException> {
            createTestApp().loadQueryFile(request, "find_methods")
        }
        assertEquals("query_contract_removed", error.code)
    }

    @Test
    fun queryFileLoaderReadsEnvelopeAndPreservesJsonStrings() {
        val file = createTempFile(suffix = ".query.json")
        file.writeText(
            """{"format":"dexclub-query","formatVersion":1,"kind":"find_methods","query":{"matcher":{"usingStrings":[{"value":"{\"event\":\"login\"}"}]}}}""",
        )

        val query = createTestApp().loadQueryFile(
            callToolRequest("find_methods", buildJsonObject { put("query_file", file.toAbsolutePath().toString()) }),
            "find_methods",
        )
        assertEquals("{\"event\":\"login\"}", query["matcher"]!!.jsonObject["usingStrings"]!!.jsonArray.single().jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun invalidDexQueryReturnsQueryErrorInsteadOfInternalError() {
        val app = createTestApp()

        val result = app.runToolCatching {
            throw DexQueryError(DexQueryErrorReason.InvalidQuery, "Invalid find-method query JSON")
        }

        assertEquals(true, result.isError)
        val payload = app.json.parseToJsonElement(
            (result.content.single() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text.orEmpty(),
        ).jsonObject
        assertEquals("invalid_query", payload["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(
            "Invalid find-method query JSON",
            payload["error"]!!.jsonObject["message"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun parseMethodDetailSectionsSupportsCliStyleNames() {
        assertEquals(
            MethodDetailSection.entries.toSet(),
            parseMethodDetailSections(listOf("using-fields", "callers", "invokes", "strings", "annotations")),
        )
    }

    @Test
    fun parseMethodDetailSectionsFallsBackToAllWhenMissing() {
        assertEquals(MethodDetailSection.entries.toSet(), parseMethodDetailSections(null))
    }

    @Test
    fun inspectMethodUsesSessionWorkspaceAndIncludes() {
        val workspace = fakeWorkspaceContext()
        val dexService = FakeDexAnalysisService(
            detail = MethodDetail(
                method = MethodHit(
                    className = "Lsample/Test;",
                    methodName = "foo",
                    descriptor = "Lsample/Test;->foo()V",
                    sourcePath = "sample.apk",
                    sourceEntry = "classes.dex",
                ),
                strings = listOf("alpha"),
                annotations = listOf("Lsample/Anno;"),
            ),
        )
        val app = createTestApp(workspace = workspace, dexService = dexService)
        val session = app.openTargetSession("sample.apk")

        val detail = app.inspectMethod(
            workspace = session.workspace,
            descriptor = "Lsample/Test;->foo()V",
            source = SourceLocator(sourcePath = "sample.apk", sourceEntry = "classes.dex"),
            includes = setOf(MethodDetailSection.Strings, MethodDetailSection.Annotations),
        )

        assertEquals(workspace, dexService.lastWorkspace)
        assertEquals("Lsample/Test;->foo()V", dexService.lastInspectRequest?.descriptor)
        assertEquals("sample.apk", dexService.lastInspectRequest?.source?.sourcePath)
        assertEquals("classes.dex", dexService.lastInspectRequest?.source?.sourceEntry)
        assertEquals(setOf(MethodDetailSection.Strings, MethodDetailSection.Annotations), dexService.lastInspectRequest?.includes)
        assertEquals(listOf("alpha"), detail.strings)
        assertEquals(listOf("Lsample/Anno;"), detail.annotations)
    }

    @Test
    fun inspectMethodExecutionRejectsMissingDescriptorWithoutCallingDexService() {
        val workspace = fakeWorkspaceContext()
        val workspaceService = FakeWorkspaceService(workspace)
        val dexService = FakeDexAnalysisService()
        val app = createTestApp(
            workspace = workspace,
            workspaceService = workspaceService,
            dexService = dexService,
        )

        val error = assertFailsWith<IllegalArgumentException> {
            app.inspectMethodExecution(
                sessionId = null,
                workdir = workspace.workdir,
                methodHandle = null,
                descriptor = null,
                sourcePath = null,
                sourceEntry = null,
                includes = emptySet(),
            )
        }

        assertEquals("method_handle or descriptor is required", error.message)
        assertEquals(WorkspaceRef(workspace.workdir), workspaceService.openedRef)
        assertNull(dexService.lastInspectRequest)
    }

    @Test
    fun exportMethodJavaTextUsesSessionWorkspaceAndReturnsFileContent() {
        val workspace = fakeWorkspaceContext()
        val dexService = FakeDexAnalysisService()
        val app = createTestApp(workspace = workspace, dexService = dexService)
        val session = app.openTargetSession("sample.apk")

        val text = app.exportMethodJavaText(
            workspace = session.workspace,
            descriptor = "Lsample/Test;->foo()V",
            source = SourceLocator(sourcePath = "sample.apk", sourceEntry = "classes.dex"),
        )

        assertEquals(workspace, dexService.lastWorkspace)
        assertEquals("Lsample/Test;->foo()V", dexService.lastExportMethodJavaRequest?.methodSignature)
        assertEquals(SourceLocator(sourcePath = "sample.apk", sourceEntry = "classes.dex"), dexService.lastExportMethodJavaRequest?.source)
        assertTrue(
            dexService.lastExportMethodJavaRequest!!.outputPath.replace('\\', '/')
                .contains("/.dexclub/targets/${workspace.activeTargetId}/cache/exports/tmp/"),
        )
        assertEquals("method-java:Lsample/Test;->foo()V", text)
    }

    @Test
    fun exportMethodSmaliTextSupportsClassMode() {
        val workspace = fakeWorkspaceContext()
        val dexService = FakeDexAnalysisService()
        val app = createTestApp(workspace = workspace, dexService = dexService)
        val session = app.openTargetSession("sample.apk")

        val text = app.exportMethodSmaliText(
            workspace = session.workspace,
            descriptor = "Lsample/Test;->foo()V",
            source = SourceLocator(sourcePath = "sample.apk", sourceEntry = "classes.dex"),
            mode = "class",
        )

        assertEquals(MethodSmaliMode.Class, dexService.lastExportMethodSmaliRequest?.mode)
        assertTrue(
            dexService.lastExportMethodSmaliRequest!!.outputPath.replace('\\', '/')
                .contains("/.dexclub/targets/${workspace.activeTargetId}/cache/exports/tmp/"),
        )
        assertEquals("method-smali:Lsample/Test;->foo()V:class", text)
    }

    @Test
    fun exportMethodSmaliTextExplainsSupportedModes() {
        val app = createTestApp()
        val session = app.openTargetSession("sample.apk")

        val error = assertFailsWith<IllegalArgumentException> {
            app.exportMethodSmaliText(
                workspace = session.workspace,
                descriptor = "Lsample/Test;->foo()V",
                mode = "full",
            )
        }

        assertEquals("Unsupported smali mode: full. Supported modes: snippet, class", error.message)
    }

    @Test
    fun exportClassJavaTextUsesSessionWorkspaceAndReturnsFileContent() {
        val workspace = fakeWorkspaceContext()
        val dexService = FakeDexAnalysisService()
        val app = createTestApp(workspace = workspace, dexService = dexService)
        val session = app.openTargetSession("sample.apk")

        val text = app.exportClassJavaText(
            workspace = session.workspace,
            descriptor = "Lsample/Test;",
            source = SourceLocator(sourcePath = "sample.apk", sourceEntry = "classes.dex"),
        )

        assertEquals("Lsample/Test;", dexService.lastExportClassJavaRequest?.className)
        assertTrue(
            dexService.lastExportClassJavaRequest!!.outputPath.replace('\\', '/')
                .contains("/.dexclub/targets/${workspace.activeTargetId}/cache/exports/tmp/"),
        )
        assertEquals("class-java:Lsample/Test;", text)
    }

    @Test
    fun exportClassSmaliTextUsesSessionWorkspaceAndReturnsFileContent() {
        val workspace = fakeWorkspaceContext()
        val dexService = FakeDexAnalysisService()
        val app = createTestApp(workspace = workspace, dexService = dexService)
        val session = app.openTargetSession("sample.apk")

        val text = app.exportClassSmaliText(
            workspace = session.workspace,
            descriptor = "Lsample/Test;",
            source = SourceLocator(sourcePath = "sample.apk", sourceEntry = "classes.dex"),
        )

        assertEquals("Lsample/Test;", dexService.lastExportClassSmaliRequest?.className)
        assertTrue(
            dexService.lastExportClassSmaliRequest!!.outputPath.replace('\\', '/')
                .contains("/.dexclub/targets/${workspace.activeTargetId}/cache/exports/tmp/"),
        )
        assertEquals("class-smali:Lsample/Test;", text)
    }
}
