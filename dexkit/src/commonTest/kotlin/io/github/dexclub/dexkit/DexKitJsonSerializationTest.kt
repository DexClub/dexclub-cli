package io.github.dexclub.dexkit

import io.github.dexclub.dexkit.query.AnnotationEncodeValueMatcher
import io.github.dexclub.dexkit.query.AnnotationMatcher
import io.github.dexclub.dexkit.query.AnnotationsMatcher
import io.github.dexclub.dexkit.query.AnnotationElementMatcher
import io.github.dexclub.dexkit.query.AnnotationElementsMatcher
import io.github.dexclub.dexkit.query.AccessFlagsMatcher
import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.ClassMatcher
import io.github.dexclub.dexkit.query.FieldsMatcher
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.InterfacesMatcher
import io.github.dexclub.dexkit.query.MethodMatcher
import io.github.dexclub.dexkit.query.MethodsMatcher
import io.github.dexclub.dexkit.query.OpCodeMatchType
import io.github.dexclub.dexkit.query.OpCodesMatcher
import io.github.dexclub.dexkit.query.ParameterMatcher
import io.github.dexclub.dexkit.query.ParametersMatcher
import io.github.dexclub.dexkit.query.RetentionPolicyType
import io.github.dexclub.dexkit.query.StringMatchType
import io.github.dexclub.dexkit.query.StringMatcher
import io.github.dexclub.dexkit.query.TargetElementType
import io.github.dexclub.dexkit.query.TargetElementTypesMatcher
import io.github.dexclub.dexkit.result.ClassData
import io.github.dexclub.dexkit.result.MethodData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DexKitJsonSerializationTest {
    @Test
    fun `find class query should support json round trip`() {
        val query = FindClass(
            searchPackages = listOf("fixture.samples"),
            excludePackages = listOf("fixture.internal"),
            ignorePackagesCase = true,
            findFirst = true,
            searchInClasses = listOf(sampleClassData()),
            matcher = ClassMatcher(
                source = StringMatcher("SampleSearchTarget.java"),
                className = StringMatcher(
                    value = "SampleSearchTarget",
                    matchType = StringMatchType.Equals,
                ),
                modifiers = AccessFlagsMatcher(1),
                superClass = ClassMatcher(className = StringMatcher("java.lang.Object")),
                interfaces = InterfacesMatcher(
                    interfaces = mutableListOf(
                        ClassMatcher(className = StringMatcher("java.io.Serializable")),
                    ),
                ),
                annotations = AnnotationsMatcher(
                    annotations = mutableListOf(
                        AnnotationMatcher(type = ClassMatcher(className = StringMatcher("Lkotlin/Metadata;"))),
                    ),
                ),
                fields = FieldsMatcher(count = 2..2),
                methods = MethodsMatcher(count = 1..3),
            ).apply {
                usingStrings += StringMatcher("dexclub-needle-string")
            },
        )

        val restored = query.toDexKitJsonString().parseDexKitJson<FindClass>()
        val matcher = assertNotNull(restored.matcher)

        assertEquals(query.searchPackages, restored.searchPackages)
        assertEquals(query.excludePackages, restored.excludePackages)
        assertEquals(query.ignorePackagesCase, restored.ignorePackagesCase)
        assertEquals(query.findFirst, restored.findFirst)
        assertEquals(query.searchInClasses, restored.searchInClasses)
        assertEquals(query.matcher?.source, matcher.source)
        assertEquals(query.matcher?.className, matcher.className)
        assertEquals(query.matcher?.superClass?.className, matcher.superClass?.className)
        assertEquals(query.matcher?.interfaces, matcher.interfaces)
        assertEquals(query.matcher?.usingStrings, matcher.usingStrings)
        assertEquals(query.matcher?.annotations, matcher.annotations)
        assertEquals(1..3, matcher.methods?.count)
        assertEquals(2..2, matcher.fields?.count)
        assertEquals(query.matcher?.modifiers, matcher.modifiers)
    }

    @Test
    fun `batch query and result list should support json round trip`() {
        val query = BatchFindMethodUsingStrings().apply {
            searchPackages = listOf("fixture.samples")
            excludePackages = listOf("fixture.internal")
            ignorePackagesCase = true
            searchInClasses = listOf(sampleClassData())
            searchInMethods = listOf(sampleMethodData())
            groups["needle"] = listOf(
                StringMatcher("dexclub-needle-string"),
                StringMatcher("Sample", StringMatchType.StartsWith),
            )
        }

        val restoredQuery = query.toDexKitJsonString().parseDexKitJson<BatchFindMethodUsingStrings>()
        val results = listOf(sampleMethodData(), sampleMethodData(name = "helper"))
        val restoredResults = results.toDexKitJsonString().parseDexKitJson<List<MethodData>>()

        assertEquals(query.searchPackages, restoredQuery.searchPackages)
        assertEquals(query.excludePackages, restoredQuery.excludePackages)
        assertEquals(query.ignorePackagesCase, restoredQuery.ignorePackagesCase)
        assertEquals(query.searchInClasses, restoredQuery.searchInClasses)
        assertEquals(query.searchInMethods, restoredQuery.searchInMethods)
        assertEquals(query.groups, restoredQuery.groups)
        assertEquals(results, restoredResults)
    }

    @Test
    fun `method matcher should support nullable entries in json`() {
        val matcher = MethodMatcher(
            name = StringMatcher("exposeNeedle"),
            modifiers = AccessFlagsMatcher(1),
            declaredClass = ClassMatcher(className = StringMatcher("fixture.samples.SampleSearchTarget")),
            returnType = ClassMatcher(className = StringMatcher("java.lang.String")),
            params = ParametersMatcher(
                params = mutableListOf(
                    ParameterMatcher(type = ClassMatcher(className = StringMatcher("java.lang.String"))),
                    null,
                ),
                count = 2..2,
            ),
            opCodes = OpCodesMatcher(opCodes = mutableListOf(0x1A)),
            callerMethods = MethodsMatcher(
                methods = mutableListOf(
                    MethodMatcher(name = StringMatcher("invokeCaller")),
                ),
            ),
        ).apply {
            usingStrings += StringMatcher("dexclub-needle-string")
        }

        val restored = matcher.toDexKitJsonString().parseDexKitJson<MethodMatcher>()

        assertEquals(matcher.name, restored.name)
        assertEquals(matcher.declaredClass?.className, restored.declaredClass?.className)
        assertEquals(matcher.returnType?.className, restored.returnType?.className)
        assertEquals(matcher.params, restored.params)
        assertEquals(matcher.usingStrings, restored.usingStrings)
        assertEquals(matcher.opCodes, restored.opCodes)
        assertEquals(matcher.modifiers, restored.modifiers)
        assertEquals(matcher.callerMethods, restored.callerMethods)
    }

    @Test
    fun `annotation matcher and encode value should support json round trip`() {
        val matcher = AnnotationMatcher(
            type = ClassMatcher(className = StringMatcher("fixture.samples.Router")),
            targetElementTypes = TargetElementTypesMatcher(
                types = mutableListOf(TargetElementType.Type, TargetElementType.Method),
            ),
            policy = RetentionPolicyType.Runtime,
            elements = AnnotationElementsMatcher(
                elements = mutableListOf(
                    AnnotationElementMatcher(
                        name = StringMatcher("value"),
                        value = AnnotationEncodeValueMatcher(
                            stringValue = StringMatcher("/sample", StringMatchType.Equals),
                        ),
                    ),
                ),
            ),
        ).apply {
            usingStrings += StringMatcher("route")
        }

        val restored = matcher.toDexKitJsonString().parseDexKitJson<AnnotationMatcher>()

        assertEquals(matcher.type, restored.type)
        assertEquals(matcher.targetElementTypes, restored.targetElementTypes)
        assertEquals(matcher.policy, restored.policy)
        assertEquals(matcher.elements, restored.elements)
        assertEquals(matcher.usingStrings, restored.usingStrings)
    }

    @Test
    fun `method matcher should support annotation and opcode names in json`() {
        val matcher = MethodMatcher(
            annotations = AnnotationsMatcher(
                annotations = mutableListOf(
                    AnnotationMatcher(
                        type = ClassMatcher(className = StringMatcher("fixture.samples.Router")),
                        policy = RetentionPolicyType.Runtime,
                    ),
                ),
            ),
            opCodes = OpCodesMatcher(
                opNames = mutableListOf("const-string", "invoke-static"),
                matchType = OpCodeMatchType.StartsWith,
                size = 2..8,
            ),
        )

        val restored = matcher.toDexKitJsonString().parseDexKitJson<MethodMatcher>()

        assertEquals(matcher.annotations, restored.annotations)
        assertEquals(matcher.opCodes, restored.opCodes)
    }

    private fun sampleClassData() = ClassData(
        descriptor = "Lfixture/samples/SampleSearchTarget;",
        name = "fixture.samples.SampleSearchTarget",
        simpleName = "SampleSearchTarget",
        sourceFile = "SampleSearchTarget.java",
        modifiers = 1,
    )

    private fun sampleMethodData(name: String = "exposeNeedle") = MethodData(
        descriptor = "Lfixture/samples/SampleSearchTarget;->$name()Ljava/lang/String;",
        name = name,
        className = "fixture.samples.SampleSearchTarget",
        paramTypeNames = emptyList(),
        returnTypeName = "java.lang.String",
        modifiers = 1,
        isConstructor = false,
        isStaticInitializer = false,
    )
}
