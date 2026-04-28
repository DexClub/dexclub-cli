package io.github.dexclub.core.impl.dex

import io.github.dexclub.core.api.dex.FindClassesRequest
import io.github.dexclub.core.api.dex.FindClassesUsingStringsRequest
import io.github.dexclub.core.api.dex.FindFieldsRequest
import io.github.dexclub.core.api.dex.FindMethodsRequest
import io.github.dexclub.core.api.dex.FindMethodsUsingStringsRequest
import io.github.dexclub.core.api.dex.ExportClassSmaliRequest
import io.github.dexclub.core.api.dex.ExportClassDexRequest
import io.github.dexclub.core.api.dex.ExportClassJavaRequest
import io.github.dexclub.core.api.dex.DexExportError
import io.github.dexclub.core.api.shared.SourceLocator
import io.github.dexclub.core.api.shared.MethodSmaliMode
import io.github.dexclub.core.api.shared.CapabilityError
import io.github.dexclub.core.api.shared.Operation
import io.github.dexclub.core.api.shared.PageWindow
import io.github.dexclub.core.api.shared.createDefaultServices
import io.github.dexclub.core.api.workspace.WorkspaceRef
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultDexAnalysisServiceTest {
    @Test
    fun findClassSortsBeforeApplyingWindow() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))

        val hits = services.dex.findClasses(
            workspace = workspace,
            request = FindClassesRequest(
                queryText = QUERY_SEARCH_TARGETS,
                window = PageWindow(offset = 1, limit = 1),
            ),
        )

        assertEquals(1, hits.size)
        assertEquals("Lfixture/samples/SampleSearchTarget;", hits.single().className)
        assertEquals("fixture.dex", hits.single().sourcePath)
        assertEquals(null, hits.single().sourceEntry)
    }

    @Test
    fun findClassOnApkReportsSourceEntry() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.apkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.apkWorkspaceDir.absolutePath))

        val hits = services.dex.findClasses(
            workspace = workspace,
            request = FindClassesRequest(queryText = QUERY_SAMPLE_CLASS),
        )

        assertTrue(hits.any { it.sourcePath == "fixture.apk" && it.sourceEntry == "classes.dex" })
    }

    @Test
    fun findMethodSortsBeforeApplyingWindow() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))

        val hits = services.dex.findMethods(
            workspace = workspace,
            request = FindMethodsRequest(
                queryText = QUERY_EXPOSE_METHOD,
                window = PageWindow(offset = 1, limit = 1),
            ),
        )

        assertEquals(1, hits.size)
        assertEquals("fixture.samples.SampleSearchTarget", hits.single().className)
        assertEquals("exposeNeedle", hits.single().methodName)
        assertEquals("fixture.dex", hits.single().sourcePath)
    }

    @Test
    fun findMethodOnApkReportsSourceEntry() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.apkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.apkWorkspaceDir.absolutePath))

        val hits = services.dex.findMethods(
            workspace = workspace,
            request = FindMethodsRequest(queryText = QUERY_EXPOSE_METHOD),
        )

        assertTrue(
            hits.any {
                it.methodName == "exposeNeedle" &&
                    it.sourcePath == "fixture.apk" &&
                    it.sourceEntry == "classes.dex"
            },
        )
    }

    @Test
    fun findFieldSortsBeforeApplyingWindow() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))

        val hits = services.dex.findFields(
            workspace = workspace,
            request = FindFieldsRequest(
                queryText = QUERY_NEEDLE_FIELD,
                window = PageWindow(offset = 1, limit = 1),
            ),
        )

        assertEquals(1, hits.size)
        assertEquals("fixture.samples.SampleSearchTarget", hits.single().className)
        assertEquals("NEEDLE", hits.single().fieldName)
        assertEquals("fixture.dex", hits.single().sourcePath)
    }

    @Test
    fun findFieldOnApkReportsSourceEntry() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.apkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.apkWorkspaceDir.absolutePath))

        val hits = services.dex.findFields(
            workspace = workspace,
            request = FindFieldsRequest(queryText = QUERY_NEEDLE_FIELD),
        )

        assertTrue(
            hits.any {
                it.fieldName == "NEEDLE" &&
                    it.sourcePath == "fixture.apk" &&
                    it.sourceEntry == "classes.dex"
            },
        )
    }

    @Test
    fun findClassUsingStringsDeduplicatesAcrossGroupsBeforeWindow() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))

        val hits = services.dex.findClassesUsingStrings(
            workspace = workspace,
            request = FindClassesUsingStringsRequest(
                queryText = QUERY_CLASS_USING_STRINGS_DUP_GROUPS,
                window = PageWindow(offset = 1, limit = 1),
            ),
        )

        assertEquals(1, hits.size)
        assertEquals("Lfixture/samples/SampleSearchTarget;", hits.single().className)
        assertEquals("fixture.dex", hits.single().sourcePath)
    }

    @Test
    fun findClassUsingStringsOnApkReportsSourceEntry() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.apkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.apkWorkspaceDir.absolutePath))

        val hits = services.dex.findClassesUsingStrings(
            workspace = workspace,
            request = FindClassesUsingStringsRequest(queryText = QUERY_CLASS_USING_STRINGS_SINGLE_GROUP),
        )

        assertTrue(hits.any { it.sourcePath == "fixture.apk" && it.sourceEntry == "classes.dex" })
    }

    @Test
    fun findMethodUsingStringsDeduplicatesAcrossGroupsBeforeWindow() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))

        val hits = services.dex.findMethodsUsingStrings(
            workspace = workspace,
            request = FindMethodsUsingStringsRequest(
                queryText = QUERY_METHOD_USING_STRINGS_DUP_GROUPS,
                window = PageWindow(offset = 1, limit = 1),
            ),
        )

        assertEquals(1, hits.size)
        assertEquals("fixture.samples.SampleSearchTarget", hits.single().className)
        assertEquals("exposeNeedle", hits.single().methodName)
        assertEquals("fixture.dex", hits.single().sourcePath)
    }

    @Test
    fun findMethodUsingStringsOnApkReportsSourceEntry() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.apkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.apkWorkspaceDir.absolutePath))

        val hits = services.dex.findMethodsUsingStrings(
            workspace = workspace,
            request = FindMethodsUsingStringsRequest(queryText = QUERY_METHOD_USING_STRINGS_SINGLE_GROUP),
        )

        assertTrue(
            hits.any {
                it.methodName == "exposeNeedle" &&
                    it.sourcePath == "fixture.apk" &&
                    it.sourceEntry == "classes.dex"
            },
        )
    }

    @Test
    fun findClassRequiresDexCapability() {
        val workdir = Files.createTempDirectory("dexclub-core-manifest").toFile()
        val manifest = workdir.toPath().resolve("AndroidManifest.xml")
        manifest.writeText("<manifest package=\"fixture.samples\"/>")
        val services = createDefaultServices()
        services.workspace.initialize(manifest.toString())
        val workspace = services.workspace.open(WorkspaceRef(workdir.absolutePath))

        val error = assertFailsWith<CapabilityError> {
            services.dex.findClasses(
                workspace = workspace,
                request = FindClassesRequest(queryText = QUERY_SAMPLE_CLASS),
            )
        }

        assertEquals(Operation.FindClass, error.operation)
        assertEquals("findClass", error.requiredCapability)
        assertEquals("manifest", error.kind)
    }

    @Test
    fun exportClassSmaliWritesSmaliForUniqueClass() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))
        val output = File(fixture.dexWorkspaceDir, "SampleSearchTarget.smali")

        val result = services.dex.exportClassSmali(
            workspace = workspace,
            request = ExportClassSmaliRequest(
                className = "fixture.samples.SampleSearchTarget",
                outputPath = output.absolutePath,
            ),
        )

        assertEquals(output.absolutePath, result.outputPath)
        assertTrue(output.isFile)
        val text = output.readText()
        assertTrue(text.contains("Lfixture/samples/SampleSearchTarget;"))
        assertTrue(text.contains(".method public exposeNeedle()Ljava/lang/String;"))
    }

    @Test
    fun exportClassSmaliRequiresSourceWhenClassIsAmbiguous() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.ambiguousApkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.ambiguousWorkspaceDir.absolutePath))

        val error = assertFailsWith<DexExportError> {
            services.dex.exportClassSmali(
                workspace = workspace,
                request = ExportClassSmaliRequest(
                    className = "fixture.samples.SampleSearchTarget",
                    outputPath = File(fixture.ambiguousWorkspaceDir, "SampleSearchTarget.smali").absolutePath,
                ),
            )
        }

        assertEquals(io.github.dexclub.core.api.dex.DexExportErrorReason.AmbiguousClass, error.reason)
    }

    @Test
    fun exportClassSmaliCanBeNarrowedBySourcePath() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.ambiguousApkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.ambiguousWorkspaceDir.absolutePath))
        val output = File(fixture.ambiguousWorkspaceDir, "SampleSearchTarget-from-second.smali")

        val result = services.dex.exportClassSmali(
            workspace = workspace,
            request = ExportClassSmaliRequest(
                className = "fixture.samples.SampleSearchTarget",
                source = SourceLocator(sourcePath = "fixture.apk", sourceEntry = "classes2.dex"),
                outputPath = output.absolutePath,
            ),
        )

        assertEquals(output.absolutePath, result.outputPath)
        assertTrue(output.readText().contains("Lfixture/samples/SampleSearchTarget;"))
    }

    @Test
    fun exportMethodSmaliWritesSnippetByDefault() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))
        val output = File(fixture.dexWorkspaceDir, "exposeNeedle.snippet.smali")

        val result = services.dex.exportMethodSmali(
            workspace = workspace,
            request = io.github.dexclub.core.api.dex.ExportMethodSmaliRequest(
                methodSignature = "Lfixture/samples/SampleSearchTarget;->exposeNeedle()Ljava/lang/String;",
                outputPath = output.absolutePath,
            ),
        )

        assertEquals(output.absolutePath, result.outputPath)
        val text = output.readText()
        assertTrue(text.startsWith(".method public exposeNeedle()Ljava/lang/String;"))
        assertTrue(!text.contains(".class "))
        assertTrue(!text.contains(".field "))
        assertTrue(!text.contains("callExposeNeedle"))
        assertTrue(text.contains("return-object"))
    }

    @Test
    fun exportMethodSmaliClassModeBuildsMinimalShell() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))
        val output = File(fixture.dexWorkspaceDir, "exposeNeedle.class.smali")

        val result = services.dex.exportMethodSmali(
            workspace = workspace,
            request = io.github.dexclub.core.api.dex.ExportMethodSmaliRequest(
                methodSignature = "Lfixture/samples/SampleSearchTarget;->exposeNeedle()Ljava/lang/String;",
                outputPath = output.absolutePath,
                mode = MethodSmaliMode.Class,
            ),
        )

        assertEquals(output.absolutePath, result.outputPath)
        val text = output.readText()
        assertTrue(text.contains(".class public Lfixture/samples/SampleSearchTarget;"))
        assertTrue(text.contains(".method public exposeNeedle()Ljava/lang/String;"))
        assertTrue(!text.contains(".field public static final NEEDLE:"))
        assertTrue(!text.contains(".method public constructor <init>()V"))
        assertTrue(!text.contains(".method public callExposeNeedle()Ljava/lang/String;"))
    }

    @Test
    fun exportMethodSmaliRequiresSourceWhenMethodClassIsAmbiguous() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.ambiguousApkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.ambiguousWorkspaceDir.absolutePath))

        val error = assertFailsWith<DexExportError> {
            services.dex.exportMethodSmali(
                workspace = workspace,
                request = io.github.dexclub.core.api.dex.ExportMethodSmaliRequest(
                    methodSignature = "Lfixture/samples/SampleSearchTarget;->exposeNeedle()Ljava/lang/String;",
                    outputPath = File(fixture.ambiguousWorkspaceDir, "exposeNeedle.smali").absolutePath,
                ),
            )
        }

        assertEquals(io.github.dexclub.core.api.dex.DexExportErrorReason.AmbiguousClass, error.reason)
    }

    @Test
    fun exportClassDexWritesSingleClassDexForUniqueClass() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))
        val output = File(fixture.dexWorkspaceDir, "SampleSearchTarget.dex")

        val result = services.dex.exportClassDex(
            workspace = workspace,
            request = ExportClassDexRequest(
                className = "fixture.samples.SampleSearchTarget",
                outputPath = output.absolutePath,
            ),
        )

        assertEquals(output.absolutePath, result.outputPath)
        assertTrue(output.isFile)
        assertTrue(isDexFile(output))
    }

    @Test
    fun exportClassDexRequiresSourceWhenClassIsAmbiguous() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.ambiguousApkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.ambiguousWorkspaceDir.absolutePath))

        val error = assertFailsWith<DexExportError> {
            services.dex.exportClassDex(
                workspace = workspace,
                request = ExportClassDexRequest(
                    className = "fixture.samples.SampleSearchTarget",
                    outputPath = File(fixture.ambiguousWorkspaceDir, "SampleSearchTarget.dex").absolutePath,
                ),
            )
        }

        assertEquals(io.github.dexclub.core.api.dex.DexExportErrorReason.AmbiguousClass, error.reason)
    }

    @Test
    fun exportClassJavaWritesSingleClassJavaForUniqueClass() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))
        val output = File(fixture.dexWorkspaceDir, "SampleSearchTarget.java")

        val result = services.dex.exportClassJava(
            workspace = workspace,
            request = ExportClassJavaRequest(
                className = "fixture.samples.SampleSearchTarget",
                outputPath = output.absolutePath,
            ),
        )

        assertEquals(output.absolutePath, result.outputPath)
        assertTrue(output.isFile)
        val text = output.readText()
        assertTrue(text.contains("class SampleSearchTarget"))
        assertTrue(text.contains("dexclub-needle-string"))
    }

    @Test
    fun exportClassJavaRequiresSourceWhenClassIsAmbiguous() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.ambiguousApkFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.ambiguousWorkspaceDir.absolutePath))

        val error = assertFailsWith<DexExportError> {
            services.dex.exportClassJava(
                workspace = workspace,
                request = ExportClassJavaRequest(
                    className = "fixture.samples.SampleSearchTarget",
                    outputPath = File(fixture.ambiguousWorkspaceDir, "SampleSearchTarget.java").absolutePath,
                ),
            )
        }

        assertEquals(io.github.dexclub.core.api.dex.DexExportErrorReason.AmbiguousClass, error.reason)
    }

    @Test
    fun exportCreatesClassSourceMapWhenMissing() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))
        val indexFile = File(fixture.dexWorkspaceDir, ".dexclub/targets/${workspace.activeTargetId}/cache/indexes/class-source-map.json")
        assertTrue(!indexFile.exists())

        services.dex.exportClassSmali(
            workspace = workspace,
            request = ExportClassSmaliRequest(
                className = "fixture.samples.SampleSearchTarget",
                outputPath = File(fixture.dexWorkspaceDir, "SampleSearchTarget.smali").absolutePath,
            ),
        )

        assertTrue(indexFile.isFile)
        val root = Json.parseToJsonElement(indexFile.readText()).jsonObject
        assertEquals(workspace.snapshot.contentFingerprint, root.getValue("contentFingerprint").jsonPrimitive.content)
        val mappings = root.getValue("mappings").jsonObject
        assertEquals("fixture.dex", mappings.getValue("Lfixture/samples/SampleSearchTarget;").jsonPrimitive.content)
    }

    @Test
    fun exportUsesValidClassSourceMapWithoutRescanningOtherSources() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))
        val indexFile = File(fixture.dexWorkspaceDir, ".dexclub/targets/${workspace.activeTargetId}/cache/indexes/class-source-map.json")

        services.dex.exportClassSmali(
            workspace = workspace,
            request = ExportClassSmaliRequest(
                className = "fixture.samples.SampleSearchTarget",
                outputPath = File(fixture.dexWorkspaceDir, "first-export.smali").absolutePath,
            ),
        )
        assertTrue(indexFile.isFile)

        File(fixture.dexWorkspaceDir, "ignored.bin").writeText("broken", Charsets.UTF_8)
        val indexedExport = File(fixture.dexWorkspaceDir, "second-export.smali")
        services.dex.exportClassSmali(
            workspace = workspace,
            request = ExportClassSmaliRequest(
                className = "fixture.samples.SampleSearchTarget",
                outputPath = indexedExport.absolutePath,
            ),
        )
        assertTrue(indexedExport.readText().contains("Lfixture/samples/SampleSearchTarget;"))
    }

    @Test
    fun exportRebuildsStaleClassSourceMap() {
        val fixture = DexAnalysisFixture.generated()
        val services = createDefaultServices()
        services.workspace.initialize(fixture.dexFile.absolutePath)
        val workspace = services.workspace.open(WorkspaceRef(fixture.dexWorkspaceDir.absolutePath))
        val indexFile = File(fixture.dexWorkspaceDir, ".dexclub/targets/${workspace.activeTargetId}/cache/indexes/class-source-map.json")

        services.dex.exportClassSmali(
            workspace = workspace,
            request = ExportClassSmaliRequest(
                className = "fixture.samples.SampleSearchTarget",
                outputPath = File(fixture.dexWorkspaceDir, "first-export.smali").absolutePath,
            ),
        )

        val staleIndex = """
            {
              "schemaVersion": 1,
              "generatedAt": "2026-04-25T12:26:00Z",
              "targetId": "${workspace.activeTargetId}",
              "toolVersion": "test",
              "contentFingerprint": "stale-fingerprint",
              "format": "class-source-map-v1",
              "mappings": {
                "Lfixture/samples/SampleSearchTarget;": "fixture.dex"
              }
            }
        """.trimIndent()
        indexFile.writeText(staleIndex, Charsets.UTF_8)

        val rebuiltExport = File(fixture.dexWorkspaceDir, "third-export.smali")
        services.dex.exportClassSmali(
            workspace = workspace,
            request = ExportClassSmaliRequest(
                className = "fixture.samples.SampleSearchTarget",
                outputPath = rebuiltExport.absolutePath,
            ),
        )

        val rebuilt = Json.parseToJsonElement(indexFile.readText()).jsonObject
        assertEquals(workspace.snapshot.contentFingerprint, rebuilt.getValue("contentFingerprint").jsonPrimitive.content)
        val mappings = rebuilt.getValue("mappings").jsonObject
        assertEquals("fixture.dex", mappings["Lfixture/samples/SampleSearchTarget;"]?.jsonPrimitive?.content)
        assertNotNull(mappings["Lfixture/samples/AnotherSearchTarget;"])
    }
}

private fun isDexFile(file: File): Boolean {
    val header = ByteArray(8)
    file.inputStream().use { input ->
        val read = input.read(header)
        if (read < header.size) return false
    }
    return header.decodeToString() == "dex\n035\u0000"
}

private class DexAnalysisFixture(
    val dexWorkspaceDir: File,
    val dexFile: File,
    val ambiguousWorkspaceDir: File,
    val ambiguousApkFile: File,
    val apkWorkspaceDir: File,
    val apkFile: File,
) {
    companion object {
        fun generated(): DexAnalysisFixture {
            val root = Files.createTempDirectory("dexclub-core-dex-fixture").toFile()
            val sampleClasses = compileJava(
                root = root,
                fileName = "SampleSearchTarget.java",
                source = """
                    package fixture.samples;
                    public class SampleSearchTarget {
                        public static final String NEEDLE = "dexclub-needle-string";
                        public String exposeNeedle() {
                            return NEEDLE;
                        }
                    }
                """.trimIndent(),
            )
            val anotherClasses = compileJava(
                root = root,
                fileName = "AnotherSearchTarget.java",
                source = """
                    package fixture.samples;
                    public class AnotherSearchTarget {
                        public static final String NEEDLE = "dexclub-needle-string";
                        public String exposeNeedle() {
                            return NEEDLE;
                        }
                    }
                """.trimIndent(),
            )

            val sampleDex = compileDex(root, "sample", sampleClasses)
            val duplicateSampleDex = compileDex(root, "duplicate", sampleClasses)

            val dexWorkspaceDir = File(root, "dex-input").also(File::mkdirs)
            val dexFile = compileDex(root, "fixture", sampleClasses, anotherClasses)
                .copyTo(File(dexWorkspaceDir, "fixture.dex"), overwrite = true)

            val ambiguousWorkspaceDir = File(root, "ambiguous-dex-input").also(File::mkdirs)
            val ambiguousApkFile = File(ambiguousWorkspaceDir, "fixture.apk")
            createPseudoApk(
                outputApk = ambiguousApkFile,
                "classes.dex" to sampleDex,
                "classes2.dex" to duplicateSampleDex,
            )

            val apkWorkspaceDir = File(root, "apk-input").also(File::mkdirs)
            val apkFile = File(apkWorkspaceDir, "fixture.apk")
            createPseudoApk(apkFile, "classes.dex" to sampleDex)

            return DexAnalysisFixture(
                dexWorkspaceDir = dexWorkspaceDir,
                dexFile = dexFile,
                ambiguousWorkspaceDir = ambiguousWorkspaceDir,
                ambiguousApkFile = ambiguousApkFile,
                apkWorkspaceDir = apkWorkspaceDir,
                apkFile = apkFile,
            )
        }

        private fun compileJava(root: File, fileName: String, source: String): File {
            val sourceDir = File(root, "src-$fileName/fixture/samples").also(File::mkdirs)
            val sourceFile = File(sourceDir, fileName).apply {
                writeText(source, Charsets.UTF_8)
            }
            val classesDir = File(root, "classes-$fileName").also(File::mkdirs)
            runCommand(
                command = listOf(
                    "javac",
                    "--release",
                    "8",
                    "-d",
                    classesDir.absolutePath,
                    sourceFile.absolutePath,
                ),
                workingDirectory = root,
            )
            return classesDir
        }

        private fun compileDex(root: File, name: String, vararg classesDirs: File): File {
            val dexDir = File(root, "dex-$name").also(File::mkdirs)
            val classFiles = classesDirs.asSequence()
                .flatMap { classesDir ->
                    classesDir.walkTopDown()
                        .asSequence()
                        .filter { it.isFile && it.extension == "class" }
                        .map { it.absolutePath }
                }
                .toList()
            runCommand(
                command = buildList {
                    add(resolveD8Command())
                    add("--min-api")
                    add("21")
                    add("--output")
                    add(dexDir.absolutePath)
                    addAll(classFiles)
                },
                workingDirectory = root,
            )
            return File(dexDir, "classes.dex")
        }

        private fun resolveD8Command(): String {
            val envRoots = listOfNotNull(
                System.getenv("ANDROID_SDK_ROOT"),
                System.getenv("ANDROID_HOME"),
            ).map(::File)
            val candidates = buildList {
                envRoots.forEach { root ->
                    root.resolve("build-tools").listFiles()
                        ?.sortedByDescending(File::getName)
                        ?.forEach { buildToolsDir ->
                            add(buildToolsDir.resolve("d8").path)
                            add(buildToolsDir.resolve("d8.bat").path)
                        }
                }
                add("d8")
                add("d8.bat")
            }
            return candidates.firstOrNull { candidate ->
                runCatching {
                    val process = ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start()
                    process.inputStream.bufferedReader().use { it.readText() }
                    process.waitFor() == 0
                }.getOrDefault(false)
            } ?: error("未找到可用的 d8 命令")
        }

        private fun runCommand(command: List<String>, workingDirectory: File) {
            val process = ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0) {
                buildString {
                    appendLine("命令执行失败: ${command.joinToString(" ")}")
                    append(output)
                }
            }
        }

        private fun createPseudoApk(outputApk: File, vararg entries: Pair<String, File>) {
            ZipOutputStream(outputApk.outputStream().buffered()).use { zip ->
                entries.forEach { (name, sourceDex) ->
                    zip.putNextEntry(ZipEntry(name))
                    sourceDex.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
}

private const val QUERY_SEARCH_TARGETS =
    """{"matcher":{"className":{"value":"SearchTarget","matchType":"Contains","ignoreCase":true}}}"""

private const val QUERY_SAMPLE_CLASS =
    """{"matcher":{"className":{"value":"fixture.samples.SampleSearchTarget","matchType":"Equals"}}}"""

private const val QUERY_EXPOSE_METHOD =
    """{"matcher":{"name":{"value":"exposeNeedle","matchType":"Equals"}}}"""

private const val QUERY_NEEDLE_FIELD =
    """{"matcher":{"name":{"value":"NEEDLE","matchType":"Equals"}}}"""

private const val QUERY_CLASS_USING_STRINGS_SINGLE_GROUP =
    """{"groups":{"needle":[{"value":"dexclub-needle-string","matchType":"Equals"}]}}"""

private const val QUERY_CLASS_USING_STRINGS_DUP_GROUPS =
    """{"groups":{"needle-a":[{"value":"dexclub-needle-string","matchType":"Equals"}],"needle-b":[{"value":"dexclub-needle-string","matchType":"Equals"}]}}"""

private const val QUERY_METHOD_USING_STRINGS_SINGLE_GROUP =
    """{"groups":{"needle":[{"value":"dexclub-needle-string","matchType":"Equals"}]}}"""

private const val QUERY_METHOD_USING_STRINGS_DUP_GROUPS =
    """{"groups":{"needle-a":[{"value":"dexclub-needle-string","matchType":"Equals"}],"needle-b":[{"value":"dexclub-needle-string","matchType":"Equals"}]}}"""

