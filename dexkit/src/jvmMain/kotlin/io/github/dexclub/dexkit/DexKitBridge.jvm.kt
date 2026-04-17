package io.github.dexclub.dexkit

import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.FindField
import io.github.dexclub.dexkit.query.FindMethod
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.ClassDataList
import io.github.dexclub.dexkit.result.FieldData
import io.github.dexclub.dexkit.result.FieldDataList
import io.github.dexclub.dexkit.result.MethodData
import io.github.dexclub.dexkit.result.MethodDataList
import io.github.dexclub.dexkit.result.toClassDataList
import io.github.dexclub.dexkit.result.toFieldDataList
import io.github.dexclub.dexkit.result.toMethodDataList
import org.luckypray.dexkit.DexKitBridge as NativeDexKitBridge
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

actual class DexKitBridge {
    private var delegate: NativeDexKitBridge? = null

    actual constructor(dexPaths: List<String>) {
        require(dexPaths.isNotEmpty()) { "dexPaths 不能为空" }
        ensureLibraryLoaded()
        val files = dexPaths.map { File(it) }
        files.forEach { dexFile ->
            require(dexFile.exists()) { "dex 文件不存在: ${dexFile.absolutePath}" }
            require(dexFile.isFile) { "dex 路径必须是文件: ${dexFile.absolutePath}" }
        }
        delegate = if (files.size == 1 && files.first().extension.equals("apk", ignoreCase = true)) {
            NativeDexKitBridge.create(readDexBytesFromApk(files.first()))
        } else {
            NativeDexKitBridge.create(files.map { it.readBytes() }.toTypedArray())
        }
    }

    actual constructor(apkPath: String) {
        ensureLibraryLoaded()
        require(apkPath.isNotEmpty()) { "apkPath 不能为空" }
        val apkFile = File(apkPath)
        require(apkFile.exists()) { "apk 文件不存在: ${apkFile.absolutePath}" }
        require(apkFile.isFile) { "apk 路径必须是文件: ${apkFile.absolutePath}" }
        delegate = NativeDexKitBridge.create(apkFile.absolutePath)
    }

    actual constructor(dexBytesArray: Array<ByteArray>) {
        ensureLibraryLoaded()
        require(dexBytesArray.isNotEmpty()) { "dexBytesArray 不能为空" }
        delegate = NativeDexKitBridge.create(dexBytesArray)
    }

    actual val isValid: Boolean
        get() = delegate?.isValid == true

    actual fun getDexNum(): Int = ensureDelegate().getDexNum()

    actual fun setThreadNum(num: Int) = ensureDelegate().setThreadNum(num)

    actual fun initFullCache() = ensureDelegate().initFullCache()

    actual fun exportDexFile(outPath: String) {
        require(outPath.isNotEmpty()) { "outPath 不能为空" }
        ensureDelegate().exportDexFile(outPath)
    }

    actual fun findClass(query: FindClass): ClassDataList {
        val d = ensureDelegate()
        return d.findClass(query.toNative(d)).map { it.toKmpClassData() }.toClassDataList(this)
    }

    actual fun findMethod(query: FindMethod): MethodDataList {
        val d = ensureDelegate()
        return d.findMethod(query.toNative(d)).map { it.toKmpMethodData() }.toMethodDataList(this)
    }

    actual fun findField(query: FindField): FieldDataList {
        val d = ensureDelegate()
        return d.findField(query.toNative(d)).map { it.toKmpFieldData() }.toFieldDataList(this)
    }

    actual fun batchFindClassUsingStrings(query: BatchFindClassUsingStrings): Map<String, ClassDataList> {
        val d = ensureDelegate()
        return d.batchFindClassUsingStrings(query.toNative(d))
            .mapValues { (_, list) -> list.map { it.toKmpClassData() }.toClassDataList(this) }
    }

    actual fun batchFindMethodUsingStrings(query: BatchFindMethodUsingStrings): Map<String, MethodDataList> {
        val d = ensureDelegate()
        return d.batchFindMethodUsingStrings(query.toNative(d))
            .mapValues { (_, list) -> list.map { it.toKmpMethodData() }.toMethodDataList(this) }
    }

    actual fun getClassData(descriptor: String): ClassData? {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getClassData(descriptor)?.toKmpClassData()
    }

    actual fun getMethodData(descriptor: String): MethodData? {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getMethodData(descriptor)?.toKmpMethodData()
    }

    actual fun getFieldData(descriptor: String): FieldData? {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getFieldData(descriptor)?.toKmpFieldData()
    }

    actual fun getFieldReaders(descriptor: String): MethodDataList {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getFieldData(descriptor)
            ?.readers?.map { it.toKmpMethodData() }.orEmpty()
            .toMethodDataList(this)
    }

    actual fun getFieldWriters(descriptor: String): MethodDataList {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getFieldData(descriptor)
            ?.writers?.map { it.toKmpMethodData() }.orEmpty()
            .toMethodDataList(this)
    }

    actual fun getMethodCallers(descriptor: String): MethodDataList {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getMethodData(descriptor)
            ?.callers?.map { it.toKmpMethodData() }.orEmpty()
            .toMethodDataList(this)
    }

    actual fun getMethodInvokes(descriptor: String): MethodDataList {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getMethodData(descriptor)
            ?.invokes?.map { it.toKmpMethodData() }.orEmpty()
            .toMethodDataList(this)
    }

    actual fun close() {
        delegate?.close()
        delegate = null
    }

    private fun ensureDelegate(): NativeDexKitBridge =
        checkNotNull(delegate) { "DexKitBridge 未初始化，请传入有效的 dex/apk 数据" }

    private fun readDexBytesFromApk(apkFile: File): Array<ByteArray> {
        val dexEntries = ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.endsWith(".dex", ignoreCase = true) }
                .map { entry ->
                    zip.getInputStream(entry).use { input -> input.readBytes() }
                }
                .toList()
        }
        require(dexEntries.isNotEmpty()) { "apk 中未找到任何 dex: ${apkFile.absolutePath}" }
        return dexEntries.toTypedArray()
    }

    private companion object {
        private val lock = Any()

        @Volatile
        private var loaded = false

        private fun ensureLibraryLoaded() {
            if (loaded) return
            synchronized(lock) {
                if (loaded) return
                loadLibrary("dexkit")
                loaded = true
            }
        }

        @Suppress("UnsafeDynamicallyLoadedCode")
        private fun loadLibrary(name: String) {
            // 1. 显式配置目录（分发包 / 调试环境优先）
            configuredNativeLibraryDirPath()?.let { configuredDir ->
                if (loadFromDirectory(configuredDir, name)) return
            }

            // 2. JAR 内 natives/ 资源（打包成 exe/msi/dmg/deb 时的主路径）
            if (loadFromClasspathResource(name)) return

            // 3. java.library.path（开发期 JVM 参数 / OS 全局安装）
            if (runCatching { System.loadLibrary(name) }.isSuccess) return

            // 4. 开发期 build 目录兜底（gradle run 未配置 java.library.path 时）
            val (libraryFile, searchedDirs) = findLibraryInDevBuild(name)
            if (libraryFile == null) {
                val searchedText = searchedDirs.joinToString(separator = "; ") { it.absolutePath }
                val userDir = File(System.getProperty("user.dir")).absolutePath
                throw UnsatisfiedLinkError(
                    "无法加载 DexKit 动态库: $name; user.dir=$userDir; searched=$searchedText"
                )
            }
            System.load(libraryFile.absolutePath)
        }

        @Suppress("UnsafeDynamicallyLoadedCode")
        private fun loadFromClasspathResource(name: String): Boolean {
            val cacheDir = resolveNativeCacheDir()
            cacheDir.mkdirs()

            nativeLibFileNames(name).forEach { fileName ->
                val stream = DexKitBridge::class.java.getResourceAsStream("/natives/$fileName")
                    ?: return@forEach
                val bytes = stream.use { input -> input.readBytes() }
                val target = File(cacheDir, fileName)
                val shouldWrite = !target.exists() || runCatching { !Files.readAllBytes(target.toPath()).contentEquals(bytes) }
                    .getOrDefault(true)
                if (shouldWrite) {
                    val temp = File.createTempFile("dexkit-", ".tmp", cacheDir)
                    try {
                        temp.outputStream().use { output -> output.write(bytes) }
                        try {
                            Files.move(
                                temp.toPath(),
                                target.toPath(),
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                        } catch (_: FileAlreadyExistsException) {
                        } catch (_: AtomicMoveNotSupportedException) {
                            if (!target.exists()) {
                                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            }
                        } catch (ex: Exception) {
                            if (!target.exists()) throw ex
                        }
                    } finally {
                        temp.delete()
                    }
                }
                if (runCatching { System.load(target.absolutePath) }.isSuccess) {
                    return true
                }
            }
            return false
        }

        @Suppress("UnsafeDynamicallyLoadedCode")
        private fun loadFromDirectory(directory: File, name: String): Boolean {
            if (!directory.exists() || !directory.isDirectory) return false
            nativeLibFileNames(name).forEach { fileName ->
                val target = File(directory, fileName)
                if (!target.exists() || !target.isFile) return@forEach
                if (runCatching { System.load(target.absolutePath) }.isSuccess) {
                    return true
                }
            }
            return false
        }

        private fun resolveNativeCacheDir(): File {
            val configuredPath = configuredNativeCacheDirPath()
            return configuredPath ?: File(System.getProperty("user.home"), ".dexclub/natives")
        }

        private fun configuredNativeLibraryDirPath(): File? {
            val configured = System.getProperty(NATIVE_LIBRARY_DIR_PROPERTY)
                ?: System.getenv(NATIVE_LIBRARY_DIR_ENV)
            if (configured.isNullOrBlank()) return null
            return resolveConfiguredDirectory(configured)
        }

        private fun configuredNativeCacheDirPath(): File? {
            val configured = System.getProperty(NATIVE_CACHE_DIR_PROPERTY)
                ?: System.getenv(NATIVE_CACHE_DIR_ENV)
            if (configured.isNullOrBlank()) return null
            return resolveConfiguredDirectory(configured)
        }

        private fun resolveConfiguredDirectory(rawPath: String): File {
            val trimmed = rawPath.trim()
            val homeDir = File(System.getProperty("user.home"))
            val expanded = if (trimmed == "~") {
                homeDir
            } else if (trimmed.startsWith("~/")) {
                File(homeDir, trimmed.removePrefix("~/"))
            } else {
                File(trimmed)
            }
            return if (expanded.isAbsolute) expanded else File(homeDir, trimmed)
        }

        private fun nativeLibFileNames(name: String): List<String> = when {
            isWindows() -> listOf("$name.dll", "lib$name.dll")
            isMac() -> listOf("lib$name.dylib")
            else -> listOf("lib$name.so")
        }

        private const val NATIVE_LIBRARY_DIR_PROPERTY = "dexclub.dexkit.native.library.dir"
        private const val NATIVE_LIBRARY_DIR_ENV = "DEXCLUB_DEXKIT_NATIVE_LIBRARY_DIR"
        private const val NATIVE_CACHE_DIR_PROPERTY = "dexclub.dexkit.native.cache.dir"
        private const val NATIVE_CACHE_DIR_ENV = "DEXCLUB_DEXKIT_NATIVE_CACHE_DIR"

        private fun findLibraryInDevBuild(name: String): Pair<File?, List<File>> {
            val candidateDirs = linkedSetOf<File>().apply {
                addAll(findDirsByCodeSource())
                addAll(findDirsByWorkingDirectory())
            }
            val namePrefixes = listOf("lib$name", name)
            val found = candidateDirs.asSequence()
                .filter { it.exists() && it.isDirectory }
                .flatMap { it.listFiles()?.asSequence().orEmpty() }
                .firstOrNull { file ->
                    namePrefixes.any { prefix -> file.name.startsWith(prefix) } && isNativeLibrary(file)
                }
            return found to candidateDirs.toList()
        }

        private fun findDirsByCodeSource(): List<File> {
            val result = mutableListOf<File>()
            val codeSource = NativeDexKitBridge::class.java.protectionDomain.codeSource?.location
                ?: return result
            val jarOrDir = runCatching { File(codeSource.toURI()) }.getOrNull() ?: return result
            var cursor: File? = if (jarOrDir.isDirectory) jarOrDir else jarOrDir.parentFile
            repeat(8) {
                if (cursor == null) return@repeat
                result += File(cursor, "library")
                result += File(cursor, "build/library")
                cursor = cursor.parentFile
            }
            return result
        }

        private fun findDirsByWorkingDirectory(): List<File> {
            val result = mutableListOf<File>()
            var current: File? = File(System.getProperty("user.dir")).absoluteFile
            repeat(5) {
                if (current == null) return@repeat
                result += File(current, "vendor/DexKit/main/build/library")
                result += File(current, "vendor/DexKit/dexkit/build/library")
                result += File(current, "libs/dex-engine/vendor/DexKit/main/build/library")
                result += File(current, "libs/dex-engine/vendor/DexKit/dexkit/build/library")
                result += File(current, "dex-engine/vendor/DexKit/main/build/library")
                result += File(current, "dex-engine/vendor/DexKit/dexkit/build/library")
                current = current.parentFile
            }
            return result
        }

        private fun isNativeLibrary(file: File): Boolean {
            val fileName = file.name.lowercase()
            return fileName.endsWith(".dll") || fileName.endsWith(".so") || fileName.endsWith(".dylib")
        }

        private fun isWindows(): Boolean =
            System.getProperty("os.name").lowercase().contains("windows")

        private fun isMac(): Boolean =
            System.getProperty("os.name").lowercase().contains("mac")
    }
}
