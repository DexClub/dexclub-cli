package io.github.dexclub.core.input

import java.io.File
import java.io.InputStream

internal object DexInputInspector {
    fun isDex(file: File): Boolean {
        try {
            val header = ByteArray(DEX_MAGIC_SIZE)
            val bytesRead = file.inputStream().use { input ->
                input.readAtLeast(header, 0, DEX_MAGIC_SIZE)
            }
            if (bytesRead < DEX_MAGIC_SIZE) return false
            return header[0] == 'd'.code.toByte() &&
                header[1] == 'e'.code.toByte() &&
                header[2] == 'x'.code.toByte() &&
                header[3] == '\n'.code.toByte() &&
                header[4].isAsciiDigit() &&
                header[5].isAsciiDigit() &&
                header[6].isAsciiDigit() &&
                header[7] == 0.toByte()
        } catch (_: Exception) {
            return false
        }
    }

    private fun Byte.isAsciiDigit(): Boolean {
        return this in '0'.code.toByte()..'9'.code.toByte()
    }

    private fun InputStream.readAtLeast(
        target: ByteArray,
        offset: Int,
        byteCount: Int,
    ): Int {
        var total = 0
        while (total < byteCount) {
            val read = read(target, offset + total, byteCount - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private const val DEX_MAGIC_SIZE = 8
}
