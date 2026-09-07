package io.github.dexclub.mcp

import io.github.dexclub.core.api.dex.FindClassQuery
import io.github.dexclub.core.api.dex.FindFieldQuery
import io.github.dexclub.core.api.dex.FindMethodQuery
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpSkillContractTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun dexclubAnalysisSkillDocumentsCurrentCoreSurface() {
        val skillText = Files.readString(skillRoot().resolve("SKILL.md"))
        val documentedTools = skillText
            .substringAfter("## Useful MCP Surface")
            .lineSequence()
            .mapNotNull { usefulToolPattern.matchEntire(it)?.groupValues?.get(1) }
            .toSet()
        val catalogTools = McpToolCatalogs.tools.map(McpToolMetadata::name).toSet()

        assertEquals(catalogTools, documentedTools)
        assertTrue("references/find-queries.md" in skillText)
        assertTrue("references/find-query-fields.md" in skillText)
        assertFalse("find_classes_using_strings" in skillText)
        assertFalse("find_methods_using_strings" in skillText)
        assertTrue("__DEXCLUB_VERSION__" in skillText)
        assertTrue("__DEXCLUB_MCP_CONTRACT_VERSION__" in skillText)
        assertTrue("`get_server_info`" in skillText)
        assertTrue("before any other DexClub tool" in skillText)
        assertTrue("`mcp_contract_version`" in skillText)
        assertTrue("Do not copy the version returned by the server" in skillText)
    }

    @Test
    fun documentedFindQueryExamplesMatchPublicDtos() {
        val referenceText = Files.readString(skillRoot().resolve("references/find-queries.md"))
        val examples = queryExamplePattern.findAll(referenceText).toList()

        assertEquals(7, examples.size)
        examples.forEach { match ->
            val toolName = match.groupValues[1]
            val queryText = match.groupValues[2]
            assertFalse("searchIn" in queryText, "example must not depend on native result pointers")
            when (toolName) {
                "find_classes" -> json.decodeFromString(FindClassQuery.serializer(), queryText)
                "find_methods" -> json.decodeFromString(FindMethodQuery.serializer(), queryText)
                "find_fields" -> json.decodeFromString(FindFieldQuery.serializer(), queryText)
                else -> error("unsupported query example marker: $toolName")
            }
        }
    }

    @Test
    fun documentedResourceIdQueriesUseSignedDecimalIntValues() {
        val referenceText = Files.readString(skillRoot().resolve("references/find-queries.md"))
        val conversions = resourceIdConversionPattern.findAll(referenceText).toList()

        assertEquals(2, conversions.size)
        conversions.forEach { match ->
            val unsignedValue = match.groupValues[1].removePrefix("0x").toLong(16)
            val documentedDecimal = match.groupValues[2].toInt()
            assertEquals(unsignedValue.toInt(), documentedDecimal)
        }

        val resourceMatchers = queryExamplePattern.findAll(referenceText)
            .filter { it.groupValues[1] == "find_methods" }
            .map { json.decodeFromString(FindMethodQuery.serializer(), it.groupValues[2]) }
            .flatMap { it.matcher?.usingNumbers.orEmpty() }
            .toList()
        val resourceMatcher = resourceMatchers.single()

        assertEquals(2131362083, resourceMatcher.intValue)
        assertEquals(null, resourceMatcher.longValue)
    }

    @Test
    fun skillDocumentsCoverageAwarePaging() {
        val skillText = Files.readString(skillRoot().resolve("SKILL.md"))
        val referenceText = Files.readString(skillRoot().resolve("references/find-queries.md"))

        assertTrue("Treat every page as a result window" in skillText)
        assertTrue("do not infer absence, uniqueness, prevalence" in skillText)
        assertTrue("independent-clue narrowing, exhaustive coverage, and stratified exploration" in skillText)
        assertTrue("examined/total" in skillText)

        assertTrue("## Paging and Projection" in referenceText)
        assertTrue("### Independent-Clue Narrowing" in referenceText)
        assertTrue("### Exhaustive Coverage" in referenceText)
        assertTrue("### Stratified Exploration" in referenceText)
        assertTrue("### Stopping and Conclusions" in referenceText)
        assertTrue("Never claim absence from sampling or an incomplete page sequence" in referenceText)
    }

    @Test
    fun documentedFindFieldInventoryMatchesPublicSerializerDescriptors() {
        val referenceText = Files.readString(skillRoot().resolve("references/find-query-fields.md"))
        val documentedObjects = parseDocumentedObjects(referenceText)
        val documentedEnums = parseDocumentedEnums(referenceText)
        val inventory = descriptorInventory(
            FindClassQuery.serializer().descriptor,
            FindMethodQuery.serializer().descriptor,
            FindFieldQuery.serializer().descriptor,
        )

        assertEquals(inventory.objects, documentedObjects)
        assertEquals(inventory.enums, documentedEnums)
        assertFalse("searchInClasses" in referenceText)
        assertFalse("searchInMethods" in referenceText)
        assertFalse("searchInFields" in referenceText)
    }

    private fun skillRoot(): Path =
        repoRoot().resolve("skills/dexclub-analysis")

    private fun repoRoot(): Path {
        val configured = System.getProperty("dexclub.repo.root")
            ?: error("missing dexclub.repo.root test system property")
        return Path.of(configured).toAbsolutePath().normalize()
    }

    private fun parseDocumentedObjects(text: String): Map<String, Map<String, Boolean>> =
        parseMarkedTables(text, "schema-object").mapValues { (_, rows) ->
            rows.associate { cells ->
                require(cells.size == 4) { "object field table must have four columns: $cells" }
                cells[0].removeSurrounding("`") to when (cells[2]) {
                    "yes" -> true
                    "no" -> false
                    else -> error("unsupported Required value: ${cells[2]}")
                }
            }
        }

    private fun parseDocumentedEnums(text: String): Map<String, Set<String>> =
        parseMarkedTables(text, "schema-enum").mapValues { (_, rows) ->
            rows.map { cells ->
                require(cells.size == 1) { "enum table must have one column: $cells" }
                cells.single().removeSurrounding("`")
            }.toSet()
        }

    private fun parseMarkedTables(text: String, marker: String): Map<String, List<List<String>>> {
        val lines = text.lines()
        val result = linkedMapOf<String, List<List<String>>>()
        lines.forEachIndexed { markerIndex, rawLine ->
            val line = rawLine.trim()
            val prefix = "<!-- $marker:"
            if (!line.startsWith(prefix) || !line.endsWith(" -->")) return@forEachIndexed
            val typeName = line.removePrefix(prefix).removeSuffix(" -->")
            val headerIndex = (markerIndex + 1 until lines.size)
                .firstOrNull { lines[it].trim().startsWith("|") }
                ?: error("missing table for $typeName")
            val rows = mutableListOf<List<String>>()
            var rowIndex = headerIndex + 2
            while (rowIndex < lines.size && lines[rowIndex].trim().startsWith("|")) {
                rows += lines[rowIndex].trim().trim('|').split('|').map(String::trim)
                rowIndex += 1
            }
            require(rows.isNotEmpty()) { "empty table for $typeName" }
            require(result.put(typeName, rows) == null) { "duplicate table marker for $typeName" }
        }
        return result
    }

    private fun descriptorInventory(vararg roots: SerialDescriptor): DescriptorInventory {
        val objects = linkedMapOf<String, Map<String, Boolean>>()
        val enums = linkedMapOf<String, Set<String>>()

        fun visit(descriptor: SerialDescriptor) {
            val typeName = descriptor.serialName.removeSuffix("?").substringAfterLast('.')
            when (descriptor.kind) {
                SerialKind.ENUM -> {
                    enums.putIfAbsent(
                        typeName,
                        (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet(),
                    )
                }
                StructureKind.CLASS,
                StructureKind.OBJECT,
                -> {
                    if (typeName in objects) return
                    objects[typeName] = (0 until descriptor.elementsCount).associate { index ->
                        descriptor.getElementName(index) to !descriptor.isElementOptional(index)
                    }
                    repeat(descriptor.elementsCount) { index ->
                        visit(descriptor.getElementDescriptor(index))
                    }
                }
                StructureKind.LIST -> visit(descriptor.getElementDescriptor(0))
                StructureKind.MAP -> visit(descriptor.getElementDescriptor(1))
                else -> Unit
            }
        }

        roots.forEach(::visit)
        return DescriptorInventory(objects = objects, enums = enums)
    }

    private companion object {
        val usefulToolPattern = Regex("""- `([a-z_]+)`""")
        val queryExamplePattern = Regex(
            """<!-- query-example:(find_classes|find_methods|find_fields) -->\s*```json\s*(.*?)\s*```""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val resourceIdConversionPattern = Regex(
            """<!-- resource-id-conversion:(0x[0-9a-fA-F]{8})=(-?\d+) -->""",
        )
    }

    private data class DescriptorInventory(
        val objects: Map<String, Map<String, Boolean>>,
        val enums: Map<String, Set<String>>,
    )
}
