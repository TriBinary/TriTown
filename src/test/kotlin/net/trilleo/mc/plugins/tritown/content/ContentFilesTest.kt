package net.trilleo.mc.plugins.tritown.content

import org.yaml.snakeyaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the bundled content files: they parse without a warning, and every
 * item they define is named and described in every language — and nothing is
 * translated that no item uses.
 *
 * Content ids come from YAML rather than Kotlin, so `LangFilesTest`'s source
 * scan cannot see their keys; this is what holds them instead.
 */
class ContentFilesTest {

    private val languages = listOf("en_US", "zh_CN").associateWith { flatten(load("lang/$it.yml")) }
    private val items = ContentParser.items(load("content/items.yml"))

    @Test
    fun `the bundled items parse without a warning`() {
        assertEquals(emptyList(), items.warnings)
    }

    @Test
    fun `the bundled loot parses without a warning`() {
        assertEquals(emptyList(), ContentParser.loot(load("content/mobs.yml"), items.value.keys).warnings)
    }

    @Test
    fun `every item is named and described in every language`() {
        val wanted = items.value.values.flatMap { listOf(it.nameKey, it.loreKey) }.toSet()
        languages.forEach { (id, values) ->
            assertEquals(emptySet(), wanted - values.keys, "Item keys missing from $id.yml")
        }
    }

    @Test
    fun `no language translates an item that does not exist`() {
        val wanted = items.value.values.flatMap { listOf(it.nameKey, it.loreKey) }.toSet()
        languages.forEach { (id, values) ->
            val orphans = values.keys.filter { it.startsWith(ContentItemDef.KEY_PREFIX) } - wanted
            assertEquals(emptySet(), orphans.toSet(), "Item keys in $id.yml that no item uses")
        }
    }

    private fun load(path: String): Map<*, *> {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "$path is not bundled" }
        return stream.reader(Charsets.UTF_8).use { Yaml().load<Map<*, *>>(it) }
    }

    private fun flatten(map: Map<*, *>, prefix: String = ""): Map<String, String> =
        map.entries.flatMap { (key, value) ->
            val path = prefix + key
            if (value is Map<*, *>) flatten(value, "$path.").entries.map { it.toPair() } else listOf(path to value.toString())
        }.toMap()
}
