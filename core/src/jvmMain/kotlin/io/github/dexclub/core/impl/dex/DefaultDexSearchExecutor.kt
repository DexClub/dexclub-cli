package io.github.dexclub.core.impl.dex

import io.github.dexclub.core.api.dex.ClassHit
import io.github.dexclub.core.api.dex.DexInspectError
import io.github.dexclub.core.api.dex.DexInspectErrorReason
import io.github.dexclub.core.api.dex.FieldHit
import io.github.dexclub.core.api.dex.FieldUsageType
import io.github.dexclub.core.api.dex.InspectMethodRequest
import io.github.dexclub.core.api.dex.MethodDetail
import io.github.dexclub.core.api.dex.MethodDetailSection
import io.github.dexclub.core.api.dex.MethodFieldUsage
import io.github.dexclub.core.api.dex.MethodHit
import io.github.dexclub.core.api.workspace.WorkspaceContext
import io.github.dexclub.core.impl.workspace.model.MaterialInventory
import io.github.dexclub.dexkit.DexKitBridge
import io.github.dexclub.dexkit.query.ClassMatcher
import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.FindField
import io.github.dexclub.dexkit.query.FindMethod
import io.github.dexclub.dexkit.query.MatchType
import io.github.dexclub.dexkit.query.MethodMatcher
import io.github.dexclub.dexkit.query.MethodsMatcher
import io.github.dexclub.dexkit.query.ParameterMatcher
import io.github.dexclub.dexkit.query.ParametersMatcher
import io.github.dexclub.dexkit.query.StringMatchType
import io.github.dexclub.dexkit.query.StringMatcher
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
        ensureDexKitLoaded()
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
        ensureDexKitLoaded()
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
        ensureDexKitLoaded()
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
        ensureDexKitLoaded()
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
        ensureDexKitLoaded()
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

    override fun inspectMethod(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        request: InspectMethodRequest,
    ): MethodDetail {
        ensureDexKitLoaded()
        val descriptor = request.descriptor.trim()
        validateMethodDescriptor(descriptor)
        val workdirPath = Paths.get(workspace.workdir)
        val matches = mutableListOf<LocatedMethod>()

        for (apkPath in inventory.apkFiles) {
            matches += locateMethodInApk(workdirPath, apkPath, descriptor)
        }

        for (dexPath in inventory.dexFiles) {
            locateMethodInDexFile(workdirPath, dexPath, descriptor)?.let(matches::add)
        }

        val locatedMethod = when (matches.size) {
            1 -> matches.single()
            0 -> throw DexInspectError(
                reason = DexInspectErrorReason.MethodNotFound,
                message = "method not found: $descriptor",
            )
            else -> throw DexInspectError(
                reason = DexInspectErrorReason.AmbiguousMethod,
                message = "method resolves to multiple dex sources and inspect-method requires a unique descriptor within the workspace: $descriptor",
            )
        }

        val classSourceCache = mutableMapOf<String, MemberSource?>()
        val callers = request.includes.takeIf { MethodDetailSection.Callers in it }?.let {
            findMethodCallers(
                workspace = workspace,
                inventory = inventory,
                method = locatedMethod.method,
            )
        }

        return loadMethodDetail(
            workdirPath = workdirPath,
            inventory = inventory,
            locatedMethod = locatedMethod,
            includes = request.includes,
            classSourceCache = classSourceCache,
            callers = callers,
        )
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

    private fun locateMethodInApk(
        workdirPath: Path,
        relativeApkPath: String,
        descriptor: String,
    ): List<LocatedMethod> {
        val apkPath = workdirPath.resolve(relativeApkPath).normalize()
        val matches = mutableListOf<LocatedMethod>()
        ZipFile(apkPath.toFile()).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.endsWith(".dex", ignoreCase = true) }
                .sortedWith(compareBy({ dexEntrySortKey(it.name).first }, { dexEntrySortKey(it.name).second }, { it.name }))
                .toList()
            check(dexEntries.isNotEmpty()) { "APK does not contain any dex entries: $apkPath" }
            for (entry in dexEntries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                locateMethodInBridge(
                    bridge = DexKitBridge(arrayOf(dexBytes)),
                    descriptor = descriptor,
                    sourcePath = relativeApkPath,
                    sourceEntry = entry.name,
                )?.let(matches::add)
            }
        }
        return matches
    }

    private fun locateMethodInDexFile(
        workdirPath: Path,
        relativeDexPath: String,
        descriptor: String,
    ): LocatedMethod? {
        val dexPath = workdirPath.resolve(relativeDexPath).normalize().toString()
        return locateMethodInBridge(
            bridge = DexKitBridge(listOf(dexPath)),
            descriptor = descriptor,
            sourcePath = relativeDexPath,
            sourceEntry = null,
        )
    }

    private fun locateMethodInBridge(
        bridge: DexKitBridge,
        descriptor: String,
        sourcePath: String,
        sourceEntry: String?,
    ): LocatedMethod? =
        try {
            val method = bridge.getMethodData(descriptor) ?: return null
            LocatedMethod(
                method = method,
                sourcePath = sourcePath,
                sourceEntry = sourceEntry,
            )
        } finally {
            bridge.close()
        }

    private fun loadMethodDetail(
        workdirPath: Path,
        inventory: MaterialInventory,
        locatedMethod: LocatedMethod,
        includes: Set<MethodDetailSection>,
        classSourceCache: MutableMap<String, MemberSource?>,
        callers: List<MethodHit>?,
    ): MethodDetail {
        val sourceMethodDetail = if (locatedMethod.sourceEntry != null) {
            loadMethodDetailInApk(
                workdirPath = workdirPath,
                relativeApkPath = locatedMethod.sourcePath,
                sourceEntry = locatedMethod.sourceEntry,
                descriptor = locatedMethod.method.descriptor,
                includes = includes,
                inventory = inventory,
                classSourceCache = classSourceCache,
            )
        } else {
            loadMethodDetailInDexFile(
                workdirPath = workdirPath,
                relativeDexPath = locatedMethod.sourcePath,
                descriptor = locatedMethod.method.descriptor,
                includes = includes,
                inventory = inventory,
                classSourceCache = classSourceCache,
            )
        }

        return MethodDetail(
            method = locatedMethod.toMethodHit(),
            usingFields = sourceMethodDetail.usingFields,
            callers = callers,
            invokes = sourceMethodDetail.invokes,
        )
    }

    private fun loadMethodDetailInApk(
        workdirPath: Path,
        relativeApkPath: String,
        sourceEntry: String,
        descriptor: String,
        includes: Set<MethodDetailSection>,
        inventory: MaterialInventory,
        classSourceCache: MutableMap<String, MemberSource?>,
    ): MethodDetail {
        val apkPath = workdirPath.resolve(relativeApkPath).normalize()
        ZipFile(apkPath.toFile()).use { zip ->
            val entry = zip.getEntry(sourceEntry)
                ?: error("APK entry not found for inspected method: $relativeApkPath!$sourceEntry")
            val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
            return loadMethodDetailInBridge(
                bridge = DexKitBridge(arrayOf(dexBytes)),
                descriptor = descriptor,
                sourcePath = relativeApkPath,
                sourceEntry = sourceEntry,
                includes = includes,
                workdirPath = workdirPath,
                inventory = inventory,
                classSourceCache = classSourceCache,
            )
        }
    }

    private fun loadMethodDetailInDexFile(
        workdirPath: Path,
        relativeDexPath: String,
        descriptor: String,
        includes: Set<MethodDetailSection>,
        inventory: MaterialInventory,
        classSourceCache: MutableMap<String, MemberSource?>,
    ): MethodDetail {
        val dexPath = workdirPath.resolve(relativeDexPath).normalize().toString()
        return loadMethodDetailInBridge(
            bridge = DexKitBridge(listOf(dexPath)),
            descriptor = descriptor,
            sourcePath = relativeDexPath,
            sourceEntry = null,
            includes = includes,
            workdirPath = workdirPath,
            inventory = inventory,
            classSourceCache = classSourceCache,
        )
    }

    private fun loadMethodDetailInBridge(
        bridge: DexKitBridge,
        descriptor: String,
        sourcePath: String,
        sourceEntry: String?,
        includes: Set<MethodDetailSection>,
        workdirPath: Path,
        inventory: MaterialInventory,
        classSourceCache: MutableMap<String, MemberSource?>,
    ): MethodDetail =
        try {
            MethodDetail(
                method = bridge.getMethodData(descriptor)?.toMethodHit(sourcePath, sourceEntry)
                    ?: error("method not found in resolved source: $descriptor"),
                usingFields = includes.takeIf { MethodDetailSection.UsingFields in it }?.let {
                    bridge.getMethodUsingFields(descriptor).map { usage ->
                        MethodFieldUsage(
                            usingType = usage.usingType.toFieldUsageType(),
                            field = usage.field.toResolvedFieldHit(
                                workdirPath = workdirPath,
                                inventory = inventory,
                                classSourceCache = classSourceCache,
                            ),
                        )
                    }
                },
                callers = null,
                invokes = includes.takeIf { MethodDetailSection.Invokes in it }?.let {
                    bridge.getMethodInvokes(descriptor).map { invoked ->
                        invoked.toResolvedMethodHit(
                            workdirPath = workdirPath,
                            inventory = inventory,
                            classSourceCache = classSourceCache,
                        )
                    }
                },
            )
        } finally {
            bridge.close()
        }

    private fun findMethodCallers(
        workspace: WorkspaceContext,
        inventory: MaterialInventory,
        method: MethodData,
    ): List<MethodHit> {
        val query = buildExactCallersQuery(method)
        return findMethods(
            workspace = workspace,
            inventory = inventory,
            query = query,
        ).distinct()
    }

    private fun buildExactCallersQuery(method: MethodData): FindMethod =
        FindMethod(
            matcher = MethodMatcher(
                invokeMethods = MethodsMatcher(
                    methods = mutableListOf(method.toExactMethodMatcher()),
                    matchType = MatchType.Contains,
                ),
            ),
        )

    private fun MethodData.toExactMethodMatcher(): MethodMatcher =
        MethodMatcher(
            name = StringMatcher(value = name, matchType = StringMatchType.Equals),
            declaredClass = ClassMatcher(
                className = StringMatcher(value = className, matchType = StringMatchType.Equals),
            ),
            returnType = ClassMatcher(
                className = StringMatcher(value = returnTypeName, matchType = StringMatchType.Equals),
            ),
            params = ParametersMatcher(
                params = paramTypeNames.map { typeName ->
                    ParameterMatcher(
                        type = ClassMatcher(
                            className = StringMatcher(value = typeName, matchType = StringMatchType.Equals),
                        ),
                    )
                }.toMutableList(),
            ),
        )

    private fun resolveClassSource(
        workdirPath: Path,
        inventory: MaterialInventory,
        className: String,
        classSourceCache: MutableMap<String, MemberSource?>,
    ): MemberSource? =
        classSourceCache.getOrPut(className) {
            val descriptor = toTypeSignature(className)
            val matches = mutableListOf<MemberSource>()
            inventory.apkFiles.forEach { apkPath ->
                matches += resolveClassSourceInApk(workdirPath, apkPath, descriptor)
            }
            inventory.dexFiles.forEach { dexPath ->
                resolveClassSourceInDexFile(workdirPath, dexPath, descriptor)?.let(matches::add)
            }
            matches.singleOrNull()
        }

    private fun resolveClassSourceInApk(
        workdirPath: Path,
        relativeApkPath: String,
        descriptor: String,
    ): List<MemberSource> {
        val apkPath = workdirPath.resolve(relativeApkPath).normalize()
        val matches = mutableListOf<MemberSource>()
        ZipFile(apkPath.toFile()).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name.endsWith(".dex", ignoreCase = true) }
                .sortedWith(compareBy({ dexEntrySortKey(it.name).first }, { dexEntrySortKey(it.name).second }, { it.name }))
                .toList()
            for (entry in dexEntries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                val present = DexKitBridge(arrayOf(dexBytes)).useGetClassData(descriptor) { it != null }
                if (present) {
                    matches += MemberSource(sourcePath = relativeApkPath, sourceEntry = entry.name)
                }
            }
        }
        return matches
    }

    private fun resolveClassSourceInDexFile(
        workdirPath: Path,
        relativeDexPath: String,
        descriptor: String,
    ): MemberSource? {
        val dexPath = workdirPath.resolve(relativeDexPath).normalize().toString()
        val present = DexKitBridge(listOf(dexPath)).useGetClassData(descriptor) { it != null }
        return if (present) MemberSource(sourcePath = relativeDexPath, sourceEntry = null) else null
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

    private fun ensureDexKitLoaded() {
        DexKitNativeLoader.ensureLoaded()
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

    private inline fun <T> DexKitBridge.useGetClassData(descriptor: String, block: (ClassData?) -> T): T =
        try {
            block(getClassData(descriptor))
        } finally {
            close()
        }

    private fun validateMethodDescriptor(descriptor: String) {
        val arrowIndex = descriptor.indexOf("->")
        val descriptorStart = descriptor.indexOf('(', startIndex = arrowIndex + 2)
        val descriptorEnd = descriptor.lastIndexOf(')')
        if (
            descriptor.isBlank() ||
            arrowIndex <= 0 ||
            descriptorStart <= arrowIndex + 2 ||
            descriptorEnd < descriptorStart ||
            descriptorEnd == descriptor.lastIndex
        ) {
            throw DexInspectError(
                reason = DexInspectErrorReason.InvalidMethodDescriptor,
                message = "invalid method descriptor: $descriptor",
            )
        }
    }

    private fun MethodData.toMethodHit(sourcePath: String?, sourceEntry: String?): MethodHit =
        MethodHit(
            className = className,
            methodName = name,
            descriptor = descriptor,
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
        )

    private fun FieldData.toFieldHit(sourcePath: String?, sourceEntry: String?): FieldHit =
        FieldHit(
            className = className,
            fieldName = name,
            descriptor = descriptor,
            sourcePath = sourcePath,
            sourceEntry = sourceEntry,
        )

    private fun MethodData.toResolvedMethodHit(
        workdirPath: Path,
        inventory: MaterialInventory,
        classSourceCache: MutableMap<String, MemberSource?>,
    ): MethodHit {
        val source = resolveClassSource(workdirPath, inventory, className, classSourceCache)
        return toMethodHit(source?.sourcePath, source?.sourceEntry)
    }

    private fun FieldData.toResolvedFieldHit(
        workdirPath: Path,
        inventory: MaterialInventory,
        classSourceCache: MutableMap<String, MemberSource?>,
    ): FieldHit {
        val source = resolveClassSource(workdirPath, inventory, className, classSourceCache)
        return toFieldHit(source?.sourcePath, source?.sourceEntry)
    }

    private fun io.github.dexclub.dexkit.result.FieldUsingType.toFieldUsageType(): FieldUsageType =
        when (this) {
            io.github.dexclub.dexkit.result.FieldUsingType.Read -> FieldUsageType.Read
            io.github.dexclub.dexkit.result.FieldUsingType.Write -> FieldUsageType.Write
        }

    private data class LocatedMethod(
        val method: MethodData,
        val sourcePath: String,
        val sourceEntry: String?,
    ) {
        fun toMethodHit(): MethodHit =
            MethodHit(
                className = method.className,
                methodName = method.name,
                descriptor = method.descriptor,
                sourcePath = sourcePath,
                sourceEntry = sourceEntry,
            )
    }

    private data class MemberSource(
        val sourcePath: String,
        val sourceEntry: String?,
    )
}
