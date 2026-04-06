package io.github.dexclub.core

import io.github.dexclub.core.model.DexExportFormat
import io.github.dexclub.core.model.DexInputKind
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.SmaliExportRequest
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
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
    fun `search should return old and new result models`() {
        val fixture = TestDexFixture.generated()

        DexEngine(listOf(fixture.dexFile.absolutePath)).use { engine ->
            val classResults = engine.searchClassesByName("SampleSearchTarget")
            assertTrue(classResults.any { it.name == fixture.className })

            val classHits = engine.searchClassHitsByName("SampleSearchTarget")
            assertTrue(classHits.any { it.name == fixture.className })
            assertTrue(classHits.all { it.sourceDexPath == fixture.dexFile.absolutePath })

            val methodResults = engine.searchMethodsByString(fixture.needle)
            assertTrue(methodResults.any { it.className == fixture.className })

            val methodHits = engine.searchMethodHitsByString(fixture.needle)
            assertTrue(methodHits.any { it.className == fixture.className })
            assertTrue(methodHits.all { it.sourceDexPath == fixture.dexFile.absolutePath })
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

                return TestDexFixture(
                    dexFile = File(dexDirectory, "classes.dex"),
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
                val candidates = listOf(
                    "/root/Android/build-tools/36.0.0/d8",
                    "/root/Android/build-tools/35.0.0/d8",
                    "/root/Android/cmdline-tools/latest/bin/d8",
                    "d8",
                )
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
        }
    }
}
