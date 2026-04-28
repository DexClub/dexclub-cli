package io.github.dexclub.dexkit.result

import kotlinx.serialization.Serializable

@Serializable
enum class FieldUsingType {
    Read,
    Write,
    ;

    fun isRead(): Boolean = this == Read

    fun isWrite(): Boolean = this == Write
}
