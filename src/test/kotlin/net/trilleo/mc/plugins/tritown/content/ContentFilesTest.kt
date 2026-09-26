package net.trilleo.mc.plugins.tritown.content

import org.yaml.snakeyaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the bundled content files: they parse without a warning, and every
 * item, piece of gear and reforge they define is named in every language — and
 * nothing is translated that none of them uses.
 *
 * Content ids come from YAML rather than Kotlin, so `LangFilesTest`'s source
 * scan cannot see their keys; this is what holds them instead.
 */
class ContentFilesTest {

    private val languages = listOf("en_US", "zh_CN").associateWith { flatten(load("lang/$it.yml")) }
    private val items = ContentParser.items(load("content/items.yml"))
    private val gear = ContentParser.gear(load("content/gear.yml"), items.value.keys)

    /** Every key a piece of gear or a reforge is named by. */
    private val gearKeys = gear.value.gear.values.flatMap { listOf(it.nameKey, it.loreKey) }.toSet() +
            gear.value.reforges.values.map { it.key }

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

    @Test
    fun `the bundled gear parses without a warning`() {
        assertEquals(emptyList(), gear.warnings)
    }

    @Test
    fun `every piece of gear and reforge is named in every language`() {
        languages.forEach { (id, values) ->
            assertEquals(emptySet(), gearKeys - values.keys, "Gear keys missing from $id.yml")
        }
    }

    @Test
    fun `no language translates gear that does not exist`() {
        languages.forEach { (id, values) ->
            val orphans = values.keys.filter {
                it.startsWith(GearDef.KEY_PREFIX) || it.startsWith(ReforgeDef.KEY_PREFIX)
            } - gearKeys
            assertEquals(emptySet(), orphans.toSet(), "Gear keys in $id.yml that no gear uses")
        }
    }

    @Test
    fun `every piece has a recipe and a tier from 1 to 10`() {
        gear.value.gear.values.forEach { def ->
            assertTrue(def.recipe != null, "${def.id} has no recipe")
            assertTrue(def.tier in 1..10, "${def.id} is tier ${def.tier}")
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
