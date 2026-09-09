import java.util.concurrent.TimeUnit
import org.gradle.api.Project

data class DexClubBuildMetadata(
    val version: String,
    val mcpContractVersion: Int,
    val commit: String,
    val dirty: Boolean,
)

fun Project.resolveDexClubBuildMetadata(mcpContractVersion: Int): DexClubBuildMetadata {
    require(mcpContractVersion > 0) { "MCP contract version must be greater than zero" }

    val explicitVersion = providers.gradleProperty("releaseVersion").orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val explicitCommit = providers.gradleProperty("releaseCommit").orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val explicitDirty = providers.gradleProperty("releaseDirty").orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    val gitCommit = gitOutput("rev-parse", "HEAD")
    return resolveDexClubBuildMetadataValues(
        mcpContractVersion = mcpContractVersion,
        explicitVersion = explicitVersion,
        explicitCommit = explicitCommit,
        explicitDirty = explicitDirty,
        gitCommit = gitCommit,
        gitDirty = if (explicitDirty == null) {
            gitOutput("status", "--porcelain")?.isNotEmpty()
        } else {
            null
        },
    )
}

internal fun resolveDexClubBuildMetadataValues(
    mcpContractVersion: Int,
    explicitVersion: String?,
    explicitCommit: String?,
    explicitDirty: String?,
    gitCommit: String?,
    gitDirty: Boolean?,
): DexClubBuildMetadata {
    require(mcpContractVersion > 0) { "MCP contract version must be greater than zero" }
    if (explicitCommit != null) {
        require(COMMIT_PATTERN.matches(explicitCommit)) {
            "releaseCommit must be a full 40-character Git SHA: $explicitCommit"
        }
    }

    val commit = explicitCommit ?: gitCommit ?: "unknown"
    require(commit == "unknown" || COMMIT_PATTERN.matches(commit)) {
        "releaseCommit must be a full 40-character Git SHA or 'unknown': $commit"
    }

    val dirty = parseDirty(explicitDirty) ?: gitDirty ?: false
    val version = explicitVersion?.trim()?.takeIf(String::isNotEmpty)?.let(::normalizeReleaseVersion)
        ?: buildString {
            append("dev-")
            append(if (commit == "unknown") commit else commit.take(12))
            if (dirty) append("-dirty")
        }

    if (explicitVersion != null) {
        require(!dirty) { "A releaseVersion build requires releaseDirty=false and a clean checkout" }
    }

    return DexClubBuildMetadata(version, mcpContractVersion, commit, dirty)
}

fun Project.dexClubBuildMetadata(): DexClubBuildMetadata =
    rootProject.extensions.extraProperties[DEXCLUB_BUILD_METADATA_KEY] as? DexClubBuildMetadata
        ?: error("DexClub build metadata has not been configured by the root project")

const val DEXCLUB_BUILD_METADATA_KEY = "dexClubBuildMetadata"

private val VERSION_PATTERN = Regex("[0-9A-Za-z][0-9A-Za-z.+-]*")
private val COMMIT_PATTERN = Regex("[0-9a-fA-F]{40}")

private fun normalizeReleaseVersion(raw: String): String {
    val normalized = raw.removePrefix("v")
    require(VERSION_PATTERN.matches(normalized)) {
        "releaseVersion contains unsupported characters after normalization: $raw"
    }
    return normalized
}

private fun parseDirty(raw: String?): Boolean? = raw?.trim()?.takeIf(String::isNotEmpty)?.let {
    when (it.lowercase()) {
        "true" -> true
        "false" -> false
        else -> error("releaseDirty must be 'true' or 'false': $it")
    }
}

private fun Project.gitOutput(vararg arguments: String): String? = try {
    val process = ProcessBuilder(listOf("git") + arguments)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        null
    } else if (process.exitValue() != 0) {
        null
    } else {
        process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .trim()
    }
} catch (_: Exception) {
    null
}
