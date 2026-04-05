package io.github.dexclub.utils

object SignatureUtils {
    fun typeName(signature: String): String {
        return signature
            .removePrefix("L")
            .removeSuffix(";")
            .replace('/', '.')
    }

    fun typeSignature(typeName: String): String {
        if (typeName.startsWith('L') && typeName.endsWith(';'))
            return typeName

        return "L${typeName.replace('.', '/')};"
    }
}