package io.github.dexclub.dexkit

import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings as KmpBatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings as KmpBatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.ClassMatcher as KmpClassMatcher
import io.github.dexclub.dexkit.query.FieldMatcher as KmpFieldMatcher
import io.github.dexclub.dexkit.query.FindClass as KmpFindClass
import io.github.dexclub.dexkit.query.FindField as KmpFindField
import io.github.dexclub.dexkit.query.FindMethod as KmpFindMethod
import io.github.dexclub.dexkit.query.MethodMatcher as KmpMethodMatcher
import io.github.dexclub.dexkit.query.StringMatchType as KmpStringMatchType
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.FieldData
import io.github.dexclub.dexkit.result.MethodData
import org.luckypray.dexkit.DexKitBridge as NativeDexKitBridge
import org.luckypray.dexkit.query.BatchFindClassUsingStrings as NativeBatchFindClassUsingStrings
import org.luckypray.dexkit.query.BatchFindMethodUsingStrings as NativeBatchFindMethodUsingStrings
import org.luckypray.dexkit.query.FindClass as NativeFindClass
import org.luckypray.dexkit.query.FindField as NativeFindField
import org.luckypray.dexkit.query.FindMethod as NativeFindMethod
import org.luckypray.dexkit.query.enums.StringMatchType as NativeStringMatchType
import org.luckypray.dexkit.query.matchers.AnnotationMatcher as NativeAnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher as NativeClassMatcher
import org.luckypray.dexkit.query.matchers.FieldMatcher as NativeFieldMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher as NativeMethodMatcher
import org.luckypray.dexkit.query.matchers.StringMatchersGroup as NativeStringMatchersGroup
import org.luckypray.dexkit.query.matchers.base.StringMatcher as NativeStringMatcher
import org.luckypray.dexkit.result.ClassData as NativeClassData
import org.luckypray.dexkit.result.FieldData as NativeFieldData
import org.luckypray.dexkit.result.MethodData as NativeMethodData

// ── Result mappers ────────────────────────────────────────────────────────────

internal fun NativeClassData.toKmpClassData(): ClassData =
    ClassData(
        descriptor = descriptor,
        name = name,
        simpleName = simpleName,
        sourceFile = sourceFile,
        modifiers = modifiers,
    )

internal fun NativeMethodData.toKmpMethodData(): MethodData =
    MethodData(
        descriptor = descriptor,
        name = name,
        className = className,
        paramTypeNames = paramTypeNames.toList(),
        returnTypeName = returnTypeName,
        modifiers = modifiers,
        isConstructor = isConstructor,
        isStaticInitializer = isStaticInitializer,
    )

internal fun NativeFieldData.toKmpFieldData(): FieldData =
    FieldData(
        descriptor = descriptor,
        name = name,
        className = className,
        typeName = typeName,
        modifiers = modifiers,
    )

// ── Query converters ──────────────────────────────────────────────────────────

internal fun KmpStringMatchType.toNative(): NativeStringMatchType = when (this) {
    KmpStringMatchType.Contains -> NativeStringMatchType.Contains
    KmpStringMatchType.StartsWith -> NativeStringMatchType.StartsWith
    KmpStringMatchType.EndsWith -> NativeStringMatchType.EndsWith
    KmpStringMatchType.Equals -> NativeStringMatchType.Equals
    KmpStringMatchType.SimilarRegex -> NativeStringMatchType.SimilarRegex
}

internal fun KmpClassMatcher.toNative(): NativeClassMatcher {
    val kmp = this
    return NativeClassMatcher().apply {
        kmp.classNameMatcher?.let { className(it.value, it.matchType.toNative(), it.ignoreCase) }
        kmp.superClassMatcher?.let { superClass(it.toNative()) }
        kmp.interfaceMatchers.forEach { addInterface(it.value, it.matchType.toNative(), it.ignoreCase) }
        kmp.usingStringMatchers.forEach { addUsingString(it.value, it.matchType.toNative(), it.ignoreCase) }
        kmp.annotationMatchers.forEach { m ->
            addAnnotation(NativeAnnotationMatcher.create().type(m.value, m.matchType.toNative(), m.ignoreCase))
        }
        kmp.methodCountRange?.let { methodCount(it.first, it.last) }
        kmp.fieldCountRange?.let { fieldCount(it.first, it.last) }
        kmp.modifiers?.let { modifiers(it) }
    }
}

internal fun KmpMethodMatcher.toNative(): NativeMethodMatcher {
    val kmp = this
    return NativeMethodMatcher().apply {
        kmp.nameMatcher?.let { name(it.value, it.matchType.toNative(), it.ignoreCase) }
        kmp.declaredClassMatcher?.let { declaredClass(it.toNative()) }
        kmp.returnTypeMatcher?.let { returnType(it.value, it.matchType.toNative(), it.ignoreCase) }
        if (kmp.paramTypeMatchers.isNotEmpty()) paramTypes = kmp.paramTypeMatchers.map { it?.value }
        kmp.paramCount?.let { paramCount = it }
        kmp.usingStringMatchers.forEach { addUsingString(it.value, it.matchType.toNative(), it.ignoreCase) }
        if (kmp.opCodes.isNotEmpty()) opCodes = kmp.opCodes
        kmp.modifiers?.let { modifiers(it) }
    }
}

internal fun KmpFieldMatcher.toNative(): NativeFieldMatcher {
    val kmp = this
    return NativeFieldMatcher().apply {
        kmp.nameMatcher?.let { name(it.value, it.matchType.toNative(), it.ignoreCase) }
        kmp.declaredClassMatcher?.let { declaredClass(it.toNative()) }
        kmp.typeMatcher?.let { type(it.value, it.matchType.toNative(), it.ignoreCase) }
        kmp.annotationMatchers.forEach { m ->
            addAnnotation(NativeAnnotationMatcher.create().type(m.value, m.matchType.toNative(), m.ignoreCase))
        }
        kmp.modifiers?.let { modifiers(it) }
    }
}

internal fun KmpFindClass.toNative(bridge: NativeDexKitBridge): NativeFindClass {
    val kmp = this
    return NativeFindClass().apply {
        if (kmp.searchPackages.isNotEmpty()) searchPackages(kmp.searchPackages)
        if (kmp.excludePackages.isNotEmpty()) excludePackages(kmp.excludePackages)
        ignorePackagesCase = kmp.ignorePackagesCase
        findFirst = kmp.findFirst
        val nativeClasses = kmp.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchIn(nativeClasses)
        kmp.matcher?.let { matcher(it.toNative()) }
    }
}

internal fun KmpFindMethod.toNative(bridge: NativeDexKitBridge): NativeFindMethod {
    val kmp = this
    return NativeFindMethod().apply {
        if (kmp.searchPackages.isNotEmpty()) searchPackages(kmp.searchPackages)
        if (kmp.excludePackages.isNotEmpty()) excludePackages(kmp.excludePackages)
        ignorePackagesCase = kmp.ignorePackagesCase
        findFirst = kmp.findFirst
        val nativeClasses = kmp.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchInClass(nativeClasses)
        val nativeMethods = kmp.searchInMethods.mapNotNull { bridge.getMethodData(it.descriptor) }
        if (nativeMethods.isNotEmpty()) searchInMethod(nativeMethods)
        kmp.matcher?.let { matcher(it.toNative()) }
    }
}

internal fun KmpFindField.toNative(bridge: NativeDexKitBridge): NativeFindField {
    val kmp = this
    return NativeFindField().apply {
        if (kmp.searchPackages.isNotEmpty()) searchPackages(kmp.searchPackages)
        if (kmp.excludePackages.isNotEmpty()) excludePackages(kmp.excludePackages)
        ignorePackagesCase = kmp.ignorePackagesCase
        findFirst = kmp.findFirst
        val nativeClasses = kmp.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchInClass(nativeClasses)
        val nativeFields = kmp.searchInFields.mapNotNull { bridge.getFieldData(it.descriptor) }
        if (nativeFields.isNotEmpty()) searchInField(nativeFields)
        kmp.matcher?.let { matcher(it.toNative()) }
    }
}

internal fun KmpBatchFindClassUsingStrings.toNative(bridge: NativeDexKitBridge): NativeBatchFindClassUsingStrings {
    val kmp = this
    return NativeBatchFindClassUsingStrings().apply {
        if (kmp.searchPackages.isNotEmpty()) searchPackages(kmp.searchPackages)
        if (kmp.excludePackages.isNotEmpty()) excludePackages(kmp.excludePackages)
        ignorePackagesCase = kmp.ignorePackagesCase
        val nativeClasses = kmp.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchIn(nativeClasses)
        kmp.groups.forEach { (name, matchers) ->
            if (matchers.isNotEmpty()) {
                val group = NativeStringMatchersGroup.create().apply {
                    groupName(name)
                    matchers.forEach { m ->
                        add(NativeStringMatcher.create(m.value, m.matchType.toNative(), m.ignoreCase))
                    }
                }
                addSearchGroup(group)
            }
        }
    }
}

internal fun KmpBatchFindMethodUsingStrings.toNative(bridge: NativeDexKitBridge): NativeBatchFindMethodUsingStrings {
    val kmp = this
    return NativeBatchFindMethodUsingStrings().apply {
        if (kmp.searchPackages.isNotEmpty()) searchPackages(kmp.searchPackages)
        if (kmp.excludePackages.isNotEmpty()) excludePackages(kmp.excludePackages)
        ignorePackagesCase = kmp.ignorePackagesCase
        val nativeClasses = kmp.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchInClasses(nativeClasses)
        val nativeMethods = kmp.searchInMethods.mapNotNull { bridge.getMethodData(it.descriptor) }
        if (nativeMethods.isNotEmpty()) searchInMethods(nativeMethods)
        kmp.groups.forEach { (name, matchers) ->
            if (matchers.isNotEmpty()) {
                val group = NativeStringMatchersGroup.create().apply {
                    groupName(name)
                    matchers.forEach { m ->
                        add(NativeStringMatcher.create(m.value, m.matchType.toNative(), m.ignoreCase))
                    }
                }
                addSearchGroup(group)
            }
        }
    }
}
