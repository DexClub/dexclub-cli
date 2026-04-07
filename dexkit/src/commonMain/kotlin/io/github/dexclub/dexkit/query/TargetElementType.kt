package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
enum class TargetElementType {
    Type,
    Field,
    Method,
    Parameter,
    Constructor,
    LocalVariable,
    AnnotationType,
    Package,
    TypeParameter,
    TypeUse,
}
