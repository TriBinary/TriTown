package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.gear.ForgeCosts
import org.yaml.snakeyaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the bundled content files: they parse without a warning, and every
 * item, piece of gear, reforge and custom mob they define is named in every
 * language — and nothing is translated that none of them uses.
 *
 * Content ids come from YAML rather than Kotlin, so `LangFilesTest`'s source
 * scan cannot see their keys; this is what holds them instead.
 */
class ContentFilesTest {

    private val languages = listOf("en_US", "zh_CN").associateWith { flatten(load("lang/$it.yml")) }
    private val items = ContentParser.items(load("content/items.yml"))
    private val gear = ContentParser.gear(load("content/gear.yml"), items.value.keys)
    private val bestiary = ContentParser.bestiary(load("content/bestiary.yml"), items.value.keys, gear.value.gear.keys)

    /** Every key a custom mob is named or described by, and every boss phase's line. */
    private val kindKeys = bestiary.value.kinds.values.flatMap { def ->
        listOf(def.nameKey, def.loreKey) + def.boss?.phases.orEmpty().indices.map(def::phaseKey)
    }.toSet()

    private val variants = bestiary.value.kinds.values.filter { it.boss == null }
    private val bosses = bestiary.value.kinds.values.mapNotNull { def -> def.boss?.let { def to it } }

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
    fun `the bundled bestiary parses without a warning`() {
        assertEquals(emptyList(), bestiary.warnings)
    }

    @Test
    fun `every custom mob is named and described in every language`() {
        languages.forEach { (id, values) ->
            assertEquals(emptySet(), kindKeys - values.keys, "Custom mob keys missing from $id.yml")
        }
    }

    @Test
    fun `no language translates a custom mob that does not exist`() {
        languages.forEach { (id, values) ->
            val orphans = values.keys.filter { it.startsWith(MobKindDef.KEY_PREFIX) } - kindKeys
            assertEquals(emptySet(), orphans.toSet(), "Custom mob keys in $id.yml that no custom mob uses")
        }
    }

    @Test
    fun `every variant has somewhere to live and a chance to appear there`() {
        variants.forEach { def ->
            val spawn = def.spawn
            assertTrue(spawn != null && spawn.chance > 0.0, "${def.id} never appears")
            assertTrue(spawn.minLevel <= spawn.maxLevel, "${def.id} spawns at no level")
        }
    }

    @Test
    fun `every boss has phases, and a sigil some variant drops`() {
        assertTrue(bosses.isNotEmpty())
        bosses.forEach { (def, boss) ->
            assertTrue(boss.phases.isNotEmpty(), "${def.id} has no phases")
            val sources = variants.filter { variant -> variant.loot.any { (it as? LootEntry.Item)?.id == boss.sigil } }
            assertTrue(sources.isNotEmpty(), "No variant drops ${boss.sigil}, so ${def.id} can never be summoned")
        }
    }

    @Test
    fun `every piece has a tier from 1 to 10, and a recipe unless a custom mob drops it`() {
        val dropped =
            bestiary.value.kinds.values.flatMap { it.loot }.filterIsInstance<LootEntry.Gear>().map { it.id }.toSet()
        gear.value.gear.values.forEach { def ->
            assertTrue(def.recipe != null || def.id in dropped, "${def.id} has no recipe, and nothing drops it")
            assertTrue(def.tier in 1..10, "${def.id} is tier ${def.tier}")
        }
    }

    @Test
    fun `signature gear drops at the tier its boss's level is made for`() {
        bosses.forEach { (def, boss) ->
            val tier = (boss.level + ForgeCosts.LEVELS_PER_TIER - 1) / ForgeCosts.LEVELS_PER_TIER
            def.loot.filterIsInstance<LootEntry.Gear>().mapNotNull { gear.value.gear[it.id] }
                .filter { it.recipe == null }
                .forEach { piece -> assertEquals(tier, piece.tier, "${piece.id} drops from ${def.id}, of tier $tier") }
        }
    }

    private fun load(path: String): Map<*, *> {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "$path is not bundled" }
        return stream.reader(Charsets.UTF_8).use { Yaml().load<Map<*, *>>(it) }
    }

    private fun flatten(map: Map<*, *>, prefix: String = ""): Map<String, String> =
        map.entries.flatMap { (key, value) ->
            val path = prefix + key
            if (value is Map<*, *>) flatten(
                value,
                "$path."
            ).entries.map { it.toPair() } else listOf(path to value.toString())
        }.toMap()
}
