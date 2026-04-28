package io.github.dexclub.dexkit

import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.FindField
import io.github.dexclub.dexkit.query.FindMethod
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.ClassDataList
import io.github.dexclub.dexkit.result.FieldData
import io.github.dexclub.dexkit.result.FieldDataList
import io.github.dexclub.dexkit.result.MethodData
import io.github.dexclub.dexkit.result.MethodDataList
import io.github.dexclub.dexkit.result.UsingFieldData

expect class DexKitBridge(dexPaths: List<String>) {

    constructor(apkPath: String)

    constructor(dexBytesArray: Array<ByteArray>)

    val isValid: Boolean

    fun getDexNum(): Int

    fun setThreadNum(num: Int)

    fun initFullCache()

    fun exportDexFile(outPath: String)

    fun findClass(query: FindClass): ClassDataList

    fun findMethod(query: FindMethod): MethodDataList

    fun findField(query: FindField): FieldDataList

    fun batchFindClassUsingStrings(query: BatchFindClassUsingStrings): Map<String, ClassDataList>

    fun batchFindMethodUsingStrings(query: BatchFindMethodUsingStrings): Map<String, MethodDataList>

    fun getClassData(descriptor: String): ClassData?

    fun getMethodData(descriptor: String): MethodData?

    fun getFieldData(descriptor: String): FieldData?

    fun getFieldReaders(descriptor: String): MethodDataList

    fun getFieldWriters(descriptor: String): MethodDataList

    fun getMethodCallers(descriptor: String): MethodDataList

    fun getMethodInvokes(descriptor: String): MethodDataList

    fun getMethodUsingFields(descriptor: String): List<UsingFieldData>

    fun close()
}
