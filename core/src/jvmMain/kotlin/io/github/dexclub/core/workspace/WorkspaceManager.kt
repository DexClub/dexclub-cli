package io.github.dexclub.core.workspace

import io.github.dexclub.core.DexEngine
import io.github.dexclub.core.model.DexArchiveInfo
import io.github.dexclub.core.model.DexClassHit
import io.github.dexclub.core.model.DexExportResult
import io.github.dexclub.core.model.DexFieldHit
import io.github.dexclub.core.model.DexMethodHit
import io.github.dexclub.core.parseCoreJson
import io.github.dexclub.core.request.DexClassQueryRequest
import io.github.dexclub.core.request.DexExportRequest
import io.github.dexclub.core.request.DexFieldQueryRequest
import io.github.dexclub.core.request.DexMethodQueryRequest
import io.github.dexclub.core.request.JavaExportRequest
import io.github.dexclub.core.request.SmaliExportRequest
import io.github.dexclub.core.toCoreJsonString
import io.github.dexclub.utils.SignatureUtils
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.ResourceType
import java.io.File
import java.nio.charset.CharacterCodingException
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipFile

class WorkspaceManager(
    private val workspaceDir: File,
    private val toolVersion: String = "dev",
    private val nowProvider: () -> Instant = { Instant.now() },
    private val workspaceIdProvider: () -> String = { UUID.randomUUID().toString() },
) {
    private val stateRoot: File
        get() = workspaceDir.resolve(".dexclub-cli")

    private val workspaceFile: File
        get() = stateRoot.resolve("workspace.json")

    private val cacheRoot: File
        get() = stateRoot.resolve("cache/v1")

    private val runsRoot: File
        get() = stateRoot.resolve("runs/v1")

    fun init(
        rawInputs: List<String>,
        requestedType: WorkspaceInputKind?,
    ): WorkspaceMetadata {
        val now = nowProvider().toString()
        val existing = loadOrNull()
        val resolvedInput = resolveInput(
            rawInputs = rawInputs,
            requestedType = requestedType,
        )
        val binding = WorkspaceInputBinding(
            resolvedEntries = resolvedInput.resolvedEntries,
        )
        when {
            existing == null && hasLegacyDerivedState() -> rebuildDerivedState()
            existing != null && shouldRebuildDerivedState(
                existing = existing,
                currentBinding = binding,
                currentType = resolvedInput.type,
                currentToolVersion = toolVersion,
            ) -> rebuildDerivedState()
        }
        val metadata = WorkspaceMetadata(
            workspaceId = existing?.workspaceId ?: workspaceIdProvider(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            toolVersion = toolVersion,
            input = WorkspaceInputSnapshot(
                type = resolvedInput.type,
                binding = binding,
                fingerprint = resolvedInput.bindingFingerprint,
            ),
        )
        cacheRoot.mkdirs()
        runsRoot.mkdirs()
        workspaceFile.parentFile.mkdirs()
        workspaceFile.writeText(metadata.toCoreJsonString(), Charsets.UTF_8)
        return metadata
    }

    fun load(): WorkspaceMetadata {
        require(workspaceFile.isFile) {
            "工作区未初始化: ${workspaceDir.absolutePath}"
        }
        return workspaceFile.readText(Charsets.UTF_8).parseCoreJson()
    }

    fun loadOrNull(): WorkspaceMetadata? {
        if (!workspaceFile.isFile) return null
        return load()
    }

    fun status(): WorkspaceStatus {
        val metadata = loadReady()
        return WorkspaceStatus(
            metadata = metadata,
            capabilities = capabilities(metadata),
            cachePresent = cacheRoot.exists(),
            runsPresent = runsRoot.exists(),
        )
    }

    fun capabilities(): WorkspaceCapabilities = capabilities(loadReady())

    fun inspect(): DexArchiveInfo {
        val metadata = loadReady()
        return DexEngine(engineInputs(metadata)).use { engine ->
            engine.inspect()
        }
    }

    fun findClassHits(request: DexClassQueryRequest): List<DexClassHit> {
        val metadata = loadReady()
        return DexEngine(engineInputs(metadata)).use { engine ->
            engine.findClassHits(request)
        }
    }

    fun findMethodHits(request: DexMethodQueryRequest): List<DexMethodHit> {
        val metadata = loadReady()
        return DexEngine(engineInputs(metadata)).use { engine ->
            engine.findMethodHits(request)
        }
    }

    fun findFieldHits(request: DexFieldQueryRequest): List<DexFieldHit> {
        val metadata = loadReady()
        return DexEngine(engineInputs(metadata)).use { engine ->
            engine.findFieldHits(request)
        }
    }

    suspend fun exportDex(
        className: String,
        outputPath: String,
    ): DexExportResult {
        val metadata = loadReady()
        val exportInputs = exportEngineInputs(metadata)
        val sourceDexPath = resolveSourceDexPath(
            metadata = metadata,
            className = className,
            exportInputs = exportInputs,
        )
        return DexEngine(exportInputs).use { engine ->
            engine.exportDex(
                DexExportRequest(
                    className = className,
                    sourceDexPath = sourceDexPath,
                    outputPath = outputPath,
                ),
            )
        }
    }

    suspend fun exportSmali(
        className: String,
        outputPath: String,
        autoUnicodeDecode: Boolean = true,
    ): DexExportResult {
        val metadata = loadReady()
        val exportInputs = exportEngineInputs(metadata)
        val sourceDexPath = resolveSourceDexPath(
            metadata = metadata,
            className = className,
            exportInputs = exportInputs,
        )
        return DexEngine(exportInputs).use { engine ->
            engine.exportSmali(
                SmaliExportRequest(
                    className = className,
                    sourceDexPath = sourceDexPath,
                    outputPath = outputPath,
                ).copy(
                    config = io.github.dexclub.core.config.SmaliRenderConfig(
                        autoUnicodeDecode = autoUnicodeDecode,
                    ),
                ),
            )
        }
    }

    suspend fun exportJava(
        className: String,
        outputPath: String,
    ): DexExportResult {
        val metadata = loadReady()
        val exportInputs = exportEngineInputs(metadata)
        val sourceDexPath = resolveSourceDexPath(
            metadata = metadata,
            className = className,
            exportInputs = exportInputs,
        )
        return DexEngine(exportInputs).use { engine ->
            engine.exportJava(
                JavaExportRequest(
                    className = className,
                    sourceDexPath = sourceDexPath,
                    outputPath = outputPath,
                ),
            )
        }
    }

    fun manifest(): WorkspaceManifest {
        val metadata = loadReady()
        requireWorkspaceCapability(
            metadata = metadata,
            capability = "manifest",
            supported = metadata.input.type == WorkspaceInputKind.Apk,
        )
        return WorkspaceManifest(
            manifest = decodeManifestText(
                apkFile = File(metadata.input.binding.resolvedEntries.single()),
            ),
        )
    }

    fun resources(): WorkspaceResourceListing {
        val metadata = loadReady()
        requireWorkspaceCapability(
            metadata = metadata,
            capability = "res",
            supported = metadata.input.type == WorkspaceInputKind.Apk,
        )
        val apkFile = File(metadata.input.binding.resolvedEntries.single())
        val resourceEntries = mutableListOf<WorkspaceResourceEntry>()
        var resourcesArscPresent = false
        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    when {
                        entry.name == "resources.arsc" -> resourcesArscPresent = true
                        entry.name.startsWith("res/") -> resourceEntries += WorkspaceResourceEntry(entry.name)
                    }
                }
        }
        return WorkspaceResourceListing(
            resourcesArscPresent = resourcesArscPresent,
            entries = resourceEntries.sortedBy(WorkspaceResourceEntry::path),
        )
    }

    private fun capabilities(metadata: WorkspaceMetadata): WorkspaceCapabilities {
        val supportsResources = metadata.input.type == WorkspaceInputKind.Apk
        return WorkspaceCapabilities(
            manifest = supportsResources,
            res = supportsResources,
        )
    }

    private fun requireWorkspaceCapability(
        metadata: WorkspaceMetadata,
        capability: String,
        supported: Boolean,
    ) {
        require(supported) {
            "当前工作区输入类型为 ${metadata.input.type.name.lowercase()}，不支持该能力: $capability"
        }
    }

    private fun engineInputs(metadata: WorkspaceMetadata): List<String> {
        return when (metadata.input.type) {
            WorkspaceInputKind.Apk -> listOf(metadata.input.binding.resolvedEntries.single())
            WorkspaceInputKind.Dex,
            WorkspaceInputKind.Dexs,
            -> metadata.input.binding.resolvedEntries
        }
    }

    private fun resolveSourceDexPath(
        metadata: WorkspaceMetadata,
        className: String,
        exportInputs: List<String>,
    ): String {
        return when (metadata.input.type) {
            WorkspaceInputKind.Dex -> metadata.input.binding.resolvedEntries.single()
            WorkspaceInputKind.Dexs -> resolveUniqueDexPath(
                dexInputs = exportInputs,
                className = className,
                workspaceType = metadata.input.type,
            )

            WorkspaceInputKind.Apk -> resolveUniqueDexPath(
                dexInputs = exportInputs,
                className = className,
                workspaceType = metadata.input.type,
            )
        }
    }

    private fun exportEngineInputs(metadata: WorkspaceMetadata): List<String> {
        return when (metadata.input.type) {
            WorkspaceInputKind.Apk -> ensureExtractedApkDexPaths(metadata)
            WorkspaceInputKind.Dex,
            WorkspaceInputKind.Dexs,
            -> metadata.input.binding.resolvedEntries
        }
    }

    private fun resolveUniqueDexPath(
        dexInputs: List<String>,
        className: String,
        workspaceType: WorkspaceInputKind,
    ): String {
        val expectedSignature = SignatureUtils.typeSignature(className)
        val candidatePaths = DexEngine(dexInputs).use { engine ->
            engine.indexedClasses()
                .filter { it.signature == expectedSignature }
                .map { it.dexAbsolutePath }
                .distinct()
                .toList()
        }
        return when (candidatePaths.size) {
            1 -> candidatePaths.single()
            0 -> error("当前工作区输入类型为 ${workspaceType.name.lowercase()}，未能解析到目标类所在的唯一 source dex: $className")
            else -> error("当前工作区输入类型为 ${workspaceType.name.lowercase()}，导出前需要先解析到唯一 source dex，但当前命中多个 dex: $className")
        }
    }

    private fun ensureExtractedApkDexPaths(metadata: WorkspaceMetadata): List<String> {
        require(metadata.input.type == WorkspaceInputKind.Apk)
        val apkFile = File(metadata.input.binding.resolvedEntries.single())
        val extractedRoot = cacheRoot.resolve("inputs/apk/${buildDerivedStateKey(metadata)}/extracted-dex")
        pruneSiblingApkDerivedCaches(extractedRoot.parentFile)
        extractedRoot.mkdirs()
        val entryNames = ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { it.endsWith(".dex", ignoreCase = true) }
                .sortedWith(compareBy<String>({ dexNameSortKey(it).first }, { dexNameSortKey(it).second }, { it }))
                .toList()
        }
        require(entryNames.isNotEmpty()) { "APK 中未找到任何 dex: ${apkFile.absolutePath}" }
        ZipFile(apkFile).use { zip ->
            entryNames.forEach { entryName ->
                val target = extractedRoot.resolve(File(entryName).name)
                if (!target.isFile) {
                    zip.getInputStream(zip.getEntry(entryName)).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        return entryNames.map { extractedRoot.resolve(File(it).name).absolutePath }
    }

    private fun decodeManifestText(apkFile: File): String {
        val manifestBytes = ZipFile(apkFile).use { zip ->
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
                ?: error("APK 中缺少 AndroidManifest.xml: ${apkFile.absolutePath}")
            zip.getInputStream(manifestEntry).use { it.readBytes() }
        }
        decodeUtf8OrNull(manifestBytes)?.trim()
            ?.takeIf { it.startsWith("<") }
            ?.let { return it }

        val outputDir = Files.createTempDirectory("dexclub-workspace-manifest").toFile()
        return try {
            val args = JadxArgs().apply {
                setInputFile(apkFile)
                outDir = outputDir
            }
            JadxDecompiler(args).use { decompiler ->
                decompiler.load()
                val manifestResource = decompiler.resources.firstOrNull { it.type == ResourceType.MANIFEST }
                    ?: error("APK 中未解析到 manifest 资源: ${apkFile.absolutePath}")
                manifestResource.loadContent().text.codeStr
            }
        } finally {
            outputDir.deleteRecursively()
        }
    }

    private fun resolveInput(
        rawInputs: List<String>,
        requestedType: WorkspaceInputKind?,
    ): ResolvedWorkspaceInput {
        val normalizedInputs = rawInputs.map(String::trim)
            .filter(String::isNotEmpty)
        require(normalizedInputs.isNotEmpty()) { "缺少参数 --input" }

        val files = normalizedInputs.map { path ->
            val file = File(path).absoluteFile
            require(file.exists()) { "输入不存在: ${file.absolutePath}" }
            file
        }
        val resolvedType = requestedType ?: inferWorkspaceType(files)
        val resolvedEntries = when (resolvedType) {
            WorkspaceInputKind.Apk -> {
                require(files.size == 1) { "apk 工作区仅支持单个 apk 输入" }
                val apkFile = files.single()
                require(apkFile.isFile && apkFile.extension.equals("apk", ignoreCase = true)) {
                    "apk 工作区要求单个 apk 文件输入"
                }
                listOf(apkFile.absolutePath)
            }

            WorkspaceInputKind.Dex -> {
                require(files.size == 1) { "dex 工作区仅支持单个 dex 输入" }
                val dexFile = files.single()
                require(dexFile.isFile && DexEngine.isDex(dexFile.absolutePath)) {
                    "dex 工作区要求单个有效 dex 文件输入"
                }
                listOf(dexFile.absolutePath)
            }

            WorkspaceInputKind.Dexs -> resolveDexSetEntries(files)
        }

        return ResolvedWorkspaceInput(
            type = resolvedType,
            resolvedEntries = resolvedEntries,
            bindingFingerprint = buildBindingFingerprint(
                type = resolvedType,
                resolvedEntries = resolvedEntries,
            ),
        )
    }

    private fun hasLegacyDerivedState(): Boolean {
        return cacheRoot.exists() || runsRoot.exists()
    }

    private fun loadReady(): WorkspaceMetadata {
        val metadata = load()
        return if (shouldRebuildDerivedState(
                existing = metadata,
                currentBinding = metadata.input.binding,
                currentType = metadata.input.type,
                currentToolVersion = toolVersion,
            )
        ) {
            rebuildDerivedState()
            val refreshed = metadata.copy(
                schemaVersion = WorkspaceMetadata.CURRENT_SCHEMA_VERSION,
                layoutVersion = WorkspaceMetadata.CURRENT_LAYOUT_VERSION,
                updatedAt = nowProvider().toString(),
                toolVersion = toolVersion,
            )
            workspaceFile.writeText(refreshed.toCoreJsonString(), Charsets.UTF_8)
            refreshed
        } else {
            metadata
        }
    }

    private fun shouldRebuildDerivedState(
        existing: WorkspaceMetadata,
        currentBinding: WorkspaceInputBinding,
        currentType: WorkspaceInputKind,
        currentToolVersion: String,
    ): Boolean {
        return existing.schemaVersion != WorkspaceMetadata.CURRENT_SCHEMA_VERSION ||
            existing.layoutVersion != WorkspaceMetadata.CURRENT_LAYOUT_VERSION ||
            existing.toolVersion != currentToolVersion ||
            existing.input.type != currentType ||
            existing.input.binding != currentBinding
    }

    private fun rebuildDerivedState() {
        cacheRoot.deleteRecursively()
        runsRoot.deleteRecursively()
    }

    private fun pruneSiblingApkDerivedCaches(currentRoot: File) {
        val parent = currentRoot.parentFile ?: return
        if (!parent.isDirectory) return
        parent.listFiles()
            ?.filter(File::isDirectory)
            ?.filter { it.absolutePath != currentRoot.absolutePath }
            ?.forEach(File::deleteRecursively)
    }

    private fun inferWorkspaceType(files: List<File>): WorkspaceInputKind {
        return when {
            files.size == 1 && files.single().isFile && files.single().extension.equals("apk", ignoreCase = true) ->
                WorkspaceInputKind.Apk

            files.size == 1 && files.single().isFile && DexEngine.isDex(files.single().absolutePath) ->
                WorkspaceInputKind.Dex

            files.size == 1 && files.single().isDirectory ->
                WorkspaceInputKind.Dexs

            files.all { it.isFile && DexEngine.isDex(it.absolutePath) } ->
                WorkspaceInputKind.Dexs

            else -> error("无法自动识别工作区输入类型，请显式指定 --type apk|dex|dexs")
        }
    }

    private fun resolveDexSetEntries(files: List<File>): List<String> {
        val resolved = if (files.size == 1 && files.single().isDirectory) {
            files.single()
                .walkTopDown()
                .filter { it.isFile && it.extension.equals("dex", ignoreCase = true) }
                .map { it.absoluteFile.absolutePath }
                .toList()
        } else {
            files.map { file ->
                require(file.isFile && DexEngine.isDex(file.absolutePath)) {
                    "dexs 工作区仅支持 dex 目录或 dex 文件列表"
                }
                file.absolutePath
            }
        }

        val unique = resolved.distinct()
        require(unique.isNotEmpty()) { "dexs 工作区未解析到任何有效 dex" }
        return unique.sortedWith(
            compareBy<String>({ dexNameSortKey(File(it).name).first }, { dexNameSortKey(File(it).name).second }, { it }),
        )
    }

    private fun buildBindingFingerprint(
        type: WorkspaceInputKind,
        resolvedEntries: List<String>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(type.name.lowercase().toByteArray(Charsets.UTF_8))
        resolvedEntries.forEach { path ->
            digest.update(0.toByte())
            digest.update(path.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun buildContentFingerprint(
        type: WorkspaceInputKind,
        resolvedEntries: List<String>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(type.name.lowercase().toByteArray(Charsets.UTF_8))
        resolvedEntries.forEach { path ->
            val file = File(path)
            digest.update(0.toByte())
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(sha256(file).toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun buildDerivedStateKey(metadata: WorkspaceMetadata): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(metadata.schemaVersion.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(metadata.layoutVersion.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(metadata.toolVersion.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(metadata.input.fingerprint.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(
            buildContentFingerprint(
                type = metadata.input.type,
                resolvedEntries = metadata.input.binding.resolvedEntries,
            ).toByteArray(Charsets.UTF_8),
        )
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun decodeUtf8OrNull(bytes: ByteArray): String? {
        val decoder: CharsetDecoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun dexNameSortKey(name: String): Pair<Int, String> {
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

    private data class ResolvedWorkspaceInput(
        val type: WorkspaceInputKind,
        val resolvedEntries: List<String>,
        val bindingFingerprint: String,
    )
}
