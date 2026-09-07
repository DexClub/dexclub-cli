import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateDexClubBuildInfo : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val objectName: Property<String>

    @get:Input
    abstract val visibility: Property<String>

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val mcpContractVersion: Property<Int>

    @get:Input
    abstract val commit: Property<String>

    @get:Input
    abstract val dirty: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputRoot = outputDirectory.get().asFile.toPath()
        project.delete(outputRoot)
        val packagePath = packageName.get().replace('.', '/')
        val outputFile = outputRoot.resolve(packagePath).resolve("${objectName.get()}.kt")
        outputFile.parent.createDirectories()
        Files.writeString(
            outputFile,
            buildString {
                appendLine("package ${packageName.get()}")
                appendLine()
                appendLine("${visibility.get()} object ${objectName.get()} {")
                appendLine("    const val VERSION: String = ${version.get().asKotlinString()}")
                appendLine("    const val MCP_CONTRACT_VERSION: Int = ${mcpContractVersion.get()}")
                appendLine("    const val COMMIT: String = ${commit.get().asKotlinString()}")
                appendLine("    const val DIRTY: Boolean = ${dirty.get()}")
                appendLine("}")
            },
            StandardCharsets.UTF_8,
        )
    }
}

abstract class GenerateDexClubVersionFile : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val mcpContractVersion: Property<Int>

    @get:Input
    abstract val commit: Property<String>

    @get:Input
    abstract val dirty: Property<Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val file = outputFile.get().asFile.toPath()
        file.parent.createDirectories()
        Files.writeString(
            file,
            buildString {
                appendLine("version: ${version.get()}")
                appendLine("commit: ${commit.get()}")
                appendLine("dirty: ${dirty.get()}")
                appendLine("mcpContractVersion: ${mcpContractVersion.get()}")
            },
            StandardCharsets.UTF_8,
        )
    }
}

abstract class GenerateDexClubSkill : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val mcpContractVersion: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val sourceRoot = sourceDirectory.get().asFile.toPath()
        val outputRoot = outputDirectory.get().asFile.toPath()
        project.delete(outputRoot)
        outputRoot.createDirectories()

        Files.walk(sourceRoot).use { entries ->
            entries.forEach { source ->
                val relative = sourceRoot.relativize(source)
                val target = outputRoot.resolve(relative)
                if (Files.isDirectory(source)) {
                    target.createDirectories()
                } else if (relative.toString().replace('\\', '/') == "SKILL.md") {
                    target.parent.createDirectories()
                    val template = Files.readString(source, StandardCharsets.UTF_8)
                    require(template.contains(VERSION_PLACEHOLDER)) {
                        "Missing $VERSION_PLACEHOLDER in $source"
                    }
                    require(template.contains(CONTRACT_VERSION_PLACEHOLDER)) {
                        "Missing $CONTRACT_VERSION_PLACEHOLDER in $source"
                    }
                    val generated = template
                        .replace(VERSION_PLACEHOLDER, version.get())
                        .replace(CONTRACT_VERSION_PLACEHOLDER, mcpContractVersion.get().toString())
                    require(VERSION_PLACEHOLDER !in generated && CONTRACT_VERSION_PLACEHOLDER !in generated)
                    Files.writeString(target, generated, StandardCharsets.UTF_8)
                } else {
                    target.parent.createDirectories()
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private companion object {
        const val VERSION_PLACEHOLDER = "__DEXCLUB_VERSION__"
        const val CONTRACT_VERSION_PLACEHOLDER = "__DEXCLUB_MCP_CONTRACT_VERSION__"
    }
}

private fun String.asKotlinString(): String = buildString {
    append('"')
    this@asKotlinString.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}
