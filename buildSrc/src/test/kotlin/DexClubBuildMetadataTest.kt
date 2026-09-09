import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.kotlin.dsl.register
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DexClubBuildMetadataTest {
    @Test
    fun explicitMetadataIsValidatedAndNormalized() {
        val metadata = resolveDexClubBuildMetadataValues(
            mcpContractVersion = 1,
            explicitVersion = "  v1.4.0-rc1 ",
            explicitCommit = COMMIT,
            explicitDirty = "false",
            gitCommit = null,
            gitDirty = null,
        )

        assertEquals("1.4.0-rc1", metadata.version)
        assertEquals(COMMIT, metadata.commit)
        assertFalse(metadata.dirty)
        assertEquals(1, metadata.mcpContractVersion)
    }

    @Test
    fun invalidExplicitMetadataFailsBeforeGeneration() {
        assertFailsWith<IllegalArgumentException> {
            resolveDexClubBuildMetadataValues(0, null, null, null, null, null)
        }

        assertFailsWith<IllegalArgumentException> {
            resolveDexClubBuildMetadataValues(1, "v1/4/0", null, null, null, null)
        }

        assertFailsWith<IllegalArgumentException> {
            resolveDexClubBuildMetadataValues(1, null, "deadbeef", null, null, null)
        }

        assertFailsWith<IllegalArgumentException> {
            resolveDexClubBuildMetadataValues(1, "1.4.0", COMMIT, "true", null, null)
        }

        assertFailsWith<IllegalStateException> {
            resolveDexClubBuildMetadataValues(1, null, null, "yes", null, null)
        }
    }

    @Test
    fun gitMetadataFallsBackToDevelopmentVersion() {
        val clean = resolveDexClubBuildMetadataValues(1, null, null, null, COMMIT, false)
        assertEquals("dev-0123456789ab", clean.version)
        assertFalse(clean.dirty)

        val dirty = resolveDexClubBuildMetadataValues(1, null, null, null, COMMIT, true)
        assertEquals("dev-0123456789ab-dirty", dirty.version)
        assertTrue(dirty.dirty)

        val unknown = resolveDexClubBuildMetadataValues(1, null, null, null, null, null)
        assertEquals("dev-unknown", unknown.version)
        assertEquals("unknown", unknown.commit)
    }

    @Test
    fun generatedBuildInfoIsStableAndEscapesKotlinStrings() {
        val project = ProjectBuilder.builder().withProjectDir(tempDirectory()).build()
        val output = project.layout.buildDirectory.dir("generated/build-info")
        val task = project.tasks.register<GenerateDexClubBuildInfo>("buildInfo") {
            packageName.set("example.generated")
            objectName.set("BuildInfo")
            visibility.set("public")
            version.set("dev-abc\\\"123")
            mcpContractVersion.set(7)
            commit.set(COMMIT)
            dirty.set(true)
            outputDirectory.set(output)
        }.get()

        task.generate()
        val generated = output.get().file("example/generated/BuildInfo.kt").asFile.toPath()
        val first = generated.readText()
        task.generate()
        val second = generated.readText()

        assertEquals(first, second)
        assertTrue("const val VERSION: String = \"dev-abc\\\\\\\"123\"" in first)
        assertTrue("const val MCP_CONTRACT_VERSION: Int = 7" in first)
        assertTrue("const val DIRTY: Boolean = true" in first)
    }

    @Test
    fun generatedVersionAndSkillContainOnlyResolvedMetadata() {
        val project = ProjectBuilder.builder().withProjectDir(tempDirectory()).build()
        val versionFile = project.layout.buildDirectory.file("generated/VERSION")
        val versionTask = project.tasks.register<GenerateDexClubVersionFile>("version") {
            version.set("1.4.0")
            mcpContractVersion.set(1)
            commit.set(COMMIT)
            dirty.set(false)
            outputFile.set(versionFile)
        }.get()
        versionTask.generate()
        assertEquals(
            "version: 1.4.0\ncommit: $COMMIT\ndirty: false\nmcpContractVersion: 1\n",
            versionFile.get().asFile.readText(),
        )

        val source = Files.createTempDirectory("dexclub-skill-template").also { root ->
            root.resolve("SKILL.md").writeText(
                "version: __DEXCLUB_VERSION__\ncontract: __DEXCLUB_MCP_CONTRACT_VERSION__\n",
            )
        }
        val skillOutput = project.layout.buildDirectory.dir("generated/skill")
        val skillTask = project.tasks.register<GenerateDexClubSkill>("skill") {
            sourceDirectory.set(source.toFile())
            version.set("dev-abc-dirty")
            mcpContractVersion.set(1)
            outputDirectory.set(skillOutput)
        }.get()
        skillTask.generate()

        val generatedSkill = skillOutput.get().file("SKILL.md").asFile.readText()
        assertEquals("version: dev-abc-dirty\ncontract: 1\n", generatedSkill)
        assertFalse("__DEXCLUB_" in generatedSkill)
    }

    private fun tempDirectory() = Files.createTempDirectory("dexclub-build-metadata").toFile()

    private companion object {
        const val COMMIT = "0123456789abcdef0123456789abcdef01234567"
    }
}
