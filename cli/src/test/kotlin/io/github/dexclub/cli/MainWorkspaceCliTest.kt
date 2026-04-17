package io.github.dexclub.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainWorkspaceCliTest {
    @Test
    fun `workspace inspect json should be parseable and clean for apk input`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-inspect-json").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                workspaceDir.absolutePath,
                "--input",
                fixture.apkFile.absolutePath,
            ).exitCode,
        )

        val inspect = invokeCli(
            "workspace",
            "inspect",
            "--workspace",
            workspaceDir.absolutePath,
            "--output-format",
            "json",
        )
        assertEquals(0, inspect.exitCode, inspect.stderr)
        val parsed = Json.parseToJsonElement(inspect.stdout.trim())
        assertEquals("apk", parsed.jsonObject.getValue("inputType").jsonPrimitive.content)
        assertTrue(!inspect.stdout.contains("I/DexKit"), inspect.stdout)
        assertTrue(!inspect.stdout.contains("DEBUG jadx"), inspect.stdout)
    }

    @Test
    fun `workspace manifest json should be parseable and clean`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-manifest-json").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                workspaceDir.absolutePath,
                "--input",
                fixture.apkFile.absolutePath,
            ).exitCode,
        )

        val manifest = invokeCli(
            "workspace",
            "manifest",
            "--workspace",
            workspaceDir.absolutePath,
            "--output-format",
            "json",
        )
        assertEquals(0, manifest.exitCode, manifest.stderr)
        val parsed = Json.parseToJsonElement(manifest.stdout.trim())
        assertTrue(parsed.jsonObject.getValue("manifest").jsonPrimitive.content.contains("<manifest"))
        assertTrue(!manifest.stdout.contains("DEBUG jadx"), manifest.stdout)
    }

    @Test
    fun `top level inspect json should be parseable and clean for apk input`() {
        val fixture = WorkspaceFixture.generated()
        val inspect = invokeCli(
            "inspect",
            "--input",
            fixture.apkFile.absolutePath,
            "--output-format",
            "json",
        )
        assertEquals(0, inspect.exitCode, inspect.stderr)
        val parsed = Json.parseToJsonElement(inspect.stdout.trim())
        assertEquals("apk", parsed.jsonObject.getValue("inputType").jsonPrimitive.content)
        assertTrue(!inspect.stdout.contains("I/DexKit"), inspect.stdout)
    }

    @Test
    fun `workspace find class with output file should keep stdout clean`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-find-output").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                workspaceDir.absolutePath,
                "--input",
                fixture.apkFile.absolutePath,
            ).exitCode,
        )
        val outputFile = File(workspaceDir, "find-class.json")
        val find = invokeCli(
            "workspace",
            "find-class",
            "--workspace",
            workspaceDir.absolutePath,
            "--query-json",
            """{"matcher":{"className":{"value":"SampleSearchTarget","matchType":"Contains","ignoreCase":true}}}""",
            "--output-file",
            outputFile.absolutePath,
        )
        assertEquals(0, find.exitCode, find.stderr)
        assertEquals("output=${outputFile.absolutePath}", find.stdout.trim())
        Json.parseToJsonElement(outputFile.readText(Charsets.UTF_8))
    }

    @Test
    fun `workspace export java should keep stdout clean`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-export-java-clean").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                workspaceDir.absolutePath,
                "--input",
                fixture.apkFile.absolutePath,
            ).exitCode,
        )
        val outputFile = File(workspaceDir, "SampleSearchTarget.java")
        val export = invokeCli(
            "workspace",
            "export-java",
            "--workspace",
            workspaceDir.absolutePath,
            "--class",
            WorkspaceFixture.SAMPLE_CLASS_NAME,
            "--output",
            outputFile.absolutePath,
        )
        assertEquals(0, export.exitCode, export.stderr)
        assertEquals("output=${outputFile.absolutePath}", export.stdout.trim())
        assertTrue(outputFile.isFile)
        assertTrue(!export.stdout.contains("DEBUG jadx"), export.stdout)
    }

    @Test
    fun `workspace init should canonicalize dexs directory and explicit list the same way`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-dexs").toFile()

        val dirInit = invokeCli(
            "workspace",
            "init",
            "--workspace",
            workspaceDir.absolutePath,
            "--input",
            fixture.dexSetDirectory.absolutePath,
            "--type",
            "dexs",
        )
        assertEquals(0, dirInit.exitCode, dirInit.stderr)
        val metadataAfterDir = workspaceMetadataFile(workspaceDir).readText()

        val workspaceDir2 = Files.createTempDirectory("dexclub-workspace-cli-dexs-list").toFile()
        val listInit = invokeCli(
            "workspace",
            "init",
            "--workspace",
            workspaceDir2.absolutePath,
            "--input",
            fixture.sampleDex.absolutePath,
            "--input",
            fixture.anotherDex.absolutePath,
            "--input",
            fixture.duplicateSampleDex.absolutePath,
            "--type",
            "dexs",
        )
        assertEquals(0, listInit.exitCode, listInit.stderr)
        val metadataAfterList = workspaceMetadataFile(workspaceDir2).readText()

        val normalizedDir = normalizeWorkspaceMetadata(metadataAfterDir)
        val normalizedList = normalizeWorkspaceMetadata(metadataAfterList)

        assertEquals("dexs", normalizedDir["inputType"])
        assertEquals("resolved_entries", normalizedDir["bindingKind"])
        assertEquals(normalizedDir["resolvedEntries"], normalizedList["resolvedEntries"])
        assertEquals(normalizedDir["fingerprint"], normalizedList["fingerprint"])
    }

    @Test
    fun `workspace status and inspect should expose different contracts`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-status").toFile()

        val init = invokeCli(
            "workspace",
            "init",
            "--workspace",
            workspaceDir.absolutePath,
            "--input",
            fixture.sampleDex.absolutePath,
        )
        assertEquals(0, init.exitCode, init.stderr)

        val status = invokeCli(
            "workspace",
            "status",
            "--workspace",
            workspaceDir.absolutePath,
        )
        assertEquals(0, status.exitCode, status.stderr)
        assertTrue(status.stdout.contains("workspaceId="), status.stdout)
        assertTrue(status.stdout.contains("fingerprint="), status.stdout)
        assertTrue(!status.stdout.contains("dexCount="), status.stdout)

        val inspect = invokeCli(
            "workspace",
            "inspect",
            "--workspace",
            workspaceDir.absolutePath,
        )
        assertEquals(0, inspect.exitCode, inspect.stderr)
        assertTrue(inspect.stdout.contains("type=dex"), inspect.stdout)
        assertTrue(inspect.stdout.contains("dexCount="), inspect.stdout)
        assertTrue(!inspect.stdout.contains("workspaceId="), inspect.stdout)
    }

    @Test
    fun `workspace capabilities and resource gates should follow input type`() {
        val fixture = WorkspaceFixture.generated()

        val dexWorkspace = Files.createTempDirectory("dexclub-workspace-cli-dex").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                dexWorkspace.absolutePath,
                "--input",
                fixture.sampleDex.absolutePath,
            ).exitCode,
        )

        val dexCapabilities = invokeCli(
            "workspace",
            "capabilities",
            "--workspace",
            dexWorkspace.absolutePath,
        )
        assertEquals(0, dexCapabilities.exitCode, dexCapabilities.stderr)
        assertTrue(dexCapabilities.stdout.contains("manifest=false"), dexCapabilities.stdout)
        assertTrue(dexCapabilities.stdout.contains("res=false"), dexCapabilities.stdout)

        val manifestFail = invokeCli(
            "workspace",
            "manifest",
            "--workspace",
            dexWorkspace.absolutePath,
        )
        assertEquals(1, manifestFail.exitCode)
        assertTrue(manifestFail.stderr.contains("当前工作区输入类型为 dex"), manifestFail.stderr)

        val apkWorkspace = Files.createTempDirectory("dexclub-workspace-cli-apk").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                apkWorkspace.absolutePath,
                "--input",
                fixture.apkFile.absolutePath,
            ).exitCode,
        )

        val apkCapabilities = invokeCli(
            "workspace",
            "capabilities",
            "--workspace",
            apkWorkspace.absolutePath,
        )
        assertEquals(0, apkCapabilities.exitCode, apkCapabilities.stderr)
        assertTrue(apkCapabilities.stdout.contains("manifest=true"), apkCapabilities.stdout)
        assertTrue(apkCapabilities.stdout.contains("res=true"), apkCapabilities.stdout)

        val manifest = invokeCli(
            "workspace",
            "manifest",
            "--workspace",
            apkWorkspace.absolutePath,
        )
        assertEquals(0, manifest.exitCode, manifest.stderr)
        assertTrue(manifest.stdout.contains("<manifest"), manifest.stdout)

        val res = invokeCli(
            "workspace",
            "res",
            "--workspace",
            apkWorkspace.absolutePath,
        )
        assertEquals(0, res.exitCode, res.stderr)
        assertTrue(res.stdout.contains("resourcesArscPresent=true"), res.stdout)
        assertTrue(res.stdout.contains("entry=res/values/strings.xml"), res.stdout)
    }

    @Test
    fun `workspace export on dexs should succeed on unique hit and fail on ambiguous hit`() {
        val fixture = WorkspaceFixture.generated()

        val uniqueWorkspace = Files.createTempDirectory("dexclub-workspace-cli-unique").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                uniqueWorkspace.absolutePath,
                "--input",
                fixture.sampleDex.absolutePath,
                "--input",
                fixture.anotherDex.absolutePath,
                "--type",
                "dexs",
            ).exitCode,
        )

        val uniqueOutput = File(uniqueWorkspace, "SampleSearchTarget.smali")
        val uniqueExport = invokeCli(
            "workspace",
            "export-smali",
            "--workspace",
            uniqueWorkspace.absolutePath,
            "--class",
            WorkspaceFixture.SAMPLE_CLASS_NAME,
            "--output",
            uniqueOutput.absolutePath,
        )
        assertEquals(0, uniqueExport.exitCode, uniqueExport.stderr)
        assertTrue(uniqueOutput.isFile)
        assertTrue(uniqueOutput.readText().contains("SampleSearchTarget"))

        val ambiguousWorkspace = Files.createTempDirectory("dexclub-workspace-cli-ambiguous").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                ambiguousWorkspace.absolutePath,
                "--input",
                fixture.sampleDex.absolutePath,
                "--input",
                fixture.duplicateSampleDex.absolutePath,
                "--type",
                "dexs",
            ).exitCode,
        )

        val ambiguousOutput = File(ambiguousWorkspace, "SampleSearchTarget.smali")
        val ambiguousExport = invokeCli(
            "workspace",
            "export-smali",
            "--workspace",
            ambiguousWorkspace.absolutePath,
            "--class",
            WorkspaceFixture.SAMPLE_CLASS_NAME,
            "--output",
            ambiguousOutput.absolutePath,
        )
        assertEquals(1, ambiguousExport.exitCode)
        assertTrue(ambiguousExport.stderr.contains("当前命中多个 dex"), ambiguousExport.stderr)
    }

    @Test
    fun `top level inspect should not modify workspace state`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-isolation").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                workspaceDir.absolutePath,
                "--input",
                fixture.sampleDex.absolutePath,
            ).exitCode,
        )

        val stateBefore = snapshotTree(workspaceDir.resolve(".dexclub-cli"))
        val inspect = invokeCli(
            "inspect",
            "--input",
            fixture.sampleDex.absolutePath,
            workingDirectory = workspaceDir,
        )
        assertEquals(0, inspect.exitCode, inspect.stderr)
        val findClass = invokeCli(
            "find-class",
            "--input",
            fixture.sampleDex.absolutePath,
            "--query-json",
            """{"matcher":{"className":{"value":"SampleSearchTarget","matchType":"Contains","ignoreCase":true}}}""",
            workingDirectory = workspaceDir,
        )
        assertEquals(0, findClass.exitCode, findClass.stderr)
        val exportOutput = File(workspaceDir, "top-level-export.smali")
        val exportSmali = invokeCli(
            "export-smali",
            "--input",
            fixture.sampleDex.absolutePath,
            "--class",
            WorkspaceFixture.SAMPLE_CLASS_NAME,
            "--output",
            exportOutput.absolutePath,
            workingDirectory = workspaceDir,
        )
        assertEquals(0, exportSmali.exitCode, exportSmali.stderr)
        val stateAfter = snapshotTree(workspaceDir.resolve(".dexclub-cli"))

        assertEquals(stateBefore, stateAfter)
    }

    @Test
    fun `workspace init should rebuild legacy state when metadata is missing`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-legacy").toFile()
        val legacyCacheMarker = workspaceDir.resolve(".dexclub-cli/cache/v1/legacy-cache.txt")
        val legacyRunsMarker = workspaceDir.resolve(".dexclub-cli/runs/v1/legacy-run.txt")
        legacyCacheMarker.parentFile.mkdirs()
        legacyRunsMarker.parentFile.mkdirs()
        legacyCacheMarker.writeText("legacy", Charsets.UTF_8)
        legacyRunsMarker.writeText("legacy", Charsets.UTF_8)

        val init = invokeCli(
            "workspace",
            "init",
            "--workspace",
            workspaceDir.absolutePath,
            "--input",
            fixture.sampleDex.absolutePath,
        )
        assertEquals(0, init.exitCode, init.stderr)
        assertTrue(!legacyCacheMarker.exists())
        assertTrue(!legacyRunsMarker.exists())
        assertTrue(workspaceMetadataFile(workspaceDir).isFile)
    }

    @Test
    fun `workspace init should rebuild derived state when schema is stale`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-schema").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                workspaceDir.absolutePath,
                "--input",
                fixture.sampleDex.absolutePath,
            ).exitCode,
        )
        val cacheMarker = workspaceDir.resolve(".dexclub-cli/cache/v1/cache-marker.txt")
        val runsMarker = workspaceDir.resolve(".dexclub-cli/runs/v1/run-marker.txt")
        cacheMarker.parentFile.mkdirs()
        runsMarker.parentFile.mkdirs()
        cacheMarker.writeText("marker", Charsets.UTF_8)
        runsMarker.writeText("marker", Charsets.UTF_8)
        val metadataFile = workspaceMetadataFile(workspaceDir)
        metadataFile.writeText(
            metadataFile.readText(Charsets.UTF_8).replace("\"schemaVersion\":1", "\"schemaVersion\":0"),
            Charsets.UTF_8,
        )

        val reinit = invokeCli(
            "workspace",
            "init",
            "--workspace",
            workspaceDir.absolutePath,
            "--input",
            fixture.sampleDex.absolutePath,
        )
        assertEquals(0, reinit.exitCode, reinit.stderr)
        assertTrue(!cacheMarker.exists())
        assertTrue(!runsMarker.exists())
    }

    @Test
    fun `workspace status should auto reconcile stale metadata`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-status-reconcile").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                workspaceDir.absolutePath,
                "--input",
                fixture.sampleDex.absolutePath,
            ).exitCode,
        )
        val cacheMarker = workspaceDir.resolve(".dexclub-cli/cache/v1/cache-marker.txt")
        val runsMarker = workspaceDir.resolve(".dexclub-cli/runs/v1/run-marker.txt")
        cacheMarker.parentFile.mkdirs()
        runsMarker.parentFile.mkdirs()
        cacheMarker.writeText("marker", Charsets.UTF_8)
        runsMarker.writeText("marker", Charsets.UTF_8)
        val metadataFile = workspaceMetadataFile(workspaceDir)
        metadataFile.writeText(
            metadataFile.readText(Charsets.UTF_8).replace("\"toolVersion\":\"dev\"", "\"toolVersion\":\"stale\""),
            Charsets.UTF_8,
        )

        val status = invokeCli(
            "workspace",
            "status",
            "--workspace",
            workspaceDir.absolutePath,
        )
        assertEquals(0, status.exitCode, status.stderr)
        assertTrue(!cacheMarker.exists())
        assertTrue(!runsMarker.exists())
        assertTrue(workspaceMetadataFile(workspaceDir).readText().contains("\"toolVersion\":\"dev\""))
    }

    @Test
    fun `apk export should not reuse extracted dex cache after input content changes`() {
        val fixture = WorkspaceFixture.generated()
        val workspaceDir = Files.createTempDirectory("dexclub-workspace-cli-apk-cache").toFile()
        assertEquals(
            0,
            invokeCli(
                "workspace",
                "init",
                "--workspace",
                workspaceDir.absolutePath,
                "--input",
                fixture.apkFile.absolutePath,
            ).exitCode,
        )

        val firstOutput = File(workspaceDir, "first.smali")
        val firstExport = invokeCli(
            "workspace",
            "export-smali",
            "--workspace",
            workspaceDir.absolutePath,
            "--class",
            WorkspaceFixture.SAMPLE_CLASS_NAME,
            "--output",
            firstOutput.absolutePath,
        )
        assertEquals(0, firstExport.exitCode, firstExport.stderr)
        val apkCacheRoot = workspaceDir.resolve(".dexclub-cli/cache/v1/inputs/apk")
        val cacheDirsBefore = apkCacheRoot.listFiles()?.filter(File::isDirectory).orEmpty().map(File::getName).toSet()
        assertTrue(cacheDirsBefore.isNotEmpty())

        fixture.overwriteApkManifestVersion("v2")

        val secondOutput = File(workspaceDir, "second.smali")
        val secondExport = invokeCli(
            "workspace",
            "export-smali",
            "--workspace",
            workspaceDir.absolutePath,
            "--class",
            WorkspaceFixture.SAMPLE_CLASS_NAME,
            "--output",
            secondOutput.absolutePath,
        )
        assertEquals(0, secondExport.exitCode, secondExport.stderr)
        val cacheDirsAfter = apkCacheRoot.listFiles()?.filter(File::isDirectory).orEmpty().map(File::getName).toSet()
        assertEquals(1, cacheDirsAfter.size, "expected only current apk derived cache to remain, after=$cacheDirsAfter")
        assertTrue(cacheDirsAfter != cacheDirsBefore, "expected derived cache key to change after input content change")
    }

    private fun invokeCli(
        vararg args: String,
        workingDirectory: File? = null,
    ): CliInvocation {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        val originalUserDir = System.getProperty("user.dir")
        return try {
            System.setOut(PrintStream(stdout, true, Charsets.UTF_8))
            System.setErr(PrintStream(stderr, true, Charsets.UTF_8))
            if (workingDirectory != null) {
                System.setProperty("user.dir", workingDirectory.absolutePath)
            }
            val exitCode = runCli(arrayOf(*args), System.out, System.err)
            CliInvocation(
                exitCode = exitCode,
                stdout = stdout.toString(Charsets.UTF_8),
                stderr = stderr.toString(Charsets.UTF_8),
            )
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            System.setProperty("user.dir", originalUserDir)
        }
    }

    private fun workspaceMetadataFile(workspaceDir: File): File =
        workspaceDir.resolve(".dexclub-cli/workspace.json")

    private fun normalizeWorkspaceMetadata(rawJson: String): Map<String, Any> {
        val inputType = Regex("\"type\":\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1).orEmpty().lowercase()
        val bindingKind = Regex("\"kind\":\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1).orEmpty()
        val fingerprint = Regex("\"fingerprint\":\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1).orEmpty()
        val resolvedEntries = Regex("\"resolvedEntries\":\\[(.*?)\\]").find(rawJson)
            ?.groupValues?.get(1)
            ?.split(",")
            ?.mapNotNull { token ->
                token.trim().removeSurrounding("\"").takeIf(String::isNotEmpty)
            }
            .orEmpty()
        return mapOf(
            "inputType" to inputType,
            "bindingKind" to bindingKind,
            "fingerprint" to fingerprint,
            "resolvedEntries" to resolvedEntries,
        )
    }

    private fun snapshotTree(root: File): Map<String, Pair<Long, Long>> {
        if (!root.exists()) return emptyMap()
        return root.walkTopDown()
            .filter { it.isFile }
            .associate { file ->
                root.toPath().relativize(file.toPath()).toString() to (file.length() to file.lastModified())
            }
    }

    private data class CliInvocation(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}

private class WorkspaceFixture private constructor(
    val dexSetDirectory: File,
    val sampleDex: File,
    val anotherDex: File,
    val duplicateSampleDex: File,
    val apkFile: File,
) {
    fun overwriteApkManifestVersion(version: String) {
        createPseudoApk(
            sourceDex = sampleDex,
            outputApk = apkFile,
            manifestVersion = version,
        )
    }

    companion object {
        const val SAMPLE_CLASS_NAME = "fixture.samples.SampleSearchTarget"
        private const val ANOTHER_CLASS_NAME = "fixture.samples.AnotherSearchTarget"
        private const val NEEDLE = "dexclub-needle-string"

        fun generated(): WorkspaceFixture {
            val tempDirectory = Files.createTempDirectory("dexclub-cli-workspace-fixture").toFile()
            val sourceRoot = File(tempDirectory, "src/fixture/samples").also(File::mkdirs)

            val sampleSource = File(sourceRoot, "SampleSearchTarget.java").apply {
                writeText(
                    """
                    package fixture.samples;
                    public class SampleSearchTarget {
                        public static final String NEEDLE = "$NEEDLE";
                        public String exposeNeedle() { return NEEDLE; }
                    }
                    """.trimIndent(),
                    Charsets.UTF_8,
                )
            }
            val anotherSource = File(sourceRoot, "AnotherSearchTarget.java").apply {
                writeText(
                    """
                    package fixture.samples;
                    public class AnotherSearchTarget {
                        public static final String OTHER = "another";
                        public String exposeOther() { return OTHER; }
                    }
                    """.trimIndent(),
                    Charsets.UTF_8,
                )
            }

            val sampleClasses = File(tempDirectory, "classes-sample").also(File::mkdirs)
            val anotherClasses = File(tempDirectory, "classes-another").also(File::mkdirs)
            val duplicateClasses = File(tempDirectory, "classes-duplicate").also(File::mkdirs)
            runCommand(
                listOf("javac", "--release", "8", "-d", sampleClasses.absolutePath, sampleSource.absolutePath),
                tempDirectory,
            )
            runCommand(
                listOf("javac", "--release", "8", "-d", anotherClasses.absolutePath, anotherSource.absolutePath),
                tempDirectory,
            )
            runCommand(
                listOf("javac", "--release", "8", "-d", duplicateClasses.absolutePath, sampleSource.absolutePath),
                tempDirectory,
            )

            val dexSetDirectory = File(tempDirectory, "dex-set").also(File::mkdirs)
            val sampleDex = buildDex(
                outputDirectory = File(tempDirectory, "dex-sample").also(File::mkdirs),
                outputFile = File(dexSetDirectory, "classes.dex"),
                classDirectory = sampleClasses,
                workingDirectory = tempDirectory,
            )
            val anotherDex = buildDex(
                outputDirectory = File(tempDirectory, "dex-another").also(File::mkdirs),
                outputFile = File(dexSetDirectory, "classes2.dex"),
                classDirectory = anotherClasses,
                workingDirectory = tempDirectory,
            )
            val duplicateSampleDex = buildDex(
                outputDirectory = File(tempDirectory, "dex-duplicate").also(File::mkdirs),
                outputFile = File(dexSetDirectory, "classes3.dex"),
                classDirectory = duplicateClasses,
                workingDirectory = tempDirectory,
            )

            val apkFile = File(tempDirectory, "fixture.apk").apply {
                createPseudoApk(
                    sourceDex = sampleDex,
                    outputApk = this,
                    manifestVersion = "v1",
                )
            }

            return WorkspaceFixture(
                dexSetDirectory = dexSetDirectory,
                sampleDex = sampleDex,
                anotherDex = anotherDex,
                duplicateSampleDex = duplicateSampleDex,
                apkFile = apkFile,
            )
        }

        private fun buildDex(
            outputDirectory: File,
            outputFile: File,
            classDirectory: File,
            workingDirectory: File,
        ): File {
            val classFiles = classDirectory.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map { it.absolutePath }
                .toList()
            runCommand(
                buildList {
                    add(resolveD8Command())
                    add("--min-api")
                    add("21")
                    add("--output")
                    add(outputDirectory.absolutePath)
                    addAll(classFiles)
                },
                workingDirectory,
            )
            val generatedDex = File(outputDirectory, "classes.dex")
            generatedDex.copyTo(outputFile, overwrite = true)
            return outputFile
        }

        private fun resolveD8Command(): String {
            val envRoots = listOfNotNull(
                System.getenv("ANDROID_SDK_ROOT"),
                System.getenv("ANDROID_HOME"),
            ).map(::File)
            val candidatePaths = buildList {
                envRoots.forEach { root ->
                    root.resolve("build-tools").listFiles()
                        ?.sortedByDescending(File::getName)
                        ?.forEach { buildToolsDir ->
                            add(buildToolsDir.resolve("d8"))
                            add(buildToolsDir.resolve("d8.bat"))
                        }
                }
                add(File("d8"))
                add(File("d8.bat"))
            }
            return candidatePaths.firstOrNull { candidate ->
                runCatching {
                    val process = ProcessBuilder(candidate.path, "--version")
                        .redirectErrorStream(true)
                        .start()
                    process.inputStream.bufferedReader().use { it.readText() }
                    process.waitFor() == 0
                }.getOrDefault(false)
            }?.path ?: error("未找到可用的 d8 命令")
        }

        private fun runCommand(
            command: List<String>,
            workingDirectory: File,
        ) {
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

        private fun createPseudoApk(
            sourceDex: File,
            outputApk: File,
            manifestVersion: String,
        ) {
            ZipOutputStream(outputApk.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.write(
                    """
                    <manifest package="fixture.samples" android:versionName="$manifestVersion">
                      <application />
                    </manifest>
                    """.trimIndent().toByteArray(Charsets.UTF_8),
                )
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("resources.arsc"))
                zip.write(byteArrayOf(0x01, 0x02))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("res/values/strings.xml"))
                zip.write(
                    """
                    <resources>
                      <string name="app_name">fixture</string>
                    </resources>
                    """.trimIndent().toByteArray(Charsets.UTF_8),
                )
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("classes.dex"))
                sourceDex.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
