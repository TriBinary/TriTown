package net.trilleo.mc.plugins.tritown.utils

import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the bundled translations: every language defines the same keys with the
 * same placeholders, and every key the code uses exists.
 */
class LangFilesTest {

    private val languages = listOf("en_US", "zh_CN").associateWith { load(it) }
    private val english = languages.getValue("en_US")

    /** Keys built at runtime (`"money.source.$source"`), which the source scan cannot see. */
    private val dynamicPrefixes = listOf("command.", "money.source.")

    private val keyLiteral = Regex("\"([a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)+)\"")
    private val placeholder = Regex("\\{([a-z]+)}")

    /** Doc comments carry example keys that no language defines, so they are dropped before the scan. */
    private val comment = Regex("/\\*[\\s\\S]*?\\*/|(?m)^\\s*//.*$")

    @Test
    fun `every language has the same keys as English`() {
        languages.forEach { (id, values) ->
            assertEquals(emptySet(), english.keys - values.keys, "Keys missing from $id.yml")
            assertEquals(emptySet(), values.keys - english.keys, "Keys in $id.yml that en_US.yml lacks")
        }
    }

    @Test
    fun `every translation keeps the English placeholders`() {
        languages.forEach { (id, values) ->
            val mismatched = english.filter { (key, text) ->
                values[key]?.let { placeholders(it) != placeholders(text) } == true
            }.keys
            assertEquals(emptySet(), mismatched, "Placeholders differ from en_US.yml in $id.yml")
        }
    }

    @Test
    fun `every key used in the code exists`() {
        val missing = usedKeys() - english.keys
        assertEquals(emptySet(), missing, "Keys used in the code but missing from en_US.yml")
    }

    @Test
    fun `every translation is used by the code`() {
        val used = usedKeys()
        val unused = english.keys.filter { key -> key !in used && dynamicPrefixes.none(key::startsWith) }
        assertTrue(unused.isEmpty(), "Keys in en_US.yml that no code uses: $unused")
    }

    /** Dotted string literals whose first segment is a section of en_US.yml, so config paths don't count. */
    private fun usedKeys(): Set<String> {
        val sections = english.keys.map { it.substringBefore('.') }.toSet()
        return File("src/main/kotlin").walk()
            .filter { it.extension == "kt" }
            .flatMap { file -> keyLiteral.findAll(comment.replace(file.readText(), "")).map { it.groupValues[1] } }
            .filter { it.substringBefore('.') in sections }
            .toSet()
    }

    private fun placeholders(text: String): Set<String> = placeholder.findAll(text).map { it.groupValues[1] }.toSet()

    private fun load(id: String): Map<String, String> {
        val stream =
            checkNotNull(javaClass.classLoader.getResourceAsStream("lang/$id.yml")) { "lang/$id.yml is not bundled" }
        return flatten(stream.reader(Charsets.UTF_8).use { Yaml().load<Map<String, Any?>>(it) })
    }

    private fun flatten(map: Map<*, *>, prefix: String = ""): Map<String, String> =
        map.entries.flatMap { (key, value) ->
            val path = prefix + key
            if (value is Map<*, *>) {
                flatten(value, "$path.").entries.map { it.toPair() }
            } else {
                listOf(path to value.toString())
            }
        }.toMap()
}
