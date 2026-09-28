package net.trilleo.mc.plugins.tritown.mobs

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.trilleo.mc.plugins.tritown.content.ContentItems
import net.trilleo.mc.plugins.tritown.content.LootEntry
import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import net.trilleo.mc.plugins.tritown.gear.Gear
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * What each player has found of the custom mobs: how many of each they have
 * slain, and which drops each has given them. Kept in their player data under
 * `bestiary`, by kind id, and read by the bestiary menu.
 *
 * A drop is remembered by its id — an item's, or `gear:` and a piece's — so it
 * can be matched against a kind's loot lines ([idOf]).
 */
object BestiaryRecords {

    private const val ROOT = "bestiary"
    private const val KILLS = "kills"
    private const val DROPS = "drops"
    private const val GEAR_PREFIX = "gear:"

    class Record(val kills: Int, val drops: Set<String>)

    fun of(player: Player, kind: String): Record {
        val entry = root(player).getAsJsonObject(kind) ?: return Record(0, emptySet())
        val kills = entry.get(KILLS)?.asInt ?: 0
        val drops =
            entry.getAsJsonArray(DROPS)?.mapNotNull { runCatching { it.asString }.getOrNull() }?.toSet().orEmpty()
        return Record(kills, drops)
    }

    /** Counts one more of [kind] slain by [player], and remembers anything new among the [drops] it gave them. */
    fun credit(player: Player, kind: String, drops: List<ItemStack>) {
        val root = root(player)
        val entry = root.getAsJsonObject(kind) ?: JsonObject().also { root.add(kind, it) }
        entry.addProperty(KILLS, (entry.get(KILLS)?.asInt ?: 0) + 1)

        val known = entry.getAsJsonArray(DROPS) ?: JsonArray().also { entry.add(DROPS, it) }
        val seen = known.mapNotNull { runCatching { it.asString }.getOrNull() }.toMutableSet()
        drops.mapNotNull(::idOf).filter(seen::add).forEach(known::add)
        PlayerDataManager.get(player).set(ROOT, root)
    }

    /** The id a drop is remembered by, or `null` if it is neither a content item nor gear. */
    fun idOf(stack: ItemStack): String? =
        Gear.read(stack)?.let { GEAR_PREFIX + it.def.id } ?: ContentItems.idOf(stack)

    /** The id a loot line's drop is remembered by. */
    fun idOf(entry: LootEntry): String = when (entry) {
        is LootEntry.Item -> entry.id
        is LootEntry.Gear -> GEAR_PREFIX + entry.id
    }

    /** The piece of gear [id] names, if it names one rather than an item. */
    fun gearId(id: String): String? = id.takeIf { it.startsWith(GEAR_PREFIX) }?.removePrefix(GEAR_PREFIX)

    private fun root(player: Player): JsonObject = PlayerDataManager.get(player).getJsonObject(ROOT)
}
