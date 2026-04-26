package io.github.dexclub.cli

internal class OutputWriter {
    fun write(rendered: RenderedOutput, stdout: Appendable, stderr: Appendable) {
        rendered.stdout?.takeIf(String::isNotEmpty)?.let { text ->
            stdout.append(text)
            if (!text.endsWith('\n')) {
                stdout.appendLine()
            }
        }
        rendered.stderr?.takeIf(String::isNotEmpty)?.let { text ->
            stderr.append(text)
            if (!text.endsWith('\n')) {
                stderr.appendLine()
            }
        }
    }
}
