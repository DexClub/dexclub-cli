package io.github.dexclub.cli

import io.github.dexclub.core.api.shared.DexKitNative
import java.io.File

internal object DexKitCliBootstrap {
    fun configureNativeLibraryDir() {
        if (System.getProperty(DexKitNative.LIBRARY_PATH_PROPERTY) != null) return
        if (System.getenv(DexKitNative.LIBRARY_PATH_ENV) != null) return
        if (System.getProperty(DexKitNative.LIBRARY_DIR_PROPERTY) != null) return
        if (System.getenv(DexKitNative.LIBRARY_DIR_ENV) != null) return

        val codeSource = CliApp::class.java.protectionDomain.codeSource?.location ?: return
        val codeSourceFile = runCatching { File(codeSource.toURI()) }.getOrNull() ?: return
        if (!codeSourceFile.isFile || !codeSourceFile.name.endsWith(".jar", ignoreCase = true)) return

        val libraryDir = codeSourceFile.parentFile ?: return
        System.setProperty(DexKitNative.LIBRARY_DIR_PROPERTY, libraryDir.absolutePath)
    }
}
