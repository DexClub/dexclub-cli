package io.github.dexclub.dexkit.query

import kotlinx.serialization.Serializable

@Serializable
data class MethodMatcher(
    var name: StringMatcher? = null,
    var modifiers: AccessFlagsMatcher? = null,
    var declaredClass: ClassMatcher? = null,
    var protoShorty: String? = null,
    var returnType: ClassMatcher? = null,
    var params: ParametersMatcher? = null,
    var annotations: AnnotationsMatcher? = null,
    var opCodes: OpCodesMatcher? = null,
    val usingStrings: MutableList<StringMatcher> = mutableListOf(),
    val usingFields: MutableList<UsingFieldMatcher> = mutableListOf(),
    val usingNumbers: MutableList<NumberEncodeValueMatcher> = mutableListOf(),
    var invokeMethods: MethodsMatcher? = null,
    var callerMethods: MethodsMatcher? = null,
)
