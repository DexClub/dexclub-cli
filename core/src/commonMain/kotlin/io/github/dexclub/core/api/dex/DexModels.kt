package io.github.dexclub.core.api.dex

import io.github.dexclub.core.api.shared.MethodSmaliMode
import io.github.dexclub.core.api.shared.PageWindow
import io.github.dexclub.core.api.shared.SourceLocator

data class ClassHit(
    val className: String,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
)

data class MethodHit(
    val className: String,
    val methodName: String,
    val descriptor: String,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
)

data class FieldHit(
    val className: String,
    val fieldName: String,
    val descriptor: String,
    val sourcePath: String? = null,
    val sourceEntry: String? = null,
)

data class FindClassesRequest(
    val queryText: String,
    val window: PageWindow = PageWindow(),
)

data class FindMethodsRequest(
    val queryText: String,
    val window: PageWindow = PageWindow(),
)

data class FindFieldsRequest(
    val queryText: String,
    val window: PageWindow = PageWindow(),
)

data class FindClassesUsingStringsRequest(
    val queryText: String,
    val window: PageWindow = PageWindow(),
)

data class FindMethodsUsingStringsRequest(
    val queryText: String,
    val window: PageWindow = PageWindow(),
)

data class ExportClassDexRequest(
    val className: String,
    val source: SourceLocator = SourceLocator(),
    val outputPath: String,
)

data class ExportClassSmaliRequest(
    val className: String,
    val source: SourceLocator = SourceLocator(),
    val outputPath: String,
    val autoUnicodeDecode: Boolean = true,
)

data class ExportClassJavaRequest(
    val className: String,
    val source: SourceLocator = SourceLocator(),
    val outputPath: String,
)

data class ExportMethodSmaliRequest(
    val methodSignature: String,
    val source: SourceLocator = SourceLocator(),
    val outputPath: String,
    val autoUnicodeDecode: Boolean = true,
    val mode: MethodSmaliMode = MethodSmaliMode.Snippet,
)

data class ExportResult(
    val outputPath: String,
)
