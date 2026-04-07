package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class NumberEncodeValueMatcher(
    var byteValue: Byte? = null,
    var shortValue: Short? = null,
    var charValue: Char? = null,
    var intValue: Int? = null,
    var longValue: Long? = null,
    var floatValue: Float? = null,
    var doubleValue: Double? = null,
)
