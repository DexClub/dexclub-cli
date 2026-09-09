import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DexClubBuildMetadataFunctionalTest {
    @Test
    fun metadataTasksTrackVersionAndTemplateInputsWithoutClean() {
        val fixture = createFixture()

        val first = runBuild(fixture, version = "1.4.0")
        assertOutcome(first, ":generateDexClubVersionFile", TaskOutcome.SUCCESS)
        assertOutcome(first, ":generateBuildInfo", TaskOutcome.SUCCESS)
        assertOutcome(first, ":generateVersionedDexClubSkill", TaskOutcome.SUCCESS)

        val versionFile = fixture.resolve("build/generated/distribution/VERSION")
        val buildInfo = fixture.resolve("build/generated/buildInfo/example/BuildInfo.kt")
        val generatedSkill = fixture.resolve("build/generated/skill/SKILL.md")
        assertTrue("version: 1.4.0" in versionFile.readText())
        assertTrue("const val VERSION: String = \"1.4.0\"" in buildInfo.readText())
        assertTrue("version: 1.4.0" in generatedSkill.readText())

        val unchanged = runBuild(fixture, version = "1.4.0")
        assertOutcome(unchanged, ":generateDexClubVersionFile", TaskOutcome.UP_TO_DATE)
        assertOutcome(unchanged, ":generateBuildInfo", TaskOutcome.UP_TO_DATE)
        assertOutcome(unchanged, ":generateVersionedDexClubSkill", TaskOutcome.UP_TO_DATE)

        val changedVersion = runBuild(fixture, version = "1.4.1")
        assertOutcome(changedVersion, ":generateDexClubVersionFile", TaskOutcome.SUCCESS)
        assertOutcome(changedVersion, ":generateBuildInfo", TaskOutcome.SUCCESS)
        assertOutcome(changedVersion, ":generateVersionedDexClubSkill", TaskOutcome.SUCCESS)
        assertTrue("version: 1.4.1" in versionFile.readText())
        assertTrue("const val VERSION: String = \"1.4.1\"" in buildInfo.readText())
        assertTrue("version: 1.4.1" in generatedSkill.readText())

        fixture.resolve("skill/reference.txt").writeText("updated\n")
        val changedTemplate = runBuild(fixture, version = "1.4.1")
        assertOutcome(changedTemplate, ":generateDexClubVersionFile", TaskOutcome.UP_TO_DATE)
        assertOutcome(changedTemplate, ":generateBuildInfo", TaskOutcome.UP_TO_DATE)
        assertOutcome(changedTemplate, ":generateVersionedDexClubSkill", TaskOutcome.SUCCESS)
        assertEquals("updated\n", fixture.resolve("build/generated/skill/reference.txt").readText())
    }

    private fun createFixture(): Path {
        val fixture = Files.createTempDirectory("dexclub-build-metadata-functional")
        val fixtureBuildSrc = fixture.resolve("buildSrc")
        val fixtureBuildSrcSources = fixtureBuildSrc.resolve("src/main/kotlin").also(Path::createDirectories)
        val repositoryBuildSrc = repositoryRoot().resolve("buildSrc")

        listOf("DexClubBuildMetadata.kt", "DexClubBuildTasks.kt").forEach { fileName ->
            Files.copy(
                repositoryBuildSrc.resolve("src/main/kotlin/$fileName"),
                fixtureBuildSrcSources.resolve(fileName),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        fixtureBuildSrc.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                gradlePluginPortal()
                mavenCentral()
            }
            """.trimIndent() + "\n",
        )
        fixture.resolve("settings.gradle.kts").writeText("rootProject.name = \"metadata-fixture\"\n")
        fixture.resolve("build.gradle.kts").writeText(
            """
            val metadata = resolveDexClubBuildMetadata(mcpContractVersion = 1)

            val generateVersion = tasks.register<GenerateDexClubVersionFile>("generateDexClubVersionFile") {
                version.set(metadata.version)
                mcpContractVersion.set(metadata.mcpContractVersion)
                commit.set(metadata.commit)
                dirty.set(metadata.dirty)
                outputFile.set(layout.buildDirectory.file("generated/distribution/VERSION"))
            }

            val generateBuildInfo = tasks.register<GenerateDexClubBuildInfo>("generateBuildInfo") {
                packageName.set("example")
                objectName.set("BuildInfo")
                visibility.set("public")
                version.set(metadata.version)
                mcpContractVersion.set(metadata.mcpContractVersion)
                commit.set(metadata.commit)
                dirty.set(metadata.dirty)
                outputDirectory.set(layout.buildDirectory.dir("generated/buildInfo"))
            }

            val generateSkill = tasks.register<GenerateDexClubSkill>("generateVersionedDexClubSkill") {
                sourceDirectory.set(layout.projectDirectory.dir("skill"))
                version.set(metadata.version)
                mcpContractVersion.set(metadata.mcpContractVersion)
                outputDirectory.set(layout.buildDirectory.dir("generated/skill"))
            }

            tasks.register("generateMetadata") {
                dependsOn(generateVersion, generateBuildInfo, generateSkill)
            }
            """.trimIndent() + "\n",
        )
        fixture.resolve("skill").createDirectories()
        fixture.resolve("skill/SKILL.md").writeText(
            "version: __DEXCLUB_VERSION__\ncontract: __DEXCLUB_MCP_CONTRACT_VERSION__\n",
        )
        fixture.resolve("skill/reference.txt").writeText("initial\n")
        return fixture
    }

    private fun runBuild(fixture: Path, version: String) =
        GradleRunner.create()
            .withProjectDir(fixture.toFile())
            .withArguments(
                "-PreleaseVersion=$version",
                "-PreleaseCommit=$COMMIT",
                "-PreleaseDirty=false",
                "generateMetadata",
            )
            .build()

    private fun assertOutcome(
        result: org.gradle.testkit.runner.BuildResult,
        taskPath: String,
        expected: TaskOutcome,
    ) {
        assertEquals(expected, result.task(taskPath)?.outcome, "Unexpected outcome for $taskPath")
    }

    private fun repositoryRoot(): Path =
        Path.of(checkNotNull(System.getProperty("dexclub.repo.root"))).toAbsolutePath().normalize()

    private companion object {
        const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
    }
}
