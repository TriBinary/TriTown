package net.trilleo.mc.plugins.tritown.gathering

import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import org.bukkit.entity.Player

/**
 * How much each player has gathered, per category: a block harvested or a
 * spawner's mob killed counts one.
 *
 * Kept in the player's [net.trilleo.mc.plugins.tritown.data.PlayerData] as
 * one object of counters.
 */
object GatherStats {

    private const val KEY = "gathering"

    fun add(player: Player, category: GatherCategory) {
        val data = PlayerDataManager.get(player)
        val counts = data.getJsonObject(KEY)
        val name = category.name.lowercase()
        counts.addProperty(name, (counts.get(name)?.asLong ?: 0L) + 1L)
        data.set(KEY, counts)
    }

    fun of(player: Player): Map<GatherCategory, Long> {
        val counts = PlayerDataManager.get(player).getJsonObject(KEY)
        return GatherCategory.entries.associateWith { counts.get(it.name.lowercase())?.asLong ?: 0L }
    }
}
