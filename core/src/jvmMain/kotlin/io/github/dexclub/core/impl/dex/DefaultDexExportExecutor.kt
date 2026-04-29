package io.github.dexclub.core.impl.dex

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import io.github.dexclub.core.api.dex.DexExportError
import io.github.dexclub.core.api.dex.DexExportErrorReason
import io.github.dexclub.core.api.dex.ExportClassDexRequest
import io.github.dexclub.core.api.dex.ExportClassJavaRequest
import io.github.dexclub.core.api.dex.ExportClassSmaliRequest
import io.github.dexclub.core.api.dex.ExportMethodDexRequest
import io.github.dexclub.core.api.dex.ExportMethodJavaRequest
import io.github.dexclub.core.api.dex.ExportMethodSmaliRequest
import io.github.dexclub.core.api.dex.ExportResult
import io.github.dexclub.core.api.shared.MethodSmaliMode
import io.github.dexclub.core.api.shared.SourceLocator
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.ClassSourceMapRecord
import io.github.dexclub.core.impl.workspace.model.ClassSourceRefRecord
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.core.impl.workspace.store.WorkspaceStore
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.zip.ZipFile

internal class DefaultDexExportExecutor(
    private val store: WorkspaceStore,
    private val toolVersion: String,
) : DexExportExecutor {
    private val jadxDecompilerService = JadxDecompilerService()

    override fun exportClassDex(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportClassDexRequest,
    ): ExportResult {
        val workdirPath = Paths.get(workspace.workdir)
        val match = resolveUniqueClassSource(
            workdirPath = workdirPath,
            inventory = inventory,
            className = request.className,
            source = request.source,
            workspace = workspace,
        )
        val outputPath = writeSingleClassDex(
            classDef = match.classDef,
            outputPath = request.outputPath,
        )
        return ExportResult(outputPath = outputPath)
    }

    override fun exportClassSmali(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportClassSmaliRequest,
    ): ExportResult {
        val workdirPath = Paths.get(workspace.workdir)
        val match = resolveUniqueClassSource(
            workdirPath = workdirPath,
            inventory = inventory,
            className = request.className,
            source = request.source,
            workspace = workspace,
        )
        val outputPath = renderClassSmali(
            classDef = match.classDef,
            outputPath = request.outputPath,
            autoUnicodeDecode = request.autoUnicodeDecode,
        )
        return ExportResult(outputPath = outputPath)
    }

    override fun exportClassJava(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportClassJavaRequest,
    ): ExportResult {
        val workdirPath = Paths.get(workspace.workdir)
        val match = resolveUniqueClassSource(
            workdirPath = workdirPath,
            inventory = inventory,
            className = request.className,
            source = request.source,
            workspace = workspace,
        )
        return ExportResult(
            outputPath = decompileSingleClassDefToJava(
                workspace = workspace,
                classDef = match.classDef,
                outputPath = request.outputPath,
            ),
        )
    }

    override fun exportMethodSmali(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportMethodSmaliRequest,
    ): ExportResult {
        val workdirPath = Paths.get(workspace.workdir)
        val signature = parseMethodSignature(request.methodSignature)
        val methodOnlyClassDef = resolveMethodOnlyClassDef(
            workdirPath = workdirPath,
            inventory = inventory,
            methodSignature = request.methodSignature,
            source = request.source,
            workspace = workspace,
        )
        val classSmali = renderClassSmaliText(
            classDef = methodOnlyClassDef,
            autoUnicodeDecode = request.autoUnicodeDecode,
        )
        val methodBlock = extractMethodBlock(
            classSmali = classSmali,
            methodName = signature.methodName,
            descriptor = signature.descriptor,
        )
        val outputText = when (request.mode) {
            MethodSmaliMode.Snippet -> ensureTrailingNewline(methodBlock)
            MethodSmaliMode.Class -> buildMethodClassShell(classSmali, methodBlock)
        }
        return ExportResult(
            outputPath = writeTextOutput(
                outputPath = request.outputPath,
                text = outputText,
            ),
        )
    }

    override fun exportMethodDex(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportMethodDexRequest,
    ): ExportResult {
        val workdirPath = Paths.get(workspace.workdir)
        val methodOnlyClassDef = resolveMethodOnlyClassDef(
            workdirPath = workdirPath,
            inventory = inventory,
            methodSignature = request.methodSignature,
            source = request.source,
            workspace = workspace,
        )
        return ExportResult(
            outputPath = writeSingleClassDex(
                classDef = methodOnlyClassDef,
                outputPath = request.outputPath,
            ),
        )
    }

    override fun exportMethodJava(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: ExportMethodJavaRequest,
    ): ExportResult {
        val workdirPath = Paths.get(workspace.workdir)
        val methodOnlyClassDef = resolveMethodOnlyClassDef(
            workdirPath = workdirPath,
            inventory = inventory,
            methodSignature = request.methodSignature,
            source = request.source,
            workspace = workspace,
        )
        return ExportResult(
            outputPath = decompileSingleClassDefToJava(
                workspace = workspace,
                classDef = methodOnlyClassDef,
                outputPath = request.outputPath,
            ),
        )
    }

    private fun resolveMethodOnlyClassDef(
        workdirPath: Path,
        inventory: MaterialInventory,
        methodSignature: String,
        source: SourceLocator,
        workspace: WorkspaceContext,
    ): ClassDef {
        val signature = parseMethodSignature(methodSignature)
        val match = resolveUniqueClassSource(
            workdirPath = workdirPath,
            inventory = inventory,
            className = signature.classSignature,
            source = source,
            workspace = workspace,
        )
        val method = match.classDef.methods.firstOrNull {
            it.name == signature.methodName && methodDescriptorOf(it) == signature.descriptor
        } ?: throw DexExportError(
            reason = DexExportErrorReason.MethodNotFound,
            message = buildString {
                append("method not found: ")
                append(methodSignature)
                append(" in class ")
                append(signature.classSignature)
                val sourceDescription = source.describe()
                if (sourceDescription != null) {
                    append(" (")
                    append(sourceDescription)
                    append(')')
                }
            },
        )
        return buildMethodOnlyClassDef(
            classDef = match.classDef,
            method = method,
        )
    }

    private fun buildMethodOnlyClassDef(classDef: ClassDef, method: Method): ClassDef {
        val immutableMethod = ImmutableMethod.of(method)
        return ImmutableClassDef(
            classDef.type,
            classDef.accessFlags,
            classDef.superclass,
            classDef.interfaces,
            classDef.sourceFile,
            classDef.annotations,
            emptyList(),
            emptyList(),
            if (method in classDef.directMethods) listOf(immutableMethod) else emptyList(),
            if (method in classDef.virtualMethods) listOf(immutableMethod) else emptyList(),
        )
    }

    private fun decompileSingleClassDefToJava(
        workspace: WorkspaceContext,
        classDef: ClassDef,
        outputPath: String,
    ): String {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        val exportTempRoot = Paths.get(store.exportTempDir(workspace.workdir, workspace.activeTargetId))
        Files.createDirectories(exportTempRoot)
        val sessionDir = Files.createTempDirectory(exportTempRoot, "${outputFile.nameWithoutExtension}-").toFile()
        val tempDex = File(sessionDir, "input.dex")
        val sanitizedClassDef = sanitizeClassDefForJavaDecompile(classDef)
        return try {
            writeSingleClassDex(
                classDef = sanitizedClassDef,
                outputPath = tempDex.absolutePath,
            )
            jadxDecompilerService.decompileDexToJavaSource(
                dexPath = tempDex.absolutePath,
                outputPath = outputPath,
                tempDirectory = sessionDir,
            )
        } finally {
            sessionDir.deleteRecursively()
        }
    }

    private fun sanitizeClassDefForJavaDecompile(classDef: ClassDef): ClassDef =
        ImmutableClassDef(
            classDef.type,
            classDef.accessFlags,
            classDef.superclass,
            classDef.interfaces,
            null,
            emptySet(),
            classDef.staticFields.map(::sanitizeFieldForJavaDecompile),
            classDef.instanceFields.map(::sanitizeFieldForJavaDecompile),
            classDef.directMethods.map(::sanitizeMethodForJavaDecompile),
            classDef.virtualMethods.map(::sanitizeMethodForJavaDecompile),
        )

    private fun sanitizeFieldForJavaDecompile(field: com.android.tools.smali.dexlib2.iface.Field) =
        ImmutableField(
            field.definingClass,
            field.name,
            field.type,
            field.accessFlags,
            field.initialValue,
            emptySet(),
            emptySet(),
        )

    private fun sanitizeMethodForJavaDecompile(method: Method): Method {
        val implementation = method.implementation
        val sanitizedImplementation = if (implementation == null) {
            null
        } else {
            ImmutableMethodImplementation(
                implementation.registerCount,
                implementation.instructions,
                implementation.tryBlocks,
                emptyList(),
            )
        }
        return ImmutableMethod(
            method.definingClass,
            method.name,
            method.parameters,
            method.returnType,
            method.accessFlags,
            emptySet(),
            emptySet(),
            sanitizedImplementation,
        )
    }

    private fun resolveUniqueClassSource(
        workdirPath: Path,
        inventory: MaterialInventory,
        className: String,
        source: SourceLocator,
        workspace: WorkspaceContext? = null,
    ): LocatedClass {
        val descriptor = toTypeSignature(className)
        val candidates = resolveCandidates(
            workdirPath = workdirPath,
            inventory = inventory,
            source = preferredSourceLocator(
                workspace = workspace,
                inventory = inventory,
                classSignature = descriptor,
                explicitSource = source,
            ),
        )
        val matches = candidates.mapNotNull { candidate ->
            candidate.loadClassDef(descriptor)?.let { classDef ->
                LocatedClass(
                    sourcePath = candidate.sourcePath,
                    sourceEntry = candidate.sourceEntry,
                    classDef = classDef,
                )
            }
        }

        return when (matches.size) {
            1 -> matches.single()
            0 -> throw DexExportError(
                reason = DexExportErrorReason.ClassNotFound,
                message = buildString {
                    append("class not found")
                    append(": ")
                    append(className)
                    val sourceDescription = source.describe()
                    if (sourceDescription != null) {
                        append(" (")
                        append(sourceDescription)
                        append(')')
                    }
                },
            )

            else -> throw DexExportError(
                reason = DexExportErrorReason.AmbiguousClass,
                message = "class resolves to multiple dex sources; specify --source-path" +
                    if (matches.any { it.sourceEntry != null }) " and --source-entry" else "" +
                    ": $className",
            )
        }
    }

    private fun resolveCandidates(
        workdirPath: Path,
        inventory: MaterialInventory,
        source: SourceLocator,
    ): List<DexSourceCandidate> {
        if (source.sourceEntry != null && source.sourcePath == null) {
            throw DexExportError(
                reason = DexExportErrorReason.InvalidSourceLocator,
                message = "sourceEntry requires sourcePath",
            )
        }

        val sourcePath = source.sourcePath
        if (sourcePath != null) {
            if (sourcePath in inventory.dexFiles) {
                if (source.sourceEntry != null) {
                    throw DexExportError(
                        reason = DexExportErrorReason.InvalidSourceLocator,
                        message = "sourceEntry is only valid for container sources: $sourcePath",
                    )
                }
                return listOf(dexFileCandidate(workdirPath, sourcePath))
            }
            if (sourcePath in inventory.apkFiles) {
                return apkEntryCandidates(workdirPath, sourcePath, source.sourceEntry)
            }
            throw DexExportError(
                reason = DexExportErrorReason.SourceNotFound,
                message = "sourcePath is not part of the current workspace: $sourcePath",
            )
        }

        return buildList {
            inventory.apkFiles.forEach { apkPath ->
                addAll(apkEntryCandidates(workdirPath, apkPath, null))
            }
            inventory.dexFiles.forEach { dexPath ->
                add(dexFileCandidate(workdirPath, dexPath))
            }
        }
    }

    private fun preferredSourceLocator(
        workspace: WorkspaceContext?,
        inventory: MaterialInventory,
        classSignature: String,
        explicitSource: SourceLocator,
    ): SourceLocator {
        if (explicitSource.sourcePath != null || explicitSource.sourceEntry != null || workspace == null) {
            return explicitSource
        }

        val currentFingerprint = workspace.snapshot.contentFingerprint
        val classSourceMap = store.loadClassSourceMap(workspace.workdir, workspace.activeTargetId)
            ?.takeIf {
                it.toolVersion == toolVersion &&
                    it.contentFingerprint == currentFingerprint
            }
            ?: buildClassSourceMap(
                workspace = workspace,
                inventory = inventory,
                contentFingerprint = currentFingerprint,
            ).also {
                store.saveClassSourceMap(workspace.workdir, workspace.activeTargetId, it)
            }

        val source = classSourceMap.sourceOf(classSignature) ?: return explicitSource
        return SourceLocator(
            sourcePath = source.sourcePath,
            sourceEntry = source.sourceEntry,
        )
    }

    private fun buildClassSourceMap(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        contentFingerprint: String,
    ): ClassSourceMapRecord {
        val workdirPath = Paths.get(workspace.workdir)
        val occurrences = linkedMapOf<String, MutableSet<ClassSourceRefKey>>()

        inventory.dexFiles.forEach { dexPath ->
            val dexFile = DexBackedDexFile(
                Opcodes.getDefault(),
                Files.readAllBytes(workdirPath.resolve(dexPath).normalize()),
            )
            dexFile.classes.forEach { classDef ->
                occurrences.getOrPut(classDef.type) { linkedSetOf() }.add(
                    ClassSourceRefKey(sourcePath = dexPath, sourceEntry = null),
                )
            }
        }

        inventory.apkFiles.forEach { apkPath ->
            val apk = workdirPath.resolve(apkPath).normalize()
            ZipFile(apk.toFile()).use { zip ->
                zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .filter { it.name.endsWith(".dex", ignoreCase = true) }
                    .forEach { entry ->
                        val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                        val dexFile = DexBackedDexFile(Opcodes.getDefault(), dexBytes)
                        dexFile.classes.forEach { classDef ->
                            occurrences.getOrPut(classDef.type) { linkedSetOf() }.add(
                                ClassSourceRefKey(sourcePath = apkPath, sourceEntry = entry.name),
                            )
                        }
                    }
            }
        }

        val sourceIds = linkedMapOf<ClassSourceRefKey, Int>()
        val sources = mutableListOf<ClassSourceRefRecord>()

        fun sourceIdOf(source: ClassSourceRefKey): Int =
            sourceIds.getOrPut(source) {
                val id = sources.size
                sources += ClassSourceRefRecord(
                    id = id,
                    sourcePath = source.sourcePath,
                    sourceEntry = source.sourceEntry,
                )
                id
            }

        val mappings = occurrences.mapNotNull { (classSignature, sourceRefs) ->
            sourceRefs.singleOrNull()?.let { classSignature to sourceIdOf(it) }
        }.toMap(linkedMapOf())

        return ClassSourceMapRecord(
            generatedAt = Instant.now().toString(),
            targetId = workspace.activeTargetId,
            toolVersion = toolVersion,
            contentFingerprint = contentFingerprint,
            sources = sources,
            mappings = mappings,
        )
    }

    private fun dexFileCandidate(workdirPath: Path, relativeDexPath: String): DexSourceCandidate =
        DexSourceCandidate(
            sourcePath = relativeDexPath,
            sourceEntry = null,
            dexBytes = Files.readAllBytes(workdirPath.resolve(relativeDexPath).normalize()),
        )

    private fun apkEntryCandidates(
        workdirPath: Path,
        relativeApkPath: String,
        sourceEntry: String?,
    ): List<DexSourceCandidate> {
        val apkPath = workdirPath.resolve(relativeApkPath).normalize()
        ZipFile(apkPath.toFile()).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.endsWith(".dex", ignoreCase = true) }
                .sortedWith(compareBy({ dexEntrySortKey(it.name).first }, { dexEntrySortKey(it.name).second }, { it.name }))
                .toList()

            if (sourceEntry != null) {
                val entry = dexEntries.firstOrNull { it.name == sourceEntry }
                    ?: throw DexExportError(
                        reason = DexExportErrorReason.SourceNotFound,
                        message = "sourceEntry not found in sourcePath: $relativeApkPath!$sourceEntry",
                    )
                return listOf(
                    DexSourceCandidate(
                        sourcePath = relativeApkPath,
                        sourceEntry = entry.name,
                        dexBytes = zip.getInputStream(entry).use { it.readBytes() },
                    ),
                )
            }

            if (dexEntries.isEmpty()) {
                throw DexExportError(
                    reason = DexExportErrorReason.SourceNotFound,
                    message = "sourcePath does not contain any dex entries: $relativeApkPath",
                )
            }

            return dexEntries.map { entry ->
                DexSourceCandidate(
                    sourcePath = relativeApkPath,
                    sourceEntry = entry.name,
                    dexBytes = zip.getInputStream(entry).use { it.readBytes() },
                )
            }
        }
    }

    private fun renderClassSmali(
        classDef: ClassDef,
        outputPath: String,
        autoUnicodeDecode: Boolean,
    ): String {
        val text = renderClassSmaliText(classDef, autoUnicodeDecode)
        return writeTextOutput(outputPath, text)
    }

    private fun writeSingleClassDex(
        classDef: ClassDef,
        outputPath: String,
    ): String {
        val dataStore = MemoryDataStore()
        val dexPool = DexPool(Opcodes.getDefault())
        dexPool.internClass(classDef)
        dexPool.writeTo(dataStore)
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(dataStore.data)
        return outputFile.absolutePath
    }

    private fun renderClassSmaliText(
        classDef: ClassDef,
        autoUnicodeDecode: Boolean,
    ): String {
        val options = BaksmaliOptions().apply {
            apiLevel = Opcodes.getDefault().api
            parameterRegisters = true
            localsDirective = true
            debugInfo = true
            accessorComments = true
        }
        val text = StringWriter()
        val writer: BaksmaliWriter = if (autoUnicodeDecode) {
            UnescapedUnicodeBaksmaliWriter(text)
        } else {
            BaksmaliWriter(text)
        }
        ClassDefinition(options, classDef).writeTo(writer)
        writer.flush()
        return text.toString()
    }

    private fun writeTextOutput(outputPath: String, text: String): String {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(text, Charsets.UTF_8)
        return outputFile.absolutePath
    }

    private fun dexEntrySortKey(name: String): Pair<Int, String> {
        if (name == "classes.dex") {
            return 1 to name
        }
        val suffix = name.removePrefix("classes").removeSuffix(".dex")
        return if (suffix.toIntOrNull() != null) {
            suffix.toInt() to name
        } else {
            Int.MAX_VALUE to name
        }
    }

    private fun SourceLocator.describe(): String? =
        when {
            sourcePath != null && sourceEntry != null -> "sourcePath=$sourcePath, sourceEntry=$sourceEntry"
            sourcePath != null -> "sourcePath=$sourcePath"
            else -> null
        }

    private fun parseMethodSignature(methodSignature: String): ParsedMethodSignature {
        val trimmed = methodSignature.trim()
        val arrowIndex = trimmed.indexOf("->")
        if (arrowIndex <= 0) {
            throw DexExportError(
                reason = DexExportErrorReason.InvalidMethodSignature,
                message = "invalid method signature: $methodSignature",
            )
        }
        val classPart = trimmed.substring(0, arrowIndex)
        val memberPart = trimmed.substring(arrowIndex + 2)
        val descriptorStart = memberPart.indexOf('(')
        if (descriptorStart <= 0 || !memberPart.contains(')')) {
            throw DexExportError(
                reason = DexExportErrorReason.InvalidMethodSignature,
                message = "invalid method signature: $methodSignature",
            )
        }
        val methodName = memberPart.substring(0, descriptorStart)
        val descriptor = memberPart.substring(descriptorStart)
        if (methodName.isBlank() || descriptor.endsWith(")")) {
            throw DexExportError(
                reason = DexExportErrorReason.InvalidMethodSignature,
                message = "invalid method signature: $methodSignature",
            )
        }
        return ParsedMethodSignature(
            classSignature = toTypeSignature(classPart),
            methodName = methodName,
            descriptor = descriptor,
        )
    }

    private fun methodDescriptorOf(method: Method): String =
        buildString {
            append('(')
            method.parameterTypes.forEach { append(it) }
            append(')')
            append(method.returnType)
        }

    private fun extractMethodBlock(
        classSmali: String,
        methodName: String,
        descriptor: String,
    ): String {
        val suffix = "$methodName$descriptor"
        val captured = mutableListOf<String>()
        var collecting = false
        for (line in classSmali.lineSequence()) {
            val trimmed = line.trimStart()
            if (!collecting) {
                if (trimmed.startsWith(".method ") && trimmed.removePrefix(".method ").endsWith(suffix)) {
                    collecting = true
                    captured += line
                }
                continue
            }

            captured += line
            if (trimmed == ".end method") {
                return captured.joinToString(separator = "\n")
            }
        }
        throw DexExportError(
            reason = DexExportErrorReason.MethodNotFound,
            message = "method block not found in rendered class: $methodName$descriptor",
        )
    }

    private fun buildMethodClassShell(classSmali: String, methodBlock: String): String {
        val header = mutableListOf<String>()
        for (line in classSmali.lineSequence()) {
            val trimmed = line.trimStart()
            if (
                trimmed.startsWith(".annotation ") ||
                trimmed.startsWith(".field ") ||
                trimmed.startsWith(".method ")
            ) {
                break
            }
            header += line
        }

        val headerText = header.joinToString(separator = "\n").trimEnd()
        return buildString {
            append(headerText)
            if (headerText.isNotEmpty()) {
                append("\n\n")
            }
            append(methodBlock.trimEnd())
            append('\n')
        }
    }

    private fun ensureTrailingNewline(text: String): String =
        if (text.endsWith('\n')) text else "$text\n"

    private data class DexSourceCandidate(
        val sourcePath: String,
        val sourceEntry: String?,
        val dexBytes: ByteArray,
    ) {
        fun loadClassDef(descriptor: String): ClassDef? {
            val dexFile = DexBackedDexFile(Opcodes.getDefault(), dexBytes)
            return dexFile.classes.firstOrNull { it.type == descriptor }
        }
    }

    private data class LocatedClass(
        val sourcePath: String,
        val sourceEntry: String?,
        val classDef: ClassDef,
    )

    private data class ParsedMethodSignature(
        val classSignature: String,
        val methodName: String,
        val descriptor: String,
    )

    private data class ClassSourceRefKey(
        val sourcePath: String,
        val sourceEntry: String?,
    )
}
