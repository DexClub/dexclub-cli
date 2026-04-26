package io.github.dexclub.core.api.shared

enum class Operation {
    Inspect,
    FindClass,
    FindMethod,
    FindField,
    ExportDex,
    ExportSmali,
    ExportJava,
    ManifestDecode,
    ResourceTableDecode,
    XmlDecode,
    ResourceEntryList,
}

class CapabilityError(
    val operation: Operation,
    val requiredCapability: String,
    val kind: String,
) : RuntimeException()
