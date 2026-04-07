package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class AnnotationEncodeValueMatcher(
    var byteValue: Byte? = null,
    var shortValue: Short? = null,
    var charValue: Char? = null,
    var intValue: Int? = null,
    var longValue: Long? = null,
    var floatValue: Float? = null,
    var doubleValue: Double? = null,
    var stringValue: StringMatcher? = null,
    var classValue: ClassMatcher? = null,
    var methodValue: MethodMatcher? = null,
    var enumValue: FieldMatcher? = null,
    var arrayValue: AnnotationEncodeArrayMatcher? = null,
    var annotationValue: AnnotationMatcher? = null,
    var nullValue: Boolean = false,
    var boolValue: Boolean? = null,
)
