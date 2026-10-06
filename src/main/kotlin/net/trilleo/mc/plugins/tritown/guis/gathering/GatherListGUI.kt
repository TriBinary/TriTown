package net.trilleo.mc.plugins.tritown.guis.gathering

import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.gathering.GatherAccess
import net.trilleo.mc.plugins.tritown.gathering.GatherManager
import net.trilleo.mc.plugins.tritown.gathering.ResourceRegion
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/** Every resource region on the server, for an administrator to pick one to edit. */
class GatherListGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.gather-list.title",
    rows = 6,
    layout = PagedLayout.FRAMED,
) {

    override fun getItems(player: Player): List<ItemStack> = GatherManager.all().map { icon(player, it) }

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        HELP_OFFSET to GatherRender.button(
            player, Material.BLAZE_ROD, "gui.gather-list.help",
            listOf(player.tr("gui.gather-list.help-lore")),
        ),
    )

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        if (!GatherRender.mayEdit(player)) return
        val region = GatherManager.all().getOrNull(contentIndex(event, page) ?: return) ?: return
        GatherRender.click(player)
        GatherRender.navigate { GatherRegionGUI.show(player, region) }
    }

    private fun icon(player: Player, region: ResourceRegion): ItemStack {
        val lines = listOf(
            player.tr("gui.gather-list.id", "id" to region.id),
            player.tr("gui.gather-list.town", "town" to GatherAccess.townName(player, region)),
            GatherRender.coordinates(player, region),
            player.tr("gui.gather-list.offers", "categories" to GatherRender.categories(player, region)),
            player.tr(if (region.enabled) "gui.gather-list.open" else "gui.gather-list.closed"),
            "",
            player.tr("gui.gather-list.click"),
        )
        return GatherRender.card(GatherRender.icon(region), region.name, lines, glow = region.enabled)
    }

    companion object {
        const val ID = "gather-list"
        private const val HELP_OFFSET = 3

        fun show(player: Player): Boolean = GUIManager.open(player, ID)
    }
}
