package io.github.dexclub.core.search

import io.github.dexclub.core.query.DexClassQueryMatcher
import io.github.dexclub.core.query.DexFieldQueryMatcher
import io.github.dexclub.core.query.DexMethodQueryMatcher
import io.github.dexclub.core.query.DexStringMatchType
import io.github.dexclub.core.query.DexStringMatcher
import io.github.dexclub.core.request.DexClassQueryRequest
import io.github.dexclub.core.request.DexFieldQueryRequest
import io.github.dexclub.core.request.DexMethodQueryRequest
import io.github.dexclub.dexkit.query.ClassMatcher
import io.github.dexclub.dexkit.query.FieldMatcher
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.FindField
import io.github.dexclub.dexkit.query.FindMethod
import io.github.dexclub.dexkit.query.MethodMatcher
import io.github.dexclub.dexkit.query.StringMatchType

internal fun DexClassQueryRequest.toDexKitQuery(): FindClass =
    FindClass(
        searchPackages = searchPackages,
        excludePackages = excludePackages,
        ignorePackagesCase = ignorePackagesCase,
        matcher = matcher?.toDexKitMatcher(),
        findFirst = findFirst,
    )

internal fun DexMethodQueryRequest.toDexKitQuery(): FindMethod =
    FindMethod(
        searchPackages = searchPackages,
        excludePackages = excludePackages,
        ignorePackagesCase = ignorePackagesCase,
        matcher = matcher?.toDexKitMatcher(),
        findFirst = findFirst,
    )

internal fun DexFieldQueryRequest.toDexKitQuery(): FindField =
    FindField(
        searchPackages = searchPackages,
        excludePackages = excludePackages,
        ignorePackagesCase = ignorePackagesCase,
        matcher = matcher?.toDexKitMatcher(),
        findFirst = findFirst,
    )

private fun DexClassQueryMatcher.toDexKitMatcher(): ClassMatcher =
    ClassMatcher(
        classNameMatcher = classNameMatcher?.toDexKitMatcher(),
        superClassMatcher = superClassMatcher?.toDexKitMatcher(),
        interfaceMatchers = interfaceMatchers.mapTo(mutableListOf()) { it.toDexKitMatcher() },
        usingStringMatchers = usingStringMatchers.mapTo(mutableListOf()) { it.toDexKitMatcher() },
        annotationMatchers = annotationMatchers.mapTo(mutableListOf()) { it.toDexKitMatcher() },
        methodCountRange = methodCountRange,
        fieldCountRange = fieldCountRange,
        modifiers = modifiers,
    )

private fun DexMethodQueryMatcher.toDexKitMatcher(): MethodMatcher =
    MethodMatcher(
        nameMatcher = nameMatcher?.toDexKitMatcher(),
        declaredClassMatcher = declaredClassMatcher?.toDexKitMatcher(),
        returnTypeMatcher = returnTypeMatcher?.toDexKitMatcher(),
        paramTypeMatchers = paramTypeMatchers.mapTo(mutableListOf()) { it?.toDexKitMatcher() },
        paramCount = paramCount,
        usingStringMatchers = usingStringMatchers.mapTo(mutableListOf()) { it.toDexKitMatcher() },
        opCodes = opCodes.toMutableList(),
        modifiers = modifiers,
    )

private fun DexFieldQueryMatcher.toDexKitMatcher(): FieldMatcher =
    FieldMatcher(
        nameMatcher = nameMatcher?.toDexKitMatcher(),
        declaredClassMatcher = declaredClassMatcher?.toDexKitMatcher(),
        typeMatcher = typeMatcher?.toDexKitMatcher(),
        annotationMatchers = annotationMatchers.mapTo(mutableListOf()) { it.toDexKitMatcher() },
        modifiers = modifiers,
    )

private fun DexStringMatcher.toDexKitMatcher(): io.github.dexclub.dexkit.query.StringMatcher =
    io.github.dexclub.dexkit.query.StringMatcher(
        value = value,
        matchType = matchType.toDexKitMatchType(),
        ignoreCase = ignoreCase,
    )

private fun DexStringMatchType.toDexKitMatchType(): StringMatchType = when (this) {
    DexStringMatchType.Contains -> StringMatchType.Contains
    DexStringMatchType.StartsWith -> StringMatchType.StartsWith
    DexStringMatchType.EndsWith -> StringMatchType.EndsWith
    DexStringMatchType.Equals -> StringMatchType.Equals
    DexStringMatchType.SimilarRegex -> StringMatchType.SimilarRegex
}
