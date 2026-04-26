package io.github.dexclub.core.api.shared

enum class WorkspaceKind {
    Apk,
    Dex,
    Manifest,
    Arsc,
    Axml,
}

enum class InputType {
    File,
}

enum class WorkspaceState {
    Healthy,
    Degraded,
    Broken,
}

enum class WorkspaceIssueSeverity {
    Warning,
    Error,
}

enum class CacheState {
    Present,
    Partial,
    Missing,
}

enum class MethodSmaliMode {
    Snippet,
    Class,
}

data class CapabilitySet(
    val inspect: Boolean = true,
    val findClass: Boolean = false,
    val findMethod: Boolean = false,
    val findField: Boolean = false,
    val exportDex: Boolean = false,
    val exportSmali: Boolean = false,
    val exportJava: Boolean = false,
    val manifestDecode: Boolean = false,
    val resourceTableDecode: Boolean = false,
    val xmlDecode: Boolean = false,
    val resourceEntryList: Boolean = false,
)

data class InventoryCounts(
    val apkCount: Int,
    val dexCount: Int,
    val manifestCount: Int,
    val arscCount: Int,
    val binaryXmlCount: Int,
)

data class WorkspaceIssue(
    val code: String,
    val severity: WorkspaceIssueSeverity,
    val message: String,
)

data class PageWindow(
    val offset: Int = 0,
    val limit: Int? = null,
)

data class SourceLocator(
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
)
