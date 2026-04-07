package io.github.dexclub.dexkit

import io.github.dexclub.dexkit.query.BatchFindMethodUsingStrings
import io.github.dexclub.dexkit.query.ClassMatcher
import io.github.dexclub.dexkit.query.FindClass
import io.github.dexclub.dexkit.query.MethodMatcher
import io.github.dexclub.dexkit.query.StringMatchType
import io.github.dexclub.dexkit.query.StringMatcher
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
            matcher = ClassMatcher().apply {
                className(
                    value = "SampleSearchTarget",
                    matchType = StringMatchType.Equals,
                )
                superClass("java.lang.Object")
                addInterface("java.io.Serializable")
                addUsingString("dexclub-needle-string")
                addAnnotation("Lkotlin/Metadata;")
                methodCount(min = 1, max = 3)
                fieldCount(count = 2)
                modifiers = 1
            },
        )

        val restored = query.toDexKitJsonString().parseDexKitJson<FindClass>()
        val matcher = assertNotNull(restored.matcher)

        assertEquals(query.searchPackages, restored.searchPackages)
        assertEquals(query.excludePackages, restored.excludePackages)
        assertEquals(query.ignorePackagesCase, restored.ignorePackagesCase)
        assertEquals(query.findFirst, restored.findFirst)
        assertEquals(query.searchInClasses, restored.searchInClasses)
        assertEquals(query.matcher?.classNameMatcher, matcher.classNameMatcher)
        assertEquals(query.matcher?.superClassMatcher?.classNameMatcher, matcher.superClassMatcher?.classNameMatcher)
        assertEquals(query.matcher?.interfaceMatchers, matcher.interfaceMatchers)
        assertEquals(query.matcher?.usingStringMatchers, matcher.usingStringMatchers)
        assertEquals(query.matcher?.annotationMatchers, matcher.annotationMatchers)
        assertEquals(1..3, matcher.methodCountRange)
        assertEquals(2..2, matcher.fieldCountRange)
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
            addGroup(
                name = "needle",
                matchers = listOf(
                    StringMatcher("dexclub-needle-string"),
                    StringMatcher("Sample", StringMatchType.StartsWith),
                ),
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
        val matcher = MethodMatcher().apply {
            name("exposeNeedle")
            declaredClass("fixture.samples.SampleSearchTarget")
            returnType("java.lang.String")
            paramTypes("java.lang.String", null)
            paramCount = 2
            addUsingString("dexclub-needle-string")
            addOpCode(0x1A)
            modifiers = 1
        }

        val restored = matcher.toDexKitJsonString().parseDexKitJson<MethodMatcher>()

        assertEquals(matcher.nameMatcher, restored.nameMatcher)
        assertEquals(matcher.declaredClassMatcher?.classNameMatcher, restored.declaredClassMatcher?.classNameMatcher)
        assertEquals(matcher.returnTypeMatcher, restored.returnTypeMatcher)
        assertEquals(matcher.paramTypeMatchers, restored.paramTypeMatchers)
        assertEquals(matcher.paramCount, restored.paramCount)
        assertEquals(matcher.usingStringMatchers, restored.usingStringMatchers)
        assertEquals(matcher.opCodes, restored.opCodes)
        assertEquals(matcher.modifiers, restored.modifiers)
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
