package io.github.dexclub.core.impl.dex

import io.github.dexclub.core.api.dex.ClassHit
import io.github.dexclub.core.api.dex.FieldHit
import io.github.dexclub.core.api.dex.MethodHit
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.FindField
import io.github.dexclub.dexkit.query.FindMethod
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.FieldData
import io.github.dexclub.dexkit.result.MethodData
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

internal class DefaultDexSearchExecutor : DexSearchExecutor {
    override fun findClasses(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: FindClass,
    ): List<ClassHit> {
        val workdirPath = Paths.get(workspace.workdir)
        val hits = mutableListOf<ClassHit>()

        for (apkPath in inventory.apkFiles) {
            val apkHits = findClassesInApk(workdirPath, apkPath, query)
            hits += apkHits
            if (query.findFirst && apkHits.isNotEmpty()) {
                return listOf(apkHits.first())
            }
        }

        for (dexPath in inventory.dexFiles) {
            val dexHits = findClassesInDexFile(workdirPath, dexPath, query)
            hits += dexHits
            if (query.findFirst && dexHits.isNotEmpty()) {
                return listOf(dexHits.first())
            }
        }

        return hits
    }

    override fun findMethods(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: FindMethod,
    ): List<MethodHit> {
        val workdirPath = Paths.get(workspace.workdir)
        val hits = mutableListOf<MethodHit>()

        for (apkPath in inventory.apkFiles) {
            val apkHits = findMethodsInApk(workdirPath, apkPath, query)
            hits += apkHits
            if (query.findFirst && apkHits.isNotEmpty()) {
                return listOf(apkHits.first())
            }
        }

        for (dexPath in inventory.dexFiles) {
            val dexHits = findMethodsInDexFile(workdirPath, dexPath, query)
            hits += dexHits
            if (query.findFirst && dexHits.isNotEmpty()) {
                return listOf(dexHits.first())
            }
        }

        return hits
    }

    override fun findFields(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: FindField,
    ): List<FieldHit> {
        val workdirPath = Paths.get(workspace.workdir)
        val hits = mutableListOf<FieldHit>()

        for (apkPath in inventory.apkFiles) {
            val apkHits = findFieldsInApk(workdirPath, apkPath, query)
            hits += apkHits
            if (query.findFirst && apkHits.isNotEmpty()) {
                return listOf(apkHits.first())
            }
        }

        for (dexPath in inventory.dexFiles) {
            val dexHits = findFieldsInDexFile(workdirPath, dexPath, query)
            hits += dexHits
            if (query.findFirst && dexHits.isNotEmpty()) {
                return listOf(dexHits.first())
            }
        }

        return hits
    }

    override fun findClassesUsingStrings(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: BatchFindClassUsingStrings,
    ): List<ClassHit> {
        val workdirPath = Paths.get(workspace.workdir)
        val hits = mutableListOf<ClassHit>()

        for (apkPath in inventory.apkFiles) {
            hits += findClassesUsingStringsInApk(workdirPath, apkPath, query)
        }

        for (dexPath in inventory.dexFiles) {
            hits += findClassesUsingStringsInDexFile(workdirPath, dexPath, query)
        }

        return hits.distinct()
    }

    override fun findMethodsUsingStrings(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        query: BatchFindMethodUsingStrings,
    ): List<MethodHit> {
        val workdirPath = Paths.get(workspace.workdir)
        val hits = mutableListOf<MethodHit>()

        for (apkPath in inventory.apkFiles) {
            hits += findMethodsUsingStringsInApk(workdirPath, apkPath, query)
        }

        for (dexPath in inventory.dexFiles) {
            hits += findMethodsUsingStringsInDexFile(workdirPath, dexPath, query)
        }

        return hits.distinct()
    }

    private fun findClassesInApk(
        workdirPath: Path,
        relativeApkPath: String,
        query: FindClass,
    ): List<ClassHit> {
        val apkPath = workdirPath.resolve(relativeApkPath).normalize()
        val hits = mutableListOf<ClassHit>()
        ZipFile(apkPath.toFile()).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.endsWith(".dex", ignoreCase = true) }
                .sortedWith(compareBy({ dexEntrySortKey(it.name).first }, { dexEntrySortKey(it.name).second }, { it.name }))
                .toList()
            check(dexEntries.isNotEmpty()) { "APK does not contain any dex entries: $apkPath" }
            for (entry in dexEntries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                val entryHits = DexKitBridge(arrayOf(dexBytes)).useFindClass(query) { results ->
                    results.map { result ->
                        ClassHit(
                            className = result.descriptor,
                            sourcePath = relativeApkPath,
                            sourceEntry = entry.name,
                        )
                    }
                }
                hits += entryHits
                if (query.findFirst && entryHits.isNotEmpty()) {
                    return listOf(entryHits.first())
                }
            }
        }
        return hits
    }

    private fun findClassesInDexFile(
        workdirPath: Path,
        relativeDexPath: String,
        query: FindClass,
    ): List<ClassHit> {
        val dexPath = workdirPath.resolve(relativeDexPath).normalize().toString()
        return DexKitBridge(listOf(dexPath)).useFindClass(query) { results ->
            results.map { result ->
                ClassHit(
                    className = result.descriptor,
                    sourcePath = relativeDexPath,
                    sourceEntry = null,
                )
            }
        }
    }

    private fun findMethodsInApk(
        workdirPath: Path,
        relativeApkPath: String,
        query: FindMethod,
    ): List<MethodHit> {
        val apkPath = workdirPath.resolve(relativeApkPath).normalize()
        val hits = mutableListOf<MethodHit>()
        ZipFile(apkPath.toFile()).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.endsWith(".dex", ignoreCase = true) }
                .sortedWith(compareBy({ dexEntrySortKey(it.name).first }, { dexEntrySortKey(it.name).second }, { it.name }))
                .toList()
            check(dexEntries.isNotEmpty()) { "APK does not contain any dex entries: $apkPath" }
            for (entry in dexEntries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                val entryHits = DexKitBridge(arrayOf(dexBytes)).useFindMethod(query) { results ->
                    results.map { result ->
                        MethodHit(
                            className = result.className,
                            methodName = result.name,
                            descriptor = result.descriptor,
                            sourcePath = relativeApkPath,
                            sourceEntry = entry.name,
                        )
                    }
                }
                hits += entryHits
                if (query.findFirst && entryHits.isNotEmpty()) {
                    return listOf(entryHits.first())
                }
            }
        }
        return hits
    }

    private fun findMethodsInDexFile(
        workdirPath: Path,
        relativeDexPath: String,
        query: FindMethod,
    ): List<MethodHit> {
        val dexPath = workdirPath.resolve(relativeDexPath).normalize().toString()
        return DexKitBridge(listOf(dexPath)).useFindMethod(query) { results ->
            results.map { result ->
                MethodHit(
                    className = result.className,
                    methodName = result.name,
                    descriptor = result.descriptor,
                    sourcePath = relativeDexPath,
                    sourceEntry = null,
                )
            }
        }
    }

    private fun findFieldsInApk(
        workdirPath: Path,
        relativeApkPath: String,
        query: FindField,
    ): List<FieldHit> {
        val apkPath = workdirPath.resolve(relativeApkPath).normalize()
        val hits = mutableListOf<FieldHit>()
        ZipFile(apkPath.toFile()).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.endsWith(".dex", ignoreCase = true) }
                .sortedWith(compareBy({ dexEntrySortKey(it.name).first }, { dexEntrySortKey(it.name).second }, { it.name }))
                .toList()
            check(dexEntries.isNotEmpty()) { "APK does not contain any dex entries: $apkPath" }
            for (entry in dexEntries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                val entryHits = DexKitBridge(arrayOf(dexBytes)).useFindField(query) { results ->
                    results.map { result ->
                        FieldHit(
                            className = result.className,
                            fieldName = result.name,
                            descriptor = result.descriptor,
                            sourcePath = relativeApkPath,
                            sourceEntry = entry.name,
                        )
                    }
                }
                hits += entryHits
                if (query.findFirst && entryHits.isNotEmpty()) {
                    return listOf(entryHits.first())
                }
            }
        }
        return hits
    }

    private fun findFieldsInDexFile(
        workdirPath: Path,
        relativeDexPath: String,
        query: FindField,
    ): List<FieldHit> {
        val dexPath = workdirPath.resolve(relativeDexPath).normalize().toString()
        return DexKitBridge(listOf(dexPath)).useFindField(query) { results ->
            results.map { result ->
                FieldHit(
                    className = result.className,
                    fieldName = result.name,
                    descriptor = result.descriptor,
                    sourcePath = relativeDexPath,
                    sourceEntry = null,
                )
            }
        }
    }

    private fun findClassesUsingStringsInApk(
        workdirPath: Path,
        relativeApkPath: String,
        query: BatchFindClassUsingStrings,
    ): List<ClassHit> {
        val apkPath = workdirPath.resolve(relativeApkPath).normalize()
        val hits = mutableListOf<ClassHit>()
        ZipFile(apkPath.toFile()).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.endsWith(".dex", ignoreCase = true) }
                .sortedWith(compareBy({ dexEntrySortKey(it.name).first }, { dexEntrySortKey(it.name).second }, { it.name }))
                .toList()
            check(dexEntries.isNotEmpty()) { "APK does not contain any dex entries: $apkPath" }
            for (entry in dexEntries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                val entryHits = DexKitBridge(arrayOf(dexBytes)).useBatchFindClassUsingStrings(query) { results ->
                    results.values.asSequence()
                        .flatten()
                        .map { result ->
                            ClassHit(
                                className = result.descriptor,
                                sourcePath = relativeApkPath,
                                sourceEntry = entry.name,
                            )
                        }
                        .distinct()
                        .toList()
                }
                hits += entryHits
            }
        }
        return hits
    }

    private fun findClassesUsingStringsInDexFile(
        workdirPath: Path,
        relativeDexPath: String,
        query: BatchFindClassUsingStrings,
    ): List<ClassHit> {
        val dexPath = workdirPath.resolve(relativeDexPath).normalize().toString()
        return DexKitBridge(listOf(dexPath)).useBatchFindClassUsingStrings(query) { results ->
            results.values.asSequence()
                .flatten()
                .map { result ->
                    ClassHit(
                        className = result.descriptor,
                        sourcePath = relativeDexPath,
                        sourceEntry = null,
                    )
                }
                .distinct()
                .toList()
        }
    }

    private fun findMethodsUsingStringsInApk(
        workdirPath: Path,
        relativeApkPath: String,
        query: BatchFindMethodUsingStrings,
    ): List<MethodHit> {
        val apkPath = workdirPath.resolve(relativeApkPath).normalize()
        val hits = mutableListOf<MethodHit>()
        ZipFile(apkPath.toFile()).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.endsWith(".dex", ignoreCase = true) }
                .sortedWith(compareBy({ dexEntrySortKey(it.name).first }, { dexEntrySortKey(it.name).second }, { it.name }))
                .toList()
            check(dexEntries.isNotEmpty()) { "APK does not contain any dex entries: $apkPath" }
            for (entry in dexEntries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                val entryHits = DexKitBridge(arrayOf(dexBytes)).useBatchFindMethodUsingStrings(query) { results ->
                    results.values.asSequence()
                        .flatten()
                        .map { result ->
                            MethodHit(
                                className = result.className,
                                methodName = result.name,
                                descriptor = result.descriptor,
                                sourcePath = relativeApkPath,
                                sourceEntry = entry.name,
                            )
                        }
                        .distinct()
                        .toList()
                }
                hits += entryHits
            }
        }
        return hits
    }

    private fun findMethodsUsingStringsInDexFile(
        workdirPath: Path,
        relativeDexPath: String,
        query: BatchFindMethodUsingStrings,
    ): List<MethodHit> {
        val dexPath = workdirPath.resolve(relativeDexPath).normalize().toString()
        return DexKitBridge(listOf(dexPath)).useBatchFindMethodUsingStrings(query) { results ->
            results.values.asSequence()
                .flatten()
                .map { result ->
                    MethodHit(
                        className = result.className,
                        methodName = result.name,
                        descriptor = result.descriptor,
                        sourcePath = relativeDexPath,
                        sourceEntry = null,
                    )
                }
                .distinct()
                .toList()
        }
    }

    private fun dexEntrySortKey(name: String): Pair<Int, String> {
        if (name == "classes.dex") {
            return 1 to name
        }
        val suffix = name.removePrefix("classes").removeSuffix(".dex")
        return if (suffix.toIntOrNull() != null) {
            suffix.toInt() to name
        } else {
            Int.MAX_VALUE to name
        }
    }

    private inline fun <T> DexKitBridge.useFindClass(query: FindClass, block: (List<ClassData>) -> T): T =
        try {
            block(findClass(query))
        } finally {
            close()
        }

    private inline fun <T> DexKitBridge.useFindMethod(query: FindMethod, block: (List<MethodData>) -> T): T =
        try {
            block(findMethod(query))
        } finally {
            close()
        }

    private inline fun <T> DexKitBridge.useFindField(query: FindField, block: (List<FieldData>) -> T): T =
        try {
            block(findField(query))
        } finally {
            close()
        }

    private inline fun <T> DexKitBridge.useBatchFindClassUsingStrings(
        query: BatchFindClassUsingStrings,
        block: (Map<String, List<ClassData>>) -> T,
    ): T =
        try {
            block(batchFindClassUsingStrings(query))
        } finally {
            close()
        }

    private inline fun <T> DexKitBridge.useBatchFindMethodUsingStrings(
        query: BatchFindMethodUsingStrings,
        block: (Map<String, List<MethodData>>) -> T,
    ): T =
        try {
            block(batchFindMethodUsingStrings(query))
        } finally {
            close()
        }
}
