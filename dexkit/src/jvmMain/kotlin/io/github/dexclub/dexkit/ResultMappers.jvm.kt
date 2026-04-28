package io.github.dexclub.dexkit

import io.github.dexclub.dexkit.query.AccessFlagsMatcher as KmpAccessFlagsMatcher
import io.github.dexclub.dexkit.query.AnnotationElementMatcher as KmpAnnotationElementMatcher
import io.github.dexclub.dexkit.query.AnnotationElementsMatcher as KmpAnnotationElementsMatcher
import io.github.dexclub.dexkit.query.AnnotationEncodeArrayMatcher as KmpAnnotationEncodeArrayMatcher
import io.github.dexclub.dexkit.query.AnnotationEncodeValueMatcher as KmpAnnotationEncodeValueMatcher
import io.github.dexclub.dexkit.query.AnnotationMatcher as KmpAnnotationMatcher
import io.github.dexclub.dexkit.query.AnnotationsMatcher as KmpAnnotationsMatcher
import io.github.dexclub.dexkit.query.BatchFindClassUsingStrings as KmpBatchFindClassUsingStrings
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings as KmpBatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.ClassMatcher as KmpClassMatcher
import io.github.dexclub.dexkit.query.FieldMatcher as KmpFieldMatcher
import io.github.dexclub.dexkit.query.FieldsMatcher as KmpFieldsMatcher
import io.github.dexclub.dexkit.query.FindClass as KmpFindClass
import io.github.dexclub.dexkit.query.FindField as KmpFindField
import io.github.dexclub.dexkit.query.FindMethod as KmpFindMethod
import io.github.dexclub.dexkit.query.InterfacesMatcher as KmpInterfacesMatcher
import io.github.dexclub.dexkit.query.MatchType as KmpMatchType
import io.github.dexclub.dexkit.query.MethodMatcher as KmpMethodMatcher
import io.github.dexclub.dexkit.query.MethodsMatcher as KmpMethodsMatcher
import io.github.dexclub.dexkit.query.OpCodeMatchType as KmpOpCodeMatchType
import io.github.dexclub.dexkit.query.OpCodesMatcher as KmpOpCodesMatcher
import io.github.dexclub.dexkit.query.ParameterMatcher as KmpParameterMatcher
import io.github.dexclub.dexkit.query.ParametersMatcher as KmpParametersMatcher
import io.github.dexclub.dexkit.query.RetentionPolicyType as KmpRetentionPolicyType
import io.github.dexclub.dexkit.query.StringMatchType as KmpStringMatchType
import io.github.dexclub.dexkit.query.TargetElementType as KmpTargetElementType
import io.github.dexclub.dexkit.query.TargetElementTypesMatcher as KmpTargetElementTypesMatcher
import io.github.dexclub.dexkit.query.UsingFieldMatcher as KmpUsingFieldMatcher
import io.github.dexclub.dexkit.query.UsingType as KmpUsingType
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.FieldUsingType
import io.github.dexclub.dexkit.result.FieldData
import io.github.dexclub.dexkit.result.MethodData
import io.github.dexclub.dexkit.result.UsingFieldData
import org.luckypray.dexkit.DexKitBridge as NativeDexKitBridge
import org.luckypray.dexkit.query.BatchFindClassUsingStrings as NativeBatchFindClassUsingStrings
import org.luckypray.dexkit.query.BatchFindMethodUsingStrings as NativeBatchFindMethodUsingStrings
import org.luckypray.dexkit.query.FindClass as NativeFindClass
import org.luckypray.dexkit.query.FindField as NativeFindField
import org.luckypray.dexkit.query.FindMethod as NativeFindMethod
import org.luckypray.dexkit.query.enums.RetentionPolicyType as NativeRetentionPolicyType
import org.luckypray.dexkit.query.enums.MatchType as NativeMatchType
import org.luckypray.dexkit.query.enums.OpCodeMatchType as NativeOpCodeMatchType
import org.luckypray.dexkit.query.enums.StringMatchType as NativeStringMatchType
import org.luckypray.dexkit.query.enums.TargetElementType as NativeTargetElementType
import org.luckypray.dexkit.query.enums.UsingType as NativeUsingType
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher as NativeAnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationElementsMatcher as NativeAnnotationElementsMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher as NativeAnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher as NativeAnnotationMatcher
import org.luckypray.dexkit.query.matchers.AnnotationsMatcher as NativeAnnotationsMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher as NativeClassMatcher
import org.luckypray.dexkit.query.matchers.FieldMatcher as NativeFieldMatcher
import org.luckypray.dexkit.query.matchers.FieldsMatcher as NativeFieldsMatcher
import org.luckypray.dexkit.query.matchers.InterfacesMatcher as NativeInterfacesMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher as NativeMethodMatcher
import org.luckypray.dexkit.query.matchers.MethodsMatcher as NativeMethodsMatcher
import org.luckypray.dexkit.query.matchers.ParameterMatcher as NativeParameterMatcher
import org.luckypray.dexkit.query.matchers.ParametersMatcher as NativeParametersMatcher
import org.luckypray.dexkit.query.matchers.StringMatchersGroup as NativeStringMatchersGroup
import org.luckypray.dexkit.query.matchers.UsingFieldMatcher as NativeUsingFieldMatcher
import org.luckypray.dexkit.query.matchers.base.AccessFlagsMatcher as NativeAccessFlagsMatcher
import org.luckypray.dexkit.query.matchers.base.AnnotationEncodeValueMatcher as NativeAnnotationEncodeValueMatcher
import org.luckypray.dexkit.query.matchers.base.OpCodesMatcher as NativeOpCodesMatcher
import org.luckypray.dexkit.query.matchers.base.StringMatcher as NativeStringMatcher
import org.luckypray.dexkit.query.matchers.base.TargetElementTypesMatcher as NativeTargetElementTypesMatcher
import org.luckypray.dexkit.result.ClassData as NativeClassData
import org.luckypray.dexkit.result.FieldData as NativeFieldData
import org.luckypray.dexkit.result.FieldUsingType as NativeFieldUsingType
import org.luckypray.dexkit.result.MethodData as NativeMethodData
import org.luckypray.dexkit.result.UsingFieldData as NativeUsingFieldData

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

internal fun NativeFieldUsingType.toKmpFieldUsingType(): FieldUsingType = when (this) {
    NativeFieldUsingType.Read -> FieldUsingType.Read
    NativeFieldUsingType.Write -> FieldUsingType.Write
}

internal fun NativeUsingFieldData.toKmpUsingFieldData(): UsingFieldData =
    UsingFieldData(
        field = field.toKmpFieldData(),
        usingType = usingType.toKmpFieldUsingType(),
    )

internal fun KmpStringMatchType.toNative(): NativeStringMatchType = when (this) {
    KmpStringMatchType.Contains -> NativeStringMatchType.Contains
    KmpStringMatchType.StartsWith -> NativeStringMatchType.StartsWith
    KmpStringMatchType.EndsWith -> NativeStringMatchType.EndsWith
    KmpStringMatchType.Equals -> NativeStringMatchType.Equals
    KmpStringMatchType.SimilarRegex -> NativeStringMatchType.SimilarRegex
}

internal fun KmpMatchType.toNative(): NativeMatchType = when (this) {
    KmpMatchType.Contains -> NativeMatchType.Contains
    KmpMatchType.Equals -> NativeMatchType.Equals
}

internal fun KmpOpCodeMatchType.toNative(): NativeOpCodeMatchType = when (this) {
    KmpOpCodeMatchType.Contains -> NativeOpCodeMatchType.Contains
    KmpOpCodeMatchType.StartsWith -> NativeOpCodeMatchType.StartsWith
    KmpOpCodeMatchType.EndsWith -> NativeOpCodeMatchType.EndsWith
    KmpOpCodeMatchType.Equals -> NativeOpCodeMatchType.Equals
}

internal fun KmpUsingType.toNative(): NativeUsingType = when (this) {
    KmpUsingType.Any -> NativeUsingType.Any
    KmpUsingType.Read -> NativeUsingType.Read
    KmpUsingType.Write -> NativeUsingType.Write
}

internal fun KmpRetentionPolicyType.toNative(): NativeRetentionPolicyType = when (this) {
    KmpRetentionPolicyType.Source -> NativeRetentionPolicyType.Source
    KmpRetentionPolicyType.Class -> NativeRetentionPolicyType.Class
    KmpRetentionPolicyType.Runtime -> NativeRetentionPolicyType.Runtime
}

internal fun KmpTargetElementType.toNative(): NativeTargetElementType = when (this) {
    KmpTargetElementType.Type -> NativeTargetElementType.Type
    KmpTargetElementType.Field -> NativeTargetElementType.Field
    KmpTargetElementType.Method -> NativeTargetElementType.Method
    KmpTargetElementType.Parameter -> NativeTargetElementType.Parameter
    KmpTargetElementType.Constructor -> NativeTargetElementType.Constructor
    KmpTargetElementType.LocalVariable -> NativeTargetElementType.LocalVariable
    KmpTargetElementType.AnnotationType -> NativeTargetElementType.AnnotationType
    KmpTargetElementType.Package -> NativeTargetElementType.Package
    KmpTargetElementType.TypeParameter -> NativeTargetElementType.TypeParameter
    KmpTargetElementType.TypeUse -> NativeTargetElementType.TypeUse
}

internal fun KmpAccessFlagsMatcher.toNative(): NativeAccessFlagsMatcher =
    NativeAccessFlagsMatcher.create(modifiers, matchType.toNative())

internal fun KmpTargetElementTypesMatcher.toNative(): NativeTargetElementTypesMatcher =
    NativeTargetElementTypesMatcher().apply {
        if (this@toNative.types.isNotEmpty()) types(this@toNative.types.map { it.toNative() })
        matchType(this@toNative.matchType.toNative())
    }

internal fun KmpAnnotationEncodeValueMatcher.toNative(): NativeAnnotationEncodeValueMatcher =
    NativeAnnotationEncodeValueMatcher().apply {
        when {
            this@toNative.byteValue != null -> byteValue(this@toNative.byteValue!!)
            this@toNative.shortValue != null -> shortValue(this@toNative.shortValue!!)
            this@toNative.charValue != null -> charValue(this@toNative.charValue!!)
            this@toNative.intValue != null -> intValue(this@toNative.intValue!!)
            this@toNative.longValue != null -> longValue(this@toNative.longValue!!)
            this@toNative.floatValue != null -> floatValue(this@toNative.floatValue!!)
            this@toNative.doubleValue != null -> doubleValue(this@toNative.doubleValue!!)
            this@toNative.stringValue != null -> stringValue(
                this@toNative.stringValue!!.value,
                this@toNative.stringValue!!.matchType.toNative(),
                this@toNative.stringValue!!.ignoreCase,
            )
            this@toNative.classValue != null -> classValue(this@toNative.classValue!!.toNative())
            this@toNative.methodValue != null -> methodValue(this@toNative.methodValue!!.toNative())
            this@toNative.enumValue != null -> enumValue(this@toNative.enumValue!!.toNative())
            this@toNative.arrayValue != null -> arrayValue(this@toNative.arrayValue!!.toNative())
            this@toNative.annotationValue != null -> annotationValue(this@toNative.annotationValue!!.toNative())
            this@toNative.nullValue -> nullValue()
            this@toNative.boolValue != null -> boolValue(this@toNative.boolValue!!)
            else -> error("AnnotationEncodeValueMatcher 至少要设置一个值")
        }
    }

internal fun KmpAnnotationElementMatcher.toNative(): NativeAnnotationElementMatcher =
    NativeAnnotationElementMatcher().apply {
        this@toNative.name?.let { name(it.value, it.matchType.toNative(), it.ignoreCase) }
        this@toNative.value?.let { value(it.toNative()) }
    }

internal fun KmpAnnotationElementsMatcher.toNative(): NativeAnnotationElementsMatcher =
    NativeAnnotationElementsMatcher().apply {
        if (this@toNative.elements.isNotEmpty()) elements(this@toNative.elements.map { it.toNative() })
        matchType(this@toNative.matchType.toNative())
        this@toNative.count?.let { count(it.first, it.last) }
    }

internal fun KmpAnnotationEncodeArrayMatcher.toNative(): NativeAnnotationEncodeArrayMatcher =
    NativeAnnotationEncodeArrayMatcher().apply {
        if (this@toNative.values.isNotEmpty()) values(this@toNative.values.map { it.toNative() })
        matchType(this@toNative.matchType.toNative())
        this@toNative.count?.let { count(it.first, it.last) }
    }

internal fun KmpAnnotationMatcher.toNative(): NativeAnnotationMatcher =
    NativeAnnotationMatcher().apply {
        this@toNative.type?.let { type(it.toNative()) }
        this@toNative.targetElementTypes?.let { targetElementTypes(it.toNative()) }
        this@toNative.policy?.let { policy(it.toNative()) }
        this@toNative.elements?.let { elements(it.toNative()) }
        this@toNative.usingStrings.forEach { addUsingString(it.value, it.matchType.toNative(), it.ignoreCase) }
    }

internal fun KmpAnnotationsMatcher.toNative(): NativeAnnotationsMatcher =
    NativeAnnotationsMatcher().apply {
        if (this@toNative.annotations.isNotEmpty()) annotations(this@toNative.annotations.map { it.toNative() })
        matchType(this@toNative.matchType.toNative())
        this@toNative.count?.let { count(it.first, it.last) }
    }

internal fun KmpInterfacesMatcher.toNative(): NativeInterfacesMatcher =
    NativeInterfacesMatcher().apply {
        if (this@toNative.interfaces.isNotEmpty()) interfaces(this@toNative.interfaces.map { it.toNative() })
        matchType(this@toNative.matchType.toNative())
        this@toNative.count?.let { count(it.first, it.last) }
    }

internal fun KmpFieldsMatcher.toNative(): NativeFieldsMatcher =
    NativeFieldsMatcher().apply {
        if (this@toNative.fields.isNotEmpty()) fields(this@toNative.fields.map { it.toNative() })
        matchType(this@toNative.matchType.toNative())
        this@toNative.count?.let { count(it.first, it.last) }
    }

internal fun KmpMethodsMatcher.toNative(): NativeMethodsMatcher =
    NativeMethodsMatcher().apply {
        if (this@toNative.methods.isNotEmpty()) methods(this@toNative.methods.map { it.toNative() })
        matchType(this@toNative.matchType.toNative())
        this@toNative.count?.let { count(it.first, it.last) }
    }

internal fun KmpParameterMatcher.toNative(): NativeParameterMatcher =
    NativeParameterMatcher().apply {
        this@toNative.type?.let { type(it.toNative()) }
        this@toNative.annotations?.let { annotations(it.toNative()) }
    }

internal fun KmpParametersMatcher.toNative(): NativeParametersMatcher =
    NativeParametersMatcher().apply {
        if (this@toNative.params.isNotEmpty()) params(this@toNative.params.map { it?.toNative() })
        this@toNative.count?.let { count(it.first, it.last) }
    }

internal fun KmpOpCodesMatcher.toNative(): NativeOpCodesMatcher =
    NativeOpCodesMatcher().apply {
        if (this@toNative.opNames.isNotEmpty()) {
            opNames(this@toNative.opNames)
        } else if (this@toNative.opCodes.isNotEmpty()) {
            opCodes(this@toNative.opCodes)
        }
        matchType(this@toNative.matchType.toNative())
        this@toNative.size?.let { size(it.first, it.last) }
    }

internal fun KmpUsingFieldMatcher.toNative(): NativeUsingFieldMatcher =
    NativeUsingFieldMatcher().apply {
        this@toNative.field?.let { field(it.toNative()) }
        usingType(this@toNative.usingType.toNative())
    }

internal fun KmpClassMatcher.toNative(): NativeClassMatcher =
    NativeClassMatcher().apply {
        this@toNative.source?.let { source(it.value, it.matchType.toNative(), it.ignoreCase) }
        this@toNative.className?.let { className(it.value, it.matchType.toNative(), it.ignoreCase) }
        this@toNative.modifiers?.let { modifiers(it.toNative()) }
        this@toNative.superClass?.let { superClass(it.toNative()) }
        this@toNative.interfaces?.let { interfaces(it.toNative()) }
        this@toNative.annotations?.let { annotations(it.toNative()) }
        this@toNative.fields?.let { fields(it.toNative()) }
        this@toNative.methods?.let { methods(it.toNative()) }
        this@toNative.usingStrings.forEach { addUsingString(it.value, it.matchType.toNative(), it.ignoreCase) }
    }

internal fun KmpFieldMatcher.toNative(): NativeFieldMatcher =
    NativeFieldMatcher().apply {
        this@toNative.name?.let { name(it.value, it.matchType.toNative(), it.ignoreCase) }
        this@toNative.modifiers?.let { modifiers(it.toNative()) }
        this@toNative.declaredClass?.let { declaredClass(it.toNative()) }
        this@toNative.type?.let { type(it.toNative()) }
        this@toNative.annotations?.let { annotations(it.toNative()) }
        this@toNative.readMethods?.let { readMethods(it.toNative()) }
        this@toNative.writeMethods?.let { writeMethods(it.toNative()) }
    }

internal fun KmpMethodMatcher.toNative(): NativeMethodMatcher =
    NativeMethodMatcher().apply {
        this@toNative.name?.let { name(it.value, it.matchType.toNative(), it.ignoreCase) }
        this@toNative.modifiers?.let { modifiers(it.toNative()) }
        this@toNative.declaredClass?.let { declaredClass(it.toNative()) }
        this@toNative.protoShorty?.let { protoShorty(it) }
        this@toNative.returnType?.let { returnType(it.toNative()) }
        this@toNative.params?.let { params(it.toNative()) }
        this@toNative.annotations?.let { annotations(it.toNative()) }
        this@toNative.opCodes?.let { opCodes(it.toNative()) }
        this@toNative.usingStrings.forEach { addUsingString(it.value, it.matchType.toNative(), it.ignoreCase) }
        if (this@toNative.usingFields.isNotEmpty()) usingFields(this@toNative.usingFields.map { it.toNative() })
        if (this@toNative.usingNumbers.isNotEmpty()) {
            usingNumbers(this@toNative.usingNumbers.map { it.toNativeNumber() })
        }
        this@toNative.invokeMethods?.let { invokeMethods(it.toNative()) }
        this@toNative.callerMethods?.let { callerMethods(it.toNative()) }
    }

private fun io.github.dexclub.dexkit.query.NumberEncodeValueMatcher.toNativeNumber(): Number =
    byteValue
        ?: shortValue
        ?: charValue?.code
        ?: intValue
        ?: longValue
        ?: floatValue
        ?: doubleValue
        ?: error("NumberEncodeValueMatcher 至少要设置一个值")

internal fun KmpFindClass.toNative(bridge: NativeDexKitBridge): NativeFindClass =
    NativeFindClass().apply {
        if (this@toNative.searchPackages.isNotEmpty()) searchPackages(this@toNative.searchPackages)
        if (this@toNative.excludePackages.isNotEmpty()) excludePackages(this@toNative.excludePackages)
        ignorePackagesCase = this@toNative.ignorePackagesCase
        findFirst = this@toNative.findFirst
        val nativeClasses = this@toNative.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchIn(nativeClasses)
        this@toNative.matcher?.let { matcher(it.toNative()) }
    }

internal fun KmpFindMethod.toNative(bridge: NativeDexKitBridge): NativeFindMethod =
    NativeFindMethod().apply {
        if (this@toNative.searchPackages.isNotEmpty()) searchPackages(this@toNative.searchPackages)
        if (this@toNative.excludePackages.isNotEmpty()) excludePackages(this@toNative.excludePackages)
        ignorePackagesCase = this@toNative.ignorePackagesCase
        findFirst = this@toNative.findFirst
        val nativeClasses = this@toNative.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchInClass(nativeClasses)
        val nativeMethods = this@toNative.searchInMethods.mapNotNull { bridge.getMethodData(it.descriptor) }
        if (nativeMethods.isNotEmpty()) searchInMethod(nativeMethods)
        this@toNative.matcher?.let { matcher(it.toNative()) }
    }

internal fun KmpFindField.toNative(bridge: NativeDexKitBridge): NativeFindField =
    NativeFindField().apply {
        if (this@toNative.searchPackages.isNotEmpty()) searchPackages(this@toNative.searchPackages)
        if (this@toNative.excludePackages.isNotEmpty()) excludePackages(this@toNative.excludePackages)
        ignorePackagesCase = this@toNative.ignorePackagesCase
        findFirst = this@toNative.findFirst
        val nativeClasses = this@toNative.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchInClass(nativeClasses)
        val nativeFields = this@toNative.searchInFields.mapNotNull { bridge.getFieldData(it.descriptor) }
        if (nativeFields.isNotEmpty()) searchInField(nativeFields)
        this@toNative.matcher?.let { matcher(it.toNative()) }
    }

internal fun KmpBatchFindClassUsingStrings.toNative(bridge: NativeDexKitBridge): NativeBatchFindClassUsingStrings =
    NativeBatchFindClassUsingStrings().apply {
        if (this@toNative.searchPackages.isNotEmpty()) searchPackages(this@toNative.searchPackages)
        if (this@toNative.excludePackages.isNotEmpty()) excludePackages(this@toNative.excludePackages)
        ignorePackagesCase = this@toNative.ignorePackagesCase
        val nativeClasses = this@toNative.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchIn(nativeClasses)
        this@toNative.groups.forEach { (name, matchers) ->
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

internal fun KmpBatchFindMethodUsingStrings.toNative(bridge: NativeDexKitBridge): NativeBatchFindMethodUsingStrings =
    NativeBatchFindMethodUsingStrings().apply {
        if (this@toNative.searchPackages.isNotEmpty()) searchPackages(this@toNative.searchPackages)
        if (this@toNative.excludePackages.isNotEmpty()) excludePackages(this@toNative.excludePackages)
        ignorePackagesCase = this@toNative.ignorePackagesCase
        val nativeClasses = this@toNative.searchInClasses.mapNotNull { bridge.getClassData(it.descriptor) }
        if (nativeClasses.isNotEmpty()) searchInClasses(nativeClasses)
        val nativeMethods = this@toNative.searchInMethods.mapNotNull { bridge.getMethodData(it.descriptor) }
        if (nativeMethods.isNotEmpty()) searchInMethods(nativeMethods)
        this@toNative.groups.forEach { (name, matchers) ->
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
