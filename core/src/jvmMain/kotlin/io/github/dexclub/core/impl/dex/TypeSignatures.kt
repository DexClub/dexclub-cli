package io.github.dexclub.core.impl.dex

internal fun toTypeSignature(typeName: String): String {
    if (typeName.startsWith('L') && typeName.endsWith(';')) {
        return typeName
    }
    return "L${typeName.replace('.', '/')};"
}
