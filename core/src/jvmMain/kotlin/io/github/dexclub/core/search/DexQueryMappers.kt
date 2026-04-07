package io.github.dexclub.core.search

import io.github.dexclub.core.request.DexClassQueryRequest
import io.github.dexclub.core.request.DexFieldQueryRequest
import io.github.dexclub.core.request.DexMethodQueryRequest
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.FindField
import io.github.dexclub.dexkit.query.FindMethod

internal fun DexClassQueryRequest.toDexKitQuery(): FindClass =
    FindClass(
        searchPackages = searchPackages,
        excludePackages = excludePackages,
        ignorePackagesCase = ignorePackagesCase,
        matcher = matcher,
        findFirst = findFirst,
    )

internal fun DexMethodQueryRequest.toDexKitQuery(): FindMethod =
    FindMethod(
        searchPackages = searchPackages,
        excludePackages = excludePackages,
        ignorePackagesCase = ignorePackagesCase,
        matcher = matcher,
        findFirst = findFirst,
    )

internal fun DexFieldQueryRequest.toDexKitQuery(): FindField =
    FindField(
        searchPackages = searchPackages,
        excludePackages = excludePackages,
        ignorePackagesCase = ignorePackagesCase,
        matcher = matcher,
        findFirst = findFirst,
    )
