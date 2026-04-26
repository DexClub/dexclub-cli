package io.github.dexclub.core.impl.dex

import com.android.tools.smali.baksmali.formatter.BaksmaliWriter
import com.android.tools.smali.dexlib2.iface.value.CharEncodedValue
import java.io.Writer

internal class UnescapedUnicodeBaksmaliWriter(
    writer: Writer,
) : BaksmaliWriter(writer) {
    override fun writeQuotedString(charSequence: CharSequence) {
        writer.write("\"")

        val text = charSequence.toString()
        var index = 0
        while (index < text.length) {
            val current = text[index]
            if (
                Character.isHighSurrogate(current) &&
                index + 1 < text.length &&
                Character.isLowSurrogate(text[index + 1])
            ) {
                val next = text[index + 1]
                val codePoint = Character.toCodePoint(current, next)
                if (shouldWriteUnicodeCodePointDirectly(codePoint)) {
                    writer.write(String(Character.toChars(codePoint)))
                } else {
                    writeUnicodeEscape(current)
                    writeUnicodeEscape(next)
                }
                index += 2
                continue
            }

            writeQuotedStringChar(current)
            index += 1
        }

        writer.write("\"")
    }

    override fun writeCharEncodedValue(encodedValue: CharEncodedValue) {
        val current = encodedValue.value
        if (current in ' '..'~') {
            writer.write("'")
            if (current == '\'' || current == '"' || current == '\\') {
                writer.write("\\")
            }
            writer.write(current.code)
            writer.write("'")
            return
        }

        if (current <= '\u007f') {
            when (current) {
                '\n' -> {
                    writer.write("'\\n'")
                    return
                }

                '\r' -> {
                    writer.write("'\\r'")
                    return
                }

                '\t' -> {
                    writer.write("'\\t'")
                    return
                }
            }
        }

        writer.write("'")
        if (shouldWriteUnicodeCharDirectly(current)) {
            writer.write(current.code)
        } else {
            writeUnicodeEscape(current)
        }
        writer.write("'")
    }

    private fun writeQuotedStringChar(current: Char) {
        if (current in ' '..'~') {
            if (current == '\'' || current == '"' || current == '\\') {
                writer.write("\\")
            }
            writer.write(current.code)
            return
        }

        if (current <= '\u007f') {
            when (current) {
                '\n' -> {
                    writer.write("\\n")
                    return
                }

                '\r' -> {
                    writer.write("\\r")
                    return
                }

                '\t' -> {
                    writer.write("\\t")
                    return
                }
            }
        }

        if (shouldWriteUnicodeCharDirectly(current)) {
            writer.write(current.code)
        } else {
            writeUnicodeEscape(current)
        }
    }

    private fun shouldWriteUnicodeCharDirectly(current: Char): Boolean =
        current.code >= 0x80 &&
            !Character.isISOControl(current.code) &&
            !Character.isSurrogate(current)

    private fun shouldWriteUnicodeCodePointDirectly(codePoint: Int): Boolean =
        codePoint >= 0x80 && !Character.isISOControl(codePoint)

    private fun writeUnicodeEscape(current: Char) {
        writer.write("\\u")
        writer.write(Character.forDigit(current.code shr 12, 16).code)
        writer.write(Character.forDigit(current.code shr 8 and 0x0f, 16).code)
        writer.write(Character.forDigit(current.code shr 4 and 0x0f, 16).code)
        writer.write(Character.forDigit(current.code and 0x0f, 16).code)
    }
}
