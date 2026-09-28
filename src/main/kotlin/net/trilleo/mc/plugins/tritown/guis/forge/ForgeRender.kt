package net.trilleo.mc.plugins.tritown.guis.forge

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tritown.gear.Forge
import net.trilleo.mc.plugins.tritown.gear.ForgeCosts
import net.trilleo.mc.plugins.tritown.gear.GearText
import net.trilleo.mc.plugins.tritown.utils.*
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** The pieces the Forge's menus share: what a thing costs, the confirm button, and what happened. */
object ForgeRender {

    /** One line per item a [cost] asks for, and one for its money, each saying whether [viewer] has enough. */
    fun costLines(viewer: Player, cost: ForgeCosts.Cost): List<String> = buildList {
        cost.items.forEach { (id, need) ->
            val have = Forge.count(viewer, id)
            add(
                viewer.tr(
                    if (have >= need) "gui.forge.cost-item" else "gui.forge.cost-item-missing",
                    "name" to GearText.item(viewer, id),
                    "have" to have,
                    "need" to need,
                )
            )
        }
        if (cost.money > 0.0) {
            val enough = EconomyUtil.isAvailable && EconomyUtil.balance(viewer) >= cost.money
            val amount = if (EconomyUtil.isAvailable) EconomyUtil.format(cost.money) else cost.money.toString()
            add(
                viewer.tr(
                    if (enough) "gui.forge.cost-money" else "gui.forge.cost-money-missing",
                    "amount" to ComponentUtil.escape(amount),
                )
            )
        }
    }

    fun confirm(viewer: Player, lines: List<String>): ItemStack = itemStack(Material.LIME_CONCRETE) {
        name(viewer.tr("gui.forge.confirm"))
        meta { lore(LoreUtil.wrapLore(lines.joinToString("<newline>"))) }
    }

    /** [stack] with [lines] of [viewer]'s language added under its own tooltip. Only ever for a menu's copy. */
    fun withLines(stack: ItemStack, lines: List<String>): ItemStack = stack.clone().apply {
        val existing = getData(DataComponentTypes.LORE)?.lines().orEmpty()
        val added = lines.map { ComponentUtil.parse(it).decoration(TextDecoration.ITALIC, false) }
        setData(DataComponentTypes.LORE, ItemLore.lore(existing + added))
    }

    /**
     * Tells [player] how an action went: [doneKey] with the piece's name when
     * it went through, or why it did not.
     */
    fun announce(player: Player, result: Forge.Result, doneKey: String) {
        val message = when (result.outcome) {
            Forge.Outcome.DONE -> {
                val piece = result.piece ?: return
                player.tr(
                    doneKey,
                    "name" to GearText.name(player, piece),
                    "rarity" to GearText.rarity(player, piece.data.rarity),
                    "stars" to piece.data.stars,
                    "items" to result.returned.entries.joinToString(", ") { (id, amount) ->
                        "${amount}x ${GearText.item(player, id)}"
                    },
                )
            }

            Forge.Outcome.NOT_GEAR -> error(player, "forge.error.not-gear")
            Forge.Outcome.MAXED -> error(player, "forge.error.maxed")
            Forge.Outcome.MISSING_ITEMS -> error(player, "forge.error.missing-items")
            Forge.Outcome.CANNOT_AFFORD -> error(player, "forge.error.cannot-afford")
            Forge.Outcome.UNAVAILABLE -> error(player, "forge.error.unavailable")
        }
        player.sendPrefixed(message)
    }

    private fun error(player: Player, key: String): String = player.tr("common.error", "message" to player.tr(key))
}
