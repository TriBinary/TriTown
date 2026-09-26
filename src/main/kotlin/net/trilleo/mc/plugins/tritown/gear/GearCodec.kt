package net.trilleo.mc.plugins.tritown.gear

import net.trilleo.mc.plugins.tritown.combat.Stat
import net.trilleo.mc.plugins.tritown.content.BalanceParser
import net.trilleo.mc.plugins.tritown.content.Rarity
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

/**
 * Reads and writes a piece's [GearData], kept in the item's own persistent
 * data under `tritown:gear`. Whatever holds the item — an inventory, a storage
 * page, a trade, a shop's copy — carries it along untouched.
 */
object GearCodec {

    private val GEAR = NamespacedKey("tritown", "gear")
    private val ID = NamespacedKey("tritown", "id")
    private val RARITY = NamespacedKey("tritown", "rarity")
    private val STARS = NamespacedKey("tritown", "stars")
    private val ROLLS = NamespacedKey("tritown", "rolls")
    private val REFORGE = NamespacedKey("tritown", "reforge")
    private val REVISION = NamespacedKey("tritown", "revision")

    /** What [stack] is, or `null` if it is not a piece of gear. Read without cloning its meta. */
    fun read(stack: ItemStack?): GearData? {
        if (stack == null || stack.isEmpty) return null
        val data = stack.persistentDataContainer.get(GEAR, PersistentDataType.TAG_CONTAINER) ?: return null
        val id = data.get(ID, PersistentDataType.STRING) ?: return null
        return GearData(
            id = id,
            rarity = data.get(RARITY, PersistentDataType.STRING)?.let(Rarity::of) ?: Rarity.COMMON,
            stars = data.get(STARS, PersistentDataType.INTEGER) ?: 0,
            rolls = rolls(data),
            reforge = data.get(REFORGE, PersistentDataType.STRING),
            revision = data.get(REVISION, PersistentDataType.INTEGER) ?: 0,
        )
    }

    fun write(stack: ItemStack, gear: GearData) {
        stack.editPersistentDataContainer { container ->
            val data = container.adapterContext.newPersistentDataContainer()
            data.set(ID, PersistentDataType.STRING, gear.id)
            data.set(RARITY, PersistentDataType.STRING, gear.rarity.name)
            data.set(STARS, PersistentDataType.INTEGER, gear.stars)
            data.set(
                ROLLS,
                PersistentDataType.LIST.strings(),
                gear.rolls.map { (stat, roll) -> "${BalanceParser.name(stat)}=$roll" },
            )
            gear.reforge?.let { data.set(REFORGE, PersistentDataType.STRING, it) }
            data.set(REVISION, PersistentDataType.INTEGER, gear.revision)
            container.set(GEAR, PersistentDataType.TAG_CONTAINER, data)
        }
    }

    private fun rolls(data: PersistentDataContainer): Map<Stat, Int> =
        data.get(ROLLS, PersistentDataType.LIST.strings()).orEmpty().mapNotNull { entry ->
            val name = entry.substringBefore('=')
            val stat = Stat.entries.firstOrNull { BalanceParser.name(it) == name } ?: return@mapNotNull null
            val roll = entry.substringAfter('=').toIntOrNull() ?: return@mapNotNull null
            stat to roll
        }.toMap()
}
