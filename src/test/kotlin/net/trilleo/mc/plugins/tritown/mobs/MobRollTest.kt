package net.trilleo.mc.plugins.tritown.mobs

import net.trilleo.mc.plugins.tritown.content.Balance
import net.trilleo.mc.plugins.tritown.content.BestiaryCatalog
import net.trilleo.mc.plugins.tritown.content.MobKindDef
import net.trilleo.mc.plugins.tritown.content.SpawnPlace
import net.trilleo.mc.plugins.tritown.content.SpawnRule
import net.trilleo.mc.plugins.tritown.content.SpawnTime
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobRollTest {

    private val balance = Balance.DEFAULT
    private val rolls = 200_000

    @Test
    fun `nothing ranks below the elite level`() {
        val random = Random(1)
        repeat(10_000) {
            assertEquals(MobRank.NORMAL, MobRoll.rank(balance.ranks.elite.minLevel - 1, balance, random))
        }
    }

    @Test
    fun `elites appear as often as tuned, and champions not before their level`() {
        val random = Random(2)
        val ranks = List(rolls) { MobRoll.rank(balance.ranks.champion.minLevel - 1, balance, random) }

        assertEquals(0, ranks.count { it == MobRank.CHAMPION })
        assertClose(balance.ranks.elite.chance, ranks.count { it == MobRank.ELITE })
    }

    @Test
    fun `champions are rolled first, and elites from what is left`() {
        val random = Random(3)
        val ranks = List(rolls) { MobRoll.rank(balance.ranks.champion.minLevel, balance, random) }

        assertClose(balance.ranks.champion.chance, ranks.count { it == MobRank.CHAMPION })
        val eliteShare = balance.ranks.elite.chance * (1.0 - balance.ranks.champion.chance / 100.0)
        assertClose(eliteShare, ranks.count { it == MobRank.ELITE })
    }

    @Test
    fun `a rank's affixes are distinct and as many as it allows`() {
        val random = Random(4)
        listOf(MobRank.ELITE, MobRank.CHAMPION).forEach { rank ->
            val tuning = rank.tuning(balance)!!
            repeat(1_000) {
                val affixes = MobRoll.affixes(rank, balance, random)
                assertEquals(affixes.size, affixes.toSet().size, "$rank rolled a duplicate: $affixes")
                assertTrue(affixes.size in tuning.minAffixes..tuning.maxAffixes, "$rank rolled ${affixes.size}")
            }
        }
    }

    @Test
    fun `a normal mob has no affixes`() {
        assertEquals(emptyList(), MobRoll.affixes(MobRank.NORMAL, balance, Random(5)))
    }

    @Test
    fun `a custom mob only takes spawns of its own kind, where it lives and at its levels`() {
        val catalog = BestiaryCatalog(mapOf("walker" to kind("walker", "ZOMBIE", chance = 100.0)))
        val random = Random(6)

        assertEquals("walker", MobRoll.kind(catalog, "ZOMBIE", 5, overworld, random)?.id)
        assertEquals(null, MobRoll.kind(catalog, "SKELETON", 5, overworld, random))
        assertEquals(null, MobRoll.kind(catalog, "ZOMBIE", 2, overworld, random))
        assertEquals(null, MobRoll.kind(catalog, "ZOMBIE", 11, overworld, random))
        assertEquals(null, MobRoll.kind(catalog, "ZOMBIE", 5, overworld.copy(environment = "nether"), random))
        assertEquals(null, MobRoll.kind(catalog, "ZOMBIE", 5, overworld.copy(biome = "minecraft:desert"), random))
        assertEquals(null, MobRoll.kind(catalog, "ZOMBIE", 5, overworld.copy(y = -20), random))
        assertEquals(null, MobRoll.kind(catalog, "ZOMBIE", 5, overworld.copy(night = false), random))
    }

    @Test
    fun `a world named in a spawn rule counts as well as its kind of world`() {
        val catalog = BestiaryCatalog(mapOf("walker" to kind("walker", "ZOMBIE", chance = 100.0, worlds = setOf("mining"))))

        assertEquals("walker", MobRoll.kind(catalog, "ZOMBIE", 5, overworld.copy(world = "mining"), Random(7))?.id)
        assertEquals(null, MobRoll.kind(catalog, "ZOMBIE", 5, overworld, Random(7)))
    }

    @Test
    fun `a custom mob appears as often as tuned`() {
        val catalog = BestiaryCatalog(mapOf("walker" to kind("walker", "ZOMBIE", chance = 6.0)))
        val random = Random(8)
        val count = (1..rolls).count { MobRoll.kind(catalog, "ZOMBIE", 5, overworld, random) != null }

        assertClose(6.0, count)
    }

    @Test
    fun `variants of a kind roll in order, and the second only gets what the first leaves`() {
        val catalog = BestiaryCatalog(
            linkedMapOf(
                "first" to kind("first", "ZOMBIE", chance = 10.0),
                "second" to kind("second", "ZOMBIE", chance = 10.0),
            )
        )
        val random = Random(9)
        val kinds = List(rolls) { MobRoll.kind(catalog, "ZOMBIE", 5, overworld, random)?.id }

        assertClose(10.0, kinds.count { it == "first" })
        assertClose(9.0, kinds.count { it == "second" })
    }

    private val overworld = SpawnPlace("normal", "world", "minecraft:plains", y = 64, night = true)

    private fun kind(id: String, base: String, chance: Double, worlds: Set<String> = setOf("normal")) = MobKindDef(
        id = id,
        base = base,
        spawn = SpawnRule(
            worlds = worlds,
            biomes = setOf("minecraft:plains"),
            minLevel = 3,
            maxLevel = 10,
            chance = chance,
            time = SpawnTime.NIGHT,
            minY = 0,
            maxY = null,
        ),
        rankable = true,
        health = 1.0,
        damage = 1.0,
        defense = 0.0,
        speed = 0.0,
        scale = 1.0,
        knockback = 0.0,
        affixes = emptySet(),
        equipment = emptyMap(),
        loot = emptyList(),
    )

    /** [count] out of [rolls] is [percent] percent, within a tenth of it. */
    private fun assertClose(percent: Double, count: Int) {
        val actual = count * 100.0 / rolls
        assertTrue(abs(actual - percent) <= percent * 0.1, "expected about $percent%, rolled $actual%")
    }
}
