package io.github.dexclub.core

import io.github.dexclub.core.source.DexIndexedClass
import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.MethodData

expect class DexEngine(
    dexPaths: List<String>,
) : AutoCloseable {
    fun dexCount(): Int

    fun classCount(): Int

    fun indexedClasses(): Sequence<DexIndexedClass>

    fun getOrCreateBridge(): DexKitBridge?

    fun readDexNum(): Int?

    fun searchClassesByName(keyword: String): List<ClassData>

    fun searchMethodsByString(keyword: String): List<MethodData>

    suspend fun exportSingleDex(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String

    suspend fun exportSingleSmali(
        autoUnicodeDecode: Boolean,
        className: String,
        dexPath: String,
        outputPath: String,
    ): String

    suspend fun exportSingleJavaSource(
        className: String,
        dexPath: String,
        outputPath: String,
    ): String

    override fun close()

    companion object {
        fun isDex(path: String): Boolean
    }
}
