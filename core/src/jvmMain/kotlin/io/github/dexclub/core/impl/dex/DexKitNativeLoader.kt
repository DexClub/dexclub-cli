package io.github.dexclub.core.impl.dex

import io.github.dexclub.core.api.shared.DexKitNative
import java.io.File

internal object DexKitNativeLoader {
    private const val LIBRARY_NAME = "dexkit"

    private val lock = Any()

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            loadLibrary()
            loaded = true
        }
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun loadLibrary() {
        configuredNativeLibraryPath()?.let { path ->
            if (tryLoad(path)) return
        }

        configuredNativeLibraryDirPath()?.let { directory ->
            if (loadFromDirectory(directory)) return
        }

        if (runCatching { System.loadLibrary(LIBRARY_NAME) }.isSuccess) return

        val (libraryFile, searchedDirs) = findLibraryInDevBuild()
        if (libraryFile == null) {
            val searchedText = searchedDirs.joinToString(separator = "; ") { it.absolutePath }
            throw UnsatisfiedLinkError(
                "无法加载 DexKit 动态库: $LIBRARY_NAME; searched=$searchedText; " +
                    "请预先调用 System.load(...)，或配置 ${DexKitNative.LIBRARY_PATH_PROPERTY} / " +
                    "${DexKitNative.LIBRARY_DIR_PROPERTY} / java.library.path"
            )
        }

        System.load(libraryFile.absolutePath)
    }

    private fun configuredNativeLibraryPath(): File? {
        val configured = System.getProperty(DexKitNative.LIBRARY_PATH_PROPERTY)
            ?: System.getenv(DexKitNative.LIBRARY_PATH_ENV)
        if (configured.isNullOrBlank()) return null
        return resolveConfiguredPath(configured)
    }

    private fun configuredNativeLibraryDirPath(): File? {
        val configured = System.getProperty(DexKitNative.LIBRARY_DIR_PROPERTY)
            ?: System.getenv(DexKitNative.LIBRARY_DIR_ENV)
        if (configured.isNullOrBlank()) return null
        return resolveConfiguredPath(configured)
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun tryLoad(path: File): Boolean {
        if (!path.exists() || !path.isFile) return false
        return runCatching { System.load(path.absolutePath) }.isSuccess
    }

    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun loadFromDirectory(directory: File): Boolean {
        if (!directory.exists() || !directory.isDirectory) return false
        nativeLibFileNames().forEach { fileName ->
            val target = File(directory, fileName)
            if (tryLoad(target)) {
                return true
            }
        }
        return false
    }

    private fun resolveConfiguredPath(rawPath: String): File {
        val trimmed = rawPath.trim()
        val homeDir = File(System.getProperty("user.home"))
        val expanded = when {
            trimmed == "~" -> homeDir
            trimmed.startsWith("~/") -> File(homeDir, trimmed.removePrefix("~/"))
            else -> File(trimmed)
        }
        return if (expanded.isAbsolute) expanded else File(homeDir, trimmed)
    }

    private fun nativeLibFileNames(): List<String> = when {
        isWindows() -> listOf("$LIBRARY_NAME.dll", "lib$LIBRARY_NAME.dll")
        isMac() -> listOf("lib$LIBRARY_NAME.dylib")
        else -> listOf("lib$LIBRARY_NAME.so")
    }

    private fun findLibraryInDevBuild(): Pair<File?, List<File>> {
        val candidateDirs = linkedSetOf<File>().apply {
            addAll(findDirsByWorkingDirectory())
        }
        val found = candidateDirs.asSequence()
            .flatMap { directory ->
                nativeLibFileNames().asSequence().map { fileName -> File(directory, fileName) }
            }
            .firstOrNull { it.exists() && it.isFile }
        return found to candidateDirs.toList()
    }

    private fun findDirsByWorkingDirectory(): List<File> {
        val result = mutableListOf<File>()
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            if (current == null) return@repeat
            result += File(current, "dexkit/vendor/DexKit/dexkit/build/library")
            result += File(current, "vendor/DexKit/dexkit/build/library")
            result += File(current, "DexKit/dexkit/build/library")
            current = current.parentFile
        }
        return result.distinct()
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("windows")

    private fun isMac(): Boolean =
        System.getProperty("os.name").lowercase().contains("mac")
}
