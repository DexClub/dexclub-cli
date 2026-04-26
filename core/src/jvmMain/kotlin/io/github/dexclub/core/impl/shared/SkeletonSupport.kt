package io.github.dexclub.core.impl.shared

internal fun skeletonNotImplemented(api: String): Nothing {
    throw NotImplementedError("$api is not implemented yet.")
}
