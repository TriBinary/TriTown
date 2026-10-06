package net.trilleo.mc.plugins.tritown.guis.gathering

import com.palmergames.bukkit.towny.TownyAPI
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.gathering.*
import net.trilleo.mc.plugins.tritown.guis.menu.MainMenuGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/**
 * A player's view of their town's resource regions: where each is, what it
 * offers and how much of it is growing back, with their own tally of what they
 * have gathered above the list. Clicking a region traces its border so it is
 * easy to find.
 */
class ResourcesGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.resources.title",
    rows = 6,
    layout = PagedLayout.FRAMED,
) {

    override fun getItems(player: Player): List<ItemStack> = regions(player).map { icon(player, it) }

    override fun topButtons(player: Player): Map<Int, ItemStack> = mapOf(STATS_OFFSET to stats(player))

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(MenuRender.BACK_OFFSET to MenuRender.back(player))

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        if (offset == MenuRender.BACK_OFFSET) MenuRender.later(player) { MainMenuGUI.show(player) }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        val region = regions(player).getOrNull(contentIndex(event, page) ?: return) ?: return
        MenuRender.later(player) {
            player.closeInventory()
            GatherEditors.outline(player, region.area, OUTLINE_SECONDS)
            player.sendPrefixed(
                player.tr(
                    "gathering.menu.located",
                    "name" to region.name, "coordinates" to GatherRender.coordinates(player, region),
                )
            )
        }
    }

    private fun regions(player: Player): List<ResourceRegion> {
        val town = TownyAPI.getInstance().getResident(player)?.townOrNull ?: return emptyList()
        return GatherManager.ofTown(town.uuid).filter { it.enabled }
    }

    private fun icon(player: Player, region: ResourceRegion): ItemStack {
        val lines = buildList {
            add(GatherRender.coordinates(player, region))
            add(player.tr("gui.gather-list.offers", "categories" to GatherRender.categories(player, region)))
            region.nodes.forEach { node ->
                add(player.tr("gui.resources.node", "block" to GatherRender.blockName(node.block),
                    "time" to GatherRender.duration(player, node.regrowSeconds)))
            }
            region.spawners.forEach { spawner ->
                add(player.tr("gui.resources.spawner", "mob" to GatherRender.entityName(spawner.type),
                    "amount" to spawner.maxAlive))
            }
            add(player.tr("gui.gather-region.regrowing", "amount" to GatherManager.depletedIn(region).size))
            add("")
            add(player.tr("gui.resources.click"))
        }
        return GatherRender.card(GatherRender.icon(region), region.name, lines)
    }

    private fun stats(player: Player): ItemStack {
        val counts = GatherStats.of(player)
        val lines = GatherCategory.entries.map { category ->
            player.tr("gui.resources.stat", "category" to player.tr(category.key), "amount" to counts[category])
        }
        return GatherRender.card(Material.BOOK, player.tr("gui.resources.stats"), lines)
    }

    companion object {
        const val ID = "resources"
        private const val STATS_OFFSET = 4
        private const val OUTLINE_SECONDS = 15

        /** Whether [player]'s town has a region open to gather in, so the main menu knows to offer this. */
        fun hasRegions(player: Player): Boolean {
            if (!GatherManager.isReady) return false
            val town = TownyAPI.getInstance().getResident(player)?.townOrNull ?: return false
            return GatherManager.ofTown(town.uuid).any { it.enabled }
        }

        fun show(player: Player): Boolean = GUIManager.open(player, ID)
    }
}
