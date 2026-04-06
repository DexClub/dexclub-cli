package io.github.dexclub.core.session

import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import io.github.dexclub.core.source.DexIndexedClass
import io.github.dexclub.utils.SignatureUtils

internal class DexSession(
    private val dexFilesByPath: Map<String, DexFile>,
) {
    private val dexPathsByClassSignature by lazy(LazyThreadSafetyMode.NONE) {
        buildMap<String, MutableList<String>> {
            dexFilesByPath.forEach { (path, dexFile) ->
                dexFile.classes.forEach { classDef ->
                    getOrPut(classDef.type) { mutableListOf() }.add(path)
                }
            }
        }
    }

    val dexCount: Int
        get() = dexFilesByPath.size

    val classCount: Int
        get() = dexFilesByPath.values.sumOf { dexFile -> dexFile.classes.size }

    fun classes(): Sequence<DexIndexedClass> {
        return dexFilesByPath.asSequence().flatMap { (path, dexFile) ->
            dexFile.classes.asSequence().map { classDef ->
                DexIndexedClass(
                    dexAbsolutePath = path,
                    signature = classDef.type,
                    modifiers = classDef.accessFlags,
                )
            }
        }
    }

    fun requireClassDef(
        className: String,
        dexPath: String,
    ): ClassDef {
        val typeSignature = SignatureUtils.typeSignature(className)
        val dexFile = dexFilesByPath[dexPath]
            ?: throw IllegalArgumentException("`$dexPath` file not found.")
        return dexFile.classes.find { it.type == typeSignature }
            ?: throw IllegalArgumentException("`$className` not found in `$dexPath`")
    }

    fun findDexPathByClassDescriptor(descriptor: String): String? {
        return dexPathsByClassSignature[descriptor]?.singleOrNull()
    }

    fun findDexPathByClassName(className: String): String? {
        return findDexPathByClassDescriptor(SignatureUtils.typeSignature(className))
    }
}
