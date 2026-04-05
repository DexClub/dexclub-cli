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
import io.github.dexclub.dexkit.result.toClassDataList
import io.github.dexclub.dexkit.result.toFieldDataList
import io.github.dexclub.dexkit.result.toMethodDataList
import org.luckypray.dexkit.DexKitBridge as NativeDexKitBridge
import java.io.File

actual class DexKitBridge {
    private var delegate: NativeDexKitBridge? = null

    actual constructor(dexPaths: List<String>) {
        require(dexPaths.isNotEmpty()) { "dexPaths 不能为空" }
        ensureLibraryLoaded()
        val dexBytesArray = dexPaths.map { path ->
            val dexFile = File(path)
            require(dexFile.exists()) { "dex 文件不存在: ${dexFile.absolutePath}" }
            require(dexFile.isFile) { "dex 路径必须是文件: ${dexFile.absolutePath}" }
            dexFile.readBytes()
        }.toTypedArray()
        delegate = NativeDexKitBridge.create(dexBytesArray)
    }

    actual constructor(apkPath: String) {
        ensureLibraryLoaded()
        require(apkPath.isNotEmpty()) { "apkPath 不能为空" }
        val apkFile = File(apkPath)
        require(apkFile.exists()) { "apk 文件不存在: ${apkFile.absolutePath}" }
        require(apkFile.isFile) { "apk 路径必须是文件: ${apkFile.absolutePath}" }
        delegate = NativeDexKitBridge.create(apkFile.absolutePath)
    }

    actual constructor(dexBytesArray: Array<ByteArray>) {
        ensureLibraryLoaded()
        require(dexBytesArray.isNotEmpty()) { "dexBytesArray 不能为空" }
        delegate = NativeDexKitBridge.create(dexBytesArray)
    }

    /**
     * 通过 [ClassLoader] 创建 Bridge（仅 Android 平台）。
     *
     * @param loader 目标 ClassLoader
     * @param useMemoryDexFile 是否使用内存中的 dex 文件
     */
    constructor(loader: ClassLoader, useMemoryDexFile: Boolean = false) {
        ensureLibraryLoaded()
        delegate = NativeDexKitBridge.create(loader, useMemoryDexFile)
    }

    actual val isValid: Boolean
        get() = delegate?.isValid == true

    actual fun getDexNum(): Int = ensureDelegate().getDexNum()

    actual fun setThreadNum(num: Int) = ensureDelegate().setThreadNum(num)

    actual fun initFullCache() = ensureDelegate().initFullCache()

    actual fun exportDexFile(outPath: String) {
        require(outPath.isNotEmpty()) { "outPath 不能为空" }
        ensureDelegate().exportDexFile(outPath)
    }

    actual fun findClass(query: FindClass): ClassDataList {
        val d = ensureDelegate()
        return d.findClass(query.toNative(d)).map { it.toKmpClassData() }.toClassDataList(this)
    }

    actual fun findMethod(query: FindMethod): MethodDataList {
        val d = ensureDelegate()
        return d.findMethod(query.toNative(d)).map { it.toKmpMethodData() }.toMethodDataList(this)
    }

    actual fun findField(query: FindField): FieldDataList {
        val d = ensureDelegate()
        return d.findField(query.toNative(d)).map { it.toKmpFieldData() }.toFieldDataList(this)
    }

    actual fun batchFindClassUsingStrings(query: BatchFindClassUsingStrings): Map<String, ClassDataList> {
        val d = ensureDelegate()
        return d.batchFindClassUsingStrings(query.toNative(d))
            .mapValues { (_, list) -> list.map { it.toKmpClassData() }.toClassDataList(this) }
    }

    actual fun batchFindMethodUsingStrings(query: BatchFindMethodUsingStrings): Map<String, MethodDataList> {
        val d = ensureDelegate()
        return d.batchFindMethodUsingStrings(query.toNative(d))
            .mapValues { (_, list) -> list.map { it.toKmpMethodData() }.toMethodDataList(this) }
    }

    actual fun getClassData(descriptor: String): ClassData? {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getClassData(descriptor)?.toKmpClassData()
    }

    actual fun getMethodData(descriptor: String): MethodData? {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getMethodData(descriptor)?.toKmpMethodData()
    }

    actual fun getFieldData(descriptor: String): FieldData? {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getFieldData(descriptor)?.toKmpFieldData()
    }

    actual fun getFieldReaders(descriptor: String): MethodDataList {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getFieldData(descriptor)
            ?.readers?.map { it.toKmpMethodData() }.orEmpty()
            .toMethodDataList(this)
    }

    actual fun getFieldWriters(descriptor: String): MethodDataList {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getFieldData(descriptor)
            ?.writers?.map { it.toKmpMethodData() }.orEmpty()
            .toMethodDataList(this)
    }

    actual fun getMethodCallers(descriptor: String): MethodDataList {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getMethodData(descriptor)
            ?.callers?.map { it.toKmpMethodData() }.orEmpty()
            .toMethodDataList(this)
    }

    actual fun getMethodInvokes(descriptor: String): MethodDataList {
        require(descriptor.isNotEmpty()) { "descriptor 不能为空" }
        return ensureDelegate().getMethodData(descriptor)
            ?.invokes?.map { it.toKmpMethodData() }.orEmpty()
            .toMethodDataList(this)
    }

    actual fun close() {
        delegate?.close()
        delegate = null
    }

    private fun ensureDelegate(): NativeDexKitBridge =
        checkNotNull(delegate) { "DexKitBridge 未初始化，请传入有效的 dex/apk 数据" }

    private companion object {
        private val lock = Any()

        @Volatile
        private var loaded = false

        private fun ensureLibraryLoaded() {
            if (loaded) return
            synchronized(lock) {
                if (loaded) return
                System.loadLibrary("dexkit")
                loaded = true
            }
        }
    }
}
