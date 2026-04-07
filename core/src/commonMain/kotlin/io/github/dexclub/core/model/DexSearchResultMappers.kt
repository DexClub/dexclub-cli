package io.github.dexclub.core.model

import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.FieldData
import io.github.dexclub.dexkit.result.MethodData

internal fun ClassData.toDexClassHit(
    sourceDexPath: String?,
): DexClassHit {
    return DexClassHit(
        name = name,
        descriptor = descriptor,
        sourceDexPath = sourceDexPath,
    )
}

internal fun MethodData.toDexMethodHit(
    sourceDexPath: String?,
): DexMethodHit {
    return DexMethodHit(
        className = className,
        name = name,
        descriptor = descriptor,
        sourceDexPath = sourceDexPath,
    )
}

internal fun FieldData.toDexFieldHit(
    sourceDexPath: String?,
): DexFieldHit {
    return DexFieldHit(
        className = className,
        name = name,
        descriptor = descriptor,
        sourceDexPath = sourceDexPath,
    )
}
