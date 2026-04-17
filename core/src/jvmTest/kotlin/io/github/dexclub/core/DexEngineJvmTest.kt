package io.github.dexclub.core

import io.github.dexclub.core.request.DexClassQueryRequest
import io.github.dexclub.core.model.DexExportFormat
import io.github.dexclub.core.model.DexInputKind
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.DexFieldQueryRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.DexMethodQueryRequest
import io.github.dexclub.core.request.SmaliExportRequest
import io.github.dexclub.dexkit.query.ClassMatcher
import io.github.dexclub.dexkit.query.FieldMatcher
import io.github.dexclub.dexkit.query.FieldsMatcher
import io.github.dexclub.dexkit.query.MethodMatcher
import io.github.dexclub.dexkit.query.MethodsMatcher
import io.github.dexclub.dexkit.query.StringMatchType
import io.github.dexclub.dexkit.query.StringMatcher
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.io.PrintStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DexEngineJvmTest {
    @Test
    fun `isDex and inspect should recognize generated dex`() {
        val fixture = TestDexFixture.generated()

        assertTrue(DexEngine.isDex(fixture.dexFile.absolutePath))
        assertTrue(!DexEngine.isDex(fixture.invalidFile.absolutePath))

        DexEngine(listOf(fixture.dexFile.absolutePath)).use { engine ->
            val archiveInfo = engine.inspect()
            assertEquals(DexInputKind.Dex, archiveInfo.kind)
            assertEquals(1, archiveInfo.dexCount)
            assertNotNull(archiveInfo.classCount)
            assertTrue(archiveInfo.classCount > 0)
            assertEquals(listOf(fixture.dexFile.absolutePath), archiveInfo.inputs.map { it.path })
        }
    }

    @Test
    fun `search should return new result models`() {
        val fixture = TestDexFixture.generated()

        DexEngine(listOf(fixture.dexFile.absolutePath)).use { engine ->
            val classHits = engine.searchClassHitsByName("SampleSearchTarget")
            assertTrue(classHits.any { it.name == fixture.className })
            assertTrue(classHits.all { it.sourceDexPath == fixture.dexFile.absolutePath })

            val methodHits = engine.searchMethodHitsByString(fixture.needle)
            assertTrue(methodHits.any { it.className == fixture.className })
            assertTrue(methodHits.all { it.sourceDexPath == fixture.dexFile.absolutePath })
        }
    }

    @Test
    fun `apk query should not report dex validation failure and should use apk source path`() {
        val fixture = TestDexFixture.generated()
        val originalErr = System.err
        val errBuffer = ByteArrayOutputStream()
        System.setErr(PrintStream(errBuffer, true, Charsets.UTF_8))

        try {
            DexEngine(listOf(fixture.apkFile.absolutePath)).use { engine ->
                val classHits = engine.findClassHits(
                    DexClassQueryRequest(
                        matcher = ClassMatcher(
                            className = StringMatcher(fixture.className, StringMatchType.Equals),
                        ),
                    ),
                )
                assertTrue(classHits.any { it.name == fixture.className })
                assertTrue(classHits.all { it.sourceDexPath == fixture.apkFile.absolutePath })
            }
        } finally {
            System.setErr(originalErr)
        }

        val stderrText = errBuffer.toString(Charsets.UTF_8)
        assertTrue(!stderrText.contains("Dex 文件校验失败"), stderrText)
    }

    @Test
    fun `advanced query requests should support json round trip`() {
        val classQuery = DexClassQueryRequest().apply {
            searchPackages = listOf("fixture.samples")
            matcher = ClassMatcher().apply {
                className = StringMatcher("SampleSearchTarget")
                usingStrings += StringMatcher(TEST_NEEDLE)
                fields = FieldsMatcher(count = 1..1)
            }
        }
        val methodQuery = DexMethodQueryRequest().apply {
            matcher = MethodMatcher().apply {
                name = StringMatcher("exposeNeedle")
                declaredClass = ClassMatcher(className = StringMatcher("fixture.samples.SampleSearchTarget"))
                returnType = ClassMatcher(className = StringMatcher("java.lang.String"))
                usingStrings += StringMatcher(TEST_NEEDLE)
            }
        }
        val fieldQuery = DexFieldQueryRequest().apply {
            matcher = FieldMatcher().apply {
                name = StringMatcher("NEEDLE")
                declaredClass = ClassMatcher(className = StringMatcher("fixture.samples.SampleSearchTarget"))
                type = ClassMatcher(className = StringMatcher("java.lang.String", StringMatchType.Equals))
            }
        }

        val restoredClassQuery = classQuery.toCoreJsonString().parseCoreJson<DexClassQueryRequest>()
        val restoredMethodQuery = methodQuery.toCoreJsonString().parseCoreJson<DexMethodQueryRequest>()
        val restoredFieldQuery = fieldQuery.toCoreJsonString().parseCoreJson<DexFieldQueryRequest>()

        assertEquals(classQuery, restoredClassQuery)
        assertEquals(methodQuery, restoredMethodQuery)
        assertEquals(fieldQuery, restoredFieldQuery)
    }

    @Test
    fun `advanced query should return class method and field hits`() {
        val fixture = TestDexFixture.generated()

        DexEngine(listOf(fixture.dexFile.absolutePath)).use { engine ->
            val classHits = engine.findClassHits(
                DexClassQueryRequest().apply {
                    searchPackages = listOf("fixture.samples")
                    matcher = ClassMatcher().apply {
                        className = StringMatcher(fixture.className)
                        fields = FieldsMatcher(count = 1..1)
                        methods = MethodsMatcher(count = 1..Int.MAX_VALUE)
                    }
                },
            )
            assertTrue(classHits.any { it.name == fixture.className })
            assertTrue(classHits.all { it.sourceDexPath == fixture.dexFile.absolutePath })

            val methodHits = engine.findMethodHits(
                DexMethodQueryRequest().apply {
                    matcher = MethodMatcher().apply {
                        name = StringMatcher("exposeNeedle")
                        declaredClass = ClassMatcher(className = StringMatcher(fixture.className))
                        returnType = ClassMatcher(className = StringMatcher("java.lang.String"))
                        usingStrings += StringMatcher(fixture.needle)
                    }
                },
            )
            assertTrue(methodHits.any { it.className == fixture.className && it.name == "exposeNeedle" })
            assertTrue(methodHits.all { it.sourceDexPath == fixture.dexFile.absolutePath })

            val fieldHits = engine.findFieldHits(
                DexFieldQueryRequest().apply {
                    matcher = FieldMatcher().apply {
                        name = StringMatcher("NEEDLE")
                        declaredClass = ClassMatcher(className = StringMatcher(fixture.className))
                        type = ClassMatcher(className = StringMatcher("java.lang.String"))
                    }
                },
            )
            assertTrue(fieldHits.any { it.className == fixture.className && it.name == "NEEDLE" })
            assertTrue(fieldHits.all { it.sourceDexPath == fixture.dexFile.absolutePath })
        }
    }

    @Test
    fun `export should write dex smali and java outputs`() = runBlocking {
        val fixture = TestDexFixture.generated()
        val outputDirectory = Files.createTempDirectory("dexclub-core-export-test").toFile()

        DexEngine(listOf(fixture.dexFile.absolutePath)).use { engine ->
            val dexResult = engine.exportDex(
                DexExportRequest(
                    className = fixture.className,
                    sourceDexPath = fixture.dexFile.absolutePath,
                    outputPath = File(outputDirectory, "SampleSearchTarget.dex").absolutePath,
                ),
            )
            assertEquals(DexExportFormat.Dex, dexResult.format)
            assertTrue(DexEngine.isDex(dexResult.outputPath))

            val smaliResult = engine.exportSmali(
                SmaliExportRequest(
                    className = fixture.className,
                    sourceDexPath = fixture.dexFile.absolutePath,
                    outputPath = File(outputDirectory, "SampleSearchTarget.smali").absolutePath,
                ),
            )
            assertEquals(DexExportFormat.Smali, smaliResult.format)
            val smaliText = File(smaliResult.outputPath).readText()
            assertTrue(smaliText.contains("Lfixture/samples/SampleSearchTarget;"))
            assertTrue(smaliText.contains(fixture.needle))

            val javaResult = engine.exportJava(
                JavaExportRequest(
                    className = fixture.className,
                    sourceDexPath = fixture.dexFile.absolutePath,
                    outputPath = File(outputDirectory, "SampleSearchTarget.java").absolutePath,
                ),
            )
            assertEquals(DexExportFormat.JavaSource, javaResult.format)
            val javaText = File(javaResult.outputPath).readText()
            assertTrue(javaText.contains("class SampleSearchTarget"))
            assertTrue(javaText.contains(fixture.needle))
        }
    }

    private data class TestDexFixture(
        val dexFile: File,
        val apkFile: File,
        val invalidFile: File,
        val className: String,
        val needle: String,
    ) {
        companion object {
            private const val FIXTURE_RESOURCE =
                "/fixtures/src/fixture/samples/SampleSearchTarget.java"
            private const val CLASS_NAME = "fixture.samples.SampleSearchTarget"
            private const val NEEDLE = "dexclub-needle-string"

            fun generated(): TestDexFixture {
                val tempDirectory = Files.createTempDirectory("dexclub-core-test-fixture").toFile()
                val sourceFile = File(tempDirectory, "src/fixture/samples/SampleSearchTarget.java")
                sourceFile.parentFile.mkdirs()
                sourceFile.writeText(loadFixtureSource(), Charsets.UTF_8)

                val classDirectory = File(tempDirectory, "classes").also(File::mkdirs)
                runCommand(
                    listOf(
                        "javac",
                        "--release",
                        "8",
                        "-d",
                        classDirectory.absolutePath,
                        sourceFile.absolutePath,
                    ),
                    tempDirectory,
                )

                val dexDirectory = File(tempDirectory, "dex-out").also(File::mkdirs)
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
                        add(dexDirectory.absolutePath)
                        addAll(classFiles)
                    },
                    tempDirectory,
                )

                val invalidFile = File(tempDirectory, "not-a-dex.bin").apply {
                    writeText("not a dex", Charsets.UTF_8)
                }
                val apkFile = File(tempDirectory, "fixture.apk").apply {
                    createPseudoApk(sourceDex = File(dexDirectory, "classes.dex"), outputApk = this)
                }

                return TestDexFixture(
                    dexFile = File(dexDirectory, "classes.dex"),
                    apkFile = apkFile,
                    invalidFile = invalidFile,
                    className = CLASS_NAME,
                    needle = NEEDLE,
                )
            }

            private fun loadFixtureSource(): String {
                return checkNotNull(TestDexFixture::class.java.getResource(FIXTURE_RESOURCE)) {
                    "缺少测试夹具源码: $FIXTURE_RESOURCE"
                }.readText()
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
                    add("/root/Android/build-tools/36.0.0/d8")
                    add("/root/Android/build-tools/35.0.0/d8")
                    add("/root/Android/cmdline-tools/latest/bin/d8")
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
            ) {
                ZipOutputStream(outputApk.outputStream().buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("classes.dex"))
                    sourceDex.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
}

private const val TEST_NEEDLE = "dexclub-needle-string"
