package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.enums.MatchMode
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * The inventory side of a trade.
 *
 * Every method here runs on the server thread, because inventories may not be
 * touched from anywhere else. Removals hand back exactly what they took so a
 * trade that fails later can put it right back — a purchase must never be able
 * to leave a player short of both the goods and the price.
 */
object ShopInventory {

    /** A player's storage, without armour or the off-hand, which a trade never touches. */
    private const val STORAGE_SLOTS = 36

    /** How many of [template] the player holds, counting by [mode]. */
    fun count(player: Player, template: ItemStack, mode: MatchMode): Int =
        player.inventory.storageContents.sumOf { stack ->
            if (stack != null && matches(template, stack, mode)) stack.amount else 0
        }

    /**
     * Whether [items] would all fit in the player's storage.
     *
     * Tested against a copy rather than by counting empty slots, so partial
     * stacks, stack limits and items that do not stack are all accounted for by
     * the same code that will do the real insertion.
     */
    fun hasSpaceFor(player: Player, items: List<ItemStack>): Boolean {
        if (items.isEmpty()) return true

        val scratch = Bukkit.createInventory(null, STORAGE_SLOTS)
        scratch.storageContents = player.inventory.storageContents.map { it?.clone() }.toTypedArray()
        return scratch.addItem(*split(items).toTypedArray()).isEmpty()
    }

    /**
     * Takes [amount] items matching [template] out of the player's storage.
     *
     * @return the stacks that were removed, or `null` when the player did not
     *   have enough — in which case nothing is taken at all
     */
    fun remove(player: Player, template: ItemStack, mode: MatchMode, amount: Int): List<ItemStack>? {
        if (amount <= 0) return emptyList()
        if (count(player, template, mode) < amount) return null

        val removed = mutableListOf<ItemStack>()
        var outstanding = amount
        val contents = player.inventory.storageContents

        for (index in contents.indices) {
            if (outstanding == 0) break
            val stack = contents[index] ?: continue
            if (!matches(template, stack, mode)) continue

            val taken = minOf(outstanding, stack.amount)
            removed += stack.clone().apply { this.amount = taken }
            outstanding -= taken

            if (taken == stack.amount) {
                contents[index] = null
            } else {
                stack.amount -= taken
            }
        }

        player.inventory.storageContents = contents
        return removed
    }

    /**
     * Puts [items] into the player's storage, dropping at their feet whatever
     * will not fit.
     *
     * Space is checked before a trade starts, so a drop only happens when
     * something else filled the inventory in between. Dropping is still the
     * right answer there: the player has already paid.
     */
    fun give(player: Player, items: List<ItemStack>) {
        if (items.isEmpty()) return

        val leftover = player.inventory.addItem(*split(items).toTypedArray())
        for (stack in leftover.values) {
            player.world.dropItemNaturally(player.location, stack)
        }
    }

    /**
     * [items] broken down into stacks the game allows.
     *
     * A price multiplied by a shift-click can ask for more than one stack holds,
     * and an oversized stack is accepted in memory but cannot be stored, so it
     * is split before anything is measured against an inventory.
     */
    fun split(items: List<ItemStack>): List<ItemStack> = items.flatMap { item ->
        val perStack = item.maxStackSize.coerceAtLeast(1)
        if (item.amount <= perStack) return@flatMap listOf(item.clone())

        buildList {
            var outstanding = item.amount
            while (outstanding > 0) {
                val size = minOf(outstanding, perStack)
                add(item.clone().apply { amount = size })
                outstanding -= size
            }
        }
    }

    private fun matches(template: ItemStack, stack: ItemStack, mode: MatchMode): Boolean = when (mode) {
        MatchMode.EXACT -> template.isSimilar(stack)
        MatchMode.MATERIAL -> template.type == stack.type
    }
}
