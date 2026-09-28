package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.combat.BalanceSimulator.Foe
import net.trilleo.mc.plugins.tritown.content.Balance
import net.trilleo.mc.plugins.tritown.content.ContentParser
import net.trilleo.mc.plugins.tritown.gear.ForgeCosts
import org.yaml.snakeyaml.Yaml
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Holds the bundled bosses to what a boss fight is meant to be: something a
 * group takes on in gear of the boss's tier, which one player could win alone
 * only slowly, and whose every hit is felt.
 */
class BossBalanceTest {

    /** The vanilla health and melee hit of each kind a bundled boss is. A costume never adds to the hit. */
    private val foes = mapOf(
        "ZOMBIE" to Foe(health = 20.0, hit = 3.0),
        "SPIDER" to Foe(health = 16.0, hit = 2.0),
        "DROWNED" to Foe(health = 20.0, hit = 3.0),
        "WITHER_SKELETON" to Foe(health = 20.0, hit = 2.0),
        "ENDERMAN" to Foe(health = 40.0, hit = 7.0),
    )

    private val balance = Balance.DEFAULT
    private val bosses = run {
        val items = ContentParser.items(load("content/items.yml")).value.keys
        val gear = ContentParser.gear(load("content/gear.yml"), items).value.gear.keys
        ContentParser.bestiary(load("content/bestiary.yml"), items, gear).value.kinds.values.filter { it.boss != null }
    }

    @Test
    fun `one player in gear of a boss's tier needs 60 to 200 swings to bring it down`() {
        bosses.forEach { def ->
            val row = row(def.id)
            assertTrue(row.hitsToKill in 60..200, "${def.id} takes ${row.hitsToKill} swings alone")
        }
    }

    @Test
    fun `each of a boss's hits takes 10 to 30 percent of the health of a player in gear of its tier`() {
        bosses.forEach { def ->
            val share = row(def.id).shareTaken * 100.0
            assertTrue(share in 10.0..30.0, "${def.id} takes $share% a hit")
        }
    }

    private fun row(id: String): BalanceSimulator.Row {
        val def = bosses.first { it.id == id }
        val boss = def.boss!!
        val foe = foes[def.base] ?: error("${def.base} has no vanilla numbers here; add them")
        val tier = (boss.level + ForgeCosts.LEVELS_PER_TIER - 1) / ForgeCosts.LEVELS_PER_TIER
        return BalanceSimulator.bossRow(balance, boss.level, tier, foe, def.health, def.damage, def.defense)
    }

    private fun load(path: String): Map<*, *> {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "$path is not bundled" }
        return stream.reader(Charsets.UTF_8).use { Yaml().load<Map<*, *>>(it) }
    }
}
