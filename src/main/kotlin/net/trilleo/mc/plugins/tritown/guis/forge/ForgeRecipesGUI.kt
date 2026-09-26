package net.trilleo.mc.plugins.tritown.guis.forge

import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.GearDef
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.gear.Forge
import net.trilleo.mc.plugins.tritown.gear.ForgeCosts
import net.trilleo.mc.plugins.tritown.gear.Gear
import net.trilleo.mc.plugins.tritown.gear.GearText
import net.trilleo.mc.plugins.tritown.guis.ConfirmGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Every piece the Forge can craft, lowest tier first, each shown as it would
 * come out at its most common with what it takes beneath it. Clicking one asks
 * first, then crafts it through [Forge.craft], which rolls its rarity.
 */
class ForgeRecipesGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.forge-recipes.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.CENTERED,
) {

    /** What each viewer's list holds, in the order it shows them. */
    private val recipes = ConcurrentHashMap<UUID, List<GearDef>>()

    override fun getItems(player: Player): List<ItemStack> =
        recipes.getOrPut(player.uniqueId) { craftable() }.mapNotNull { def -> render(player, def) }

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(MenuRender.BACK_OFFSET to MenuRender.back(player))

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        if (offset == MenuRender.BACK_OFFSET) MenuRender.later(player) { ForgeGUI.show(player) }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val index = contentIndex(event, page) ?: return
        val def = recipes[player.uniqueId]?.getOrNull(index) ?: return
        val preview = Gear.preview(def) ?: return

        MenuRender.later(player) {
            ConfirmGUI.show(
                player,
                title = player.tr("gui.forge-recipes.confirm"),
                subject = preview,
                choices = listOf(ConfirmGUI.Choice(ForgeRender.confirm(player, costLines(player, def))) { viewer ->
                    ForgeRender.announce(viewer, Forge.craft(viewer, def), "forge.crafted")
                    show(viewer)
                }),
                onCancel = ::show,
            )
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        recipes.remove(event.player.uniqueId)
    }

    private fun render(viewer: Player, def: GearDef): ItemStack? {
        val preview = Gear.preview(def) ?: return null
        val lines = listOf("", viewer.tr("gui.forge-recipes.tier", "tier" to def.tier)) +
                costLines(viewer, def) + "" + viewer.tr("gui.forge-recipes.click")
        return ForgeRender.withLines(preview, lines)
    }

    private fun costLines(viewer: Player, def: GearDef): List<String> {
        val recipe = def.recipe ?: return emptyList()
        return ForgeRender.costLines(viewer, ForgeCosts.Cost(recipe.items, recipe.money))
    }

    /** Every piece with a recipe, by tier and then by slot, so a tier's set reads together. */
    private fun craftable(): List<GearDef> =
        ContentRegistry.gear.gear.values
            .filter { it.recipe != null }
            .sortedWith(compareBy<GearDef>({ it.tier }, { it.slot.ordinal }, { it.id }))

    companion object {
        const val ID = "forge-recipes"

        fun show(player: Player): Boolean {
            (GUIManager.getGUI(ID) as? ForgeRecipesGUI)?.recipes?.remove(player.uniqueId)
            return GUIManager.open(player, ID)
        }
    }
}
