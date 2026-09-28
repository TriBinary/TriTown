package net.trilleo.mc.plugins.tritown.content

import net.trilleo.mc.plugins.tritown.mobs.Affix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BestiaryParserTest {

    private val items = setOf("grave-dust")
    private val gear = setOf("gravewalker-blade")

    @Test
    fun `a variant reads whole, with its defaults filled in`() {
        val result = parse(
            "walker" to mapOf(
                "base" to "zombie",
                "spawn" to mapOf(
                    "worlds" to listOf("Normal"),
                    "biomes" to listOf("desert"),
                    "levels" to listOf(3, 9),
                    "chance" to 5,
                ),
                "affixes" to listOf("vampiric"),
                "equipment" to mapOf("hand" to "gear:gravewalker-blade", "off-hand" to "shield"),
                "loot" to listOf(mapOf("item" to "grave-dust", "chance" to 20, "amount" to listOf(1, 2))),
            )
        )
        val def = result.value.kinds.getValue("walker")

        assertEquals(emptyList(), result.warnings)
        assertEquals("ZOMBIE", def.base)
        assertEquals(setOf("normal"), def.spawn?.worlds)
        assertEquals(setOf("minecraft:desert"), def.spawn?.biomes)
        assertEquals(3, def.spawn?.minLevel)
        assertEquals(SpawnTime.ANY, def.spawn?.time)
        assertEquals(1.0, def.health)
        assertTrue(def.rankable)
        assertEquals(setOf(Affix.VAMPIRIC), def.affixes)
        assertEquals(Costume.Gear("gravewalker-blade"), def.equipment[CostumeSlot.HAND])
        assertEquals(Costume.Vanilla("SHIELD"), def.equipment[CostumeSlot.OFF_HAND])
        assertEquals(listOf<LootEntry>(LootEntry.Item("grave-dust", 20.0, 1, 2)), def.loot)
        assertEquals(listOf(def), result.value.variantsFor("ZOMBIE"))
    }

    @Test
    fun `what names nothing is left out and said`() {
        val result = parse(
            "walker" to mapOf(
                "base" to "zombie",
                "spawn" to mapOf("chance" to 5, "time" to "dusk"),
                "affixes" to listOf("sleepy"),
                "equipment" to mapOf("hand" to "gear:no-such-blade", "tail" to "stick"),
                "loot" to listOf(mapOf("item" to "no-such-dust", "chance" to 20)),
            )
        )
        val def = result.value.kinds.getValue("walker")

        assertEquals(5, result.warnings.size, result.warnings.toString())
        assertEquals(SpawnTime.ANY, def.spawn?.time)
        assertEquals(emptySet(), def.affixes)
        assertEquals(emptyMap(), def.equipment)
        assertEquals(emptyList(), def.loot)
    }

    @Test
    fun `a variant with no base or nowhere to spawn is left out`() {
        val result = parse(
            "nobody" to mapOf("spawn" to mapOf("chance" to 5)),
            "nowhere" to mapOf("base" to "zombie"),
        )

        assertEquals(emptyMap(), result.value.kinds)
        assertEquals(2, result.warnings.size)
    }

    private fun parse(vararg variants: Pair<String, Any>) =
        ContentParser.bestiary(mapOf("variants" to linkedMapOf(*variants)), items, gear)
}
