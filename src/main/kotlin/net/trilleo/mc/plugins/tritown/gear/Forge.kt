package net.trilleo.mc.plugins.tritown.gear

import net.trilleo.mc.plugins.tritown.content.ContentItems
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.GearDef
import net.trilleo.mc.plugins.tritown.economy.EconomyContext
import net.trilleo.mc.plugins.tritown.economy.TransactionReason
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

/**
 * Everything the Forge does, and the only place gear is made or changed.
 *
 * Every action keeps the same order, the one `ShopTrade` keeps: **everything
 * that can refuse is asked before anything is taken**. The items are counted,
 * then the money is charged, and only once it has gone are the items taken and
 * the piece made or changed — the items cannot vanish in between, since it all
 * happens in one tick. A piece being changed is the one in the player's main
 * hand, read afresh at the moment of the change.
 *
 * Money moves through [EconomyUtil] as `gear`, so the admin panel files it
 * under the Forge.
 */
object Forge {

    enum class Outcome { DONE, NOT_GEAR, MAXED, MISSING_ITEMS, CANNOT_AFFORD, UNAVAILABLE }

    /** What a finished action produced, for the message: the piece as it is now. */
    class Result(val outcome: Outcome, val piece: Gear.Piece? = null, val returned: Map<String, Int> = emptyMap())

    fun craft(player: Player, def: GearDef): Result {
        val recipe = def.recipe ?: return Result(Outcome.UNAVAILABLE)
        val cost = ForgeCosts.Cost(recipe.items, recipe.money)
        val reason = TransactionReason.of(TransactionReason.GEAR_CRAFT, "item" to def.id)
        refusal(player, cost)?.let { return Result(it) }
        if (!charge(player, cost, reason)) return Result(Outcome.CANNOT_AFFORD)

        take(player, cost.items)
        val rarity = GearStats.pick(ContentRegistry.balance.gear.craftOdds, Random)
        val stack = Gear.create(def, rarity) ?: return Result(Outcome.UNAVAILABLE)
        InventoryUtil.give(player, listOf(stack))
        return Result(Outcome.DONE, Gear.read(stack))
    }

    fun upgrade(player: Player): Result = change(player, TransactionReason.GEAR_UPGRADE,
        cost = { piece ->
            ForgeCosts.upgrade(piece.def, piece.data, balance.gear, balance.forge, ContentRegistry.loot)
        },
        apply = { piece -> piece.data.copy(stars = piece.data.stars + 1) },
    )

    fun refine(player: Player): Result = change(player, TransactionReason.GEAR_REFINE,
        cost = { piece -> ForgeCosts.refine(piece.def, piece.data, balance.forge, ContentRegistry.loot) },
        apply = { piece -> piece.data.copy(rarity = ForgeCosts.nextRarity(piece.data.rarity)) },
    )

    /** A new reforge, never the one the piece already has when another would fit. */
    fun reforge(player: Player): Result = change(player, TransactionReason.GEAR_REFORGE,
        cost = { piece ->
            ForgeCosts.reforge(piece.def, balance.forge, ContentRegistry.loot)
                .takeIf { ContentRegistry.gear.reforgesFor(piece.def.slot).isNotEmpty() }
        },
        apply = { piece ->
            val choices = ContentRegistry.gear.reforgesFor(piece.def.slot)
            val fresh = choices.filter { it.id != piece.data.reforge }.ifEmpty { choices }
            piece.data.copy(reforge = fresh.random().id)
        },
    )

    /** Breaks the held piece down into essence. Nothing is charged, so there is nothing to refuse but the piece. */
    fun salvage(player: Player): Result {
        val held = player.inventory.itemInMainHand
        val piece = Gear.read(held) ?: return Result(Outcome.NOT_GEAR)
        val returned = ForgeCosts.salvage(piece.def, piece.data, balance.forge, ContentRegistry.loot)

        player.inventory.setItemInMainHand(null)
        InventoryUtil.give(player, returned.mapNotNull { (id, amount) -> ContentItems.create(id, amount) })
        return Result(Outcome.DONE, piece, returned)
    }

    /** How many of the content item [id] the player carries. */
    fun count(player: Player, id: String): Int =
        player.inventory.storageContents.filter { ContentItems.idOf(it) == id }.sumOf { it!!.amount }

    /**
     * Changes the piece in the player's main hand: [cost] says what it costs,
     * or `null` when it cannot go further; [apply] says what it becomes.
     */
    private fun change(
        player: Player,
        reason: String,
        cost: (Gear.Piece) -> ForgeCosts.Cost?,
        apply: (Gear.Piece) -> GearData,
    ): Result {
        val held = player.inventory.itemInMainHand
        val piece = Gear.read(held) ?: return Result(Outcome.NOT_GEAR)
        val price = cost(piece) ?: return Result(Outcome.MAXED)
        refusal(player, price)?.let { return Result(it) }
        if (!charge(player, price, TransactionReason.of(reason, "item" to piece.def.id))) {
            return Result(Outcome.CANNOT_AFFORD)
        }

        take(player, price.items)
        val changed = apply(piece)
        Gear.save(held, changed)
        player.inventory.setItemInMainHand(held)
        return Result(Outcome.DONE, Gear.read(held))
    }

    /** Why the player cannot pay [cost], or `null` if they can. */
    private fun refusal(player: Player, cost: ForgeCosts.Cost): Outcome? {
        if (cost.items.any { (id, amount) -> count(player, id) < amount }) return Outcome.MISSING_ITEMS
        if (cost.money > 0.0 && !EconomyUtil.isAvailable) return Outcome.UNAVAILABLE
        return null
    }

    private fun charge(player: Player, cost: ForgeCosts.Cost, reason: String): Boolean =
        cost.money <= 0.0 || EconomyUtil.withdraw(player, cost.money, EconomyContext.SOURCE_GEAR, reason)

    /** Takes [items] out of the player's inventory, which [refusal] has already checked holds them. */
    private fun take(player: Player, items: Map<String, Int>) {
        val inventory = player.inventory
        for ((id, wanted) in items) {
            var left = wanted
            for (slot in 0 until inventory.storageContents.size) {
                if (left <= 0) break
                val stack: ItemStack = inventory.getItem(slot) ?: continue
                if (ContentItems.idOf(stack) != id) continue
                val used = minOf(stack.amount, left)
                stack.amount -= used
                inventory.setItem(slot, stack.takeIf { it.amount > 0 })
                left -= used
            }
        }
    }

    private val balance get() = ContentRegistry.balance
}
