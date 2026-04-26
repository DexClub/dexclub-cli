package io.github.dexclub.core.impl.resource

import io.github.dexclub.core.impl.workspace.runtime.sha256Hex
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

internal fun resourceSourceFingerprint(workdir: String, sourcePath: String): String =
    sha256Hex(Files.readAllBytes(Path.of(workdir).resolve(sourcePath).normalize()))

internal fun resourceXmlCacheId(sourcePath: String, sourceEntry: String?): String =
    sha256Hex(
        buildString {
            append(sourcePath)
            append('\u0000')
            append(sourceEntry.orEmpty())
        }.toByteArray(StandardCharsets.UTF_8),
    )

internal fun resourceNowUtc(): String = Instant.now().toString()
