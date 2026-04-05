package io.github.dexclub.dexkit

import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.FindField
import io.github.dexclub.dexkit.query.FindMethod
import io.github.dexclub.dexkit.result.ClassDataList
import io.github.dexclub.dexkit.result.FieldDataList
import io.github.dexclub.dexkit.result.MethodDataList

inline fun DexKitBridge.findClass(init: FindClass.() -> Unit): ClassDataList =
    findClass(FindClass().apply(init))

inline fun DexKitBridge.findMethod(init: FindMethod.() -> Unit): MethodDataList =
    findMethod(FindMethod().apply(init))

inline fun DexKitBridge.findField(init: FindField.() -> Unit): FieldDataList =
    findField(FindField().apply(init))

inline fun DexKitBridge.batchFindClassUsingStrings(
    init: BatchFindClassUsingStrings.() -> Unit,
): Map<String, ClassDataList> =
    batchFindClassUsingStrings(BatchFindClassUsingStrings().apply(init))

inline fun DexKitBridge.batchFindMethodUsingStrings(
    init: BatchFindMethodUsingStrings.() -> Unit,
): Map<String, MethodDataList> =
    batchFindMethodUsingStrings(BatchFindMethodUsingStrings().apply(init))
