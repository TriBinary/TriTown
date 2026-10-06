package net.trilleo.mc.plugins.tritown.guis.gathering

import net.trilleo.mc.plugins.tritown.config.GatheringSettings
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.gathering.GatherCategory
import net.trilleo.mc.plugins.tritown.gathering.GatherManager
import net.trilleo.mc.plugins.tritown.gathering.ResourceNode
import net.trilleo.mc.plugins.tritown.gathering.ResourceRegion
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/**
 * The kinds of block a region grows back.
 *
 * A resource is added by clicking a block — or a seed, for a crop — in your
 * own inventory. Nothing is taken: only the kind of block is read, and every
 * block of that kind inside the region becomes a node.
 */
class GatherNodeListGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.gather-nodes.title",
    rows = 6,
    layout = PagedLayout.FRAMED,
) {

    override fun getItems(player: Player): List<ItemStack> =
        GatherRender.region(player)?.nodes?.map { icon(player, it) }.orEmpty()

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        BACK_OFFSET to GatherRender.button(
            player, Material.ARROW, "gui.gather-region.back-region",
            listOf(player.tr("gui.gather-region.back-region-lore")),
        ),
        ADD_OFFSET to GatherRender.button(
            player, Material.PAPER, "gui.gather-nodes.add",
            listOf(player.tr("gui.gather-nodes.add-lore")),
        ),
    )

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        val region = GatherRender.region(player) ?: return
        if (offset == BACK_OFFSET) {
            GatherRender.click(player)
            GatherRender.navigate { GatherRegionGUI.show(player, region) }
        }
    }

    override fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player
        if (player != null && event.clickedInventory === player.inventory) {
            event.isCancelled = true
            if (!GatherRender.mayEdit(player)) return
            val region = GatherRender.region(player) ?: return
            event.currentItem?.let { add(player, region, it) }
            return
        }
        super.onClick(event)
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        if (!GatherRender.mayEdit(player)) return
        val region = GatherRender.region(player) ?: return
        val node = region.nodes.getOrNull(contentIndex(event, page) ?: return) ?: return

        if (event.click == ClickType.SHIFT_LEFT) {
            region.nodes.remove(node)
            GatherManager.save()
            player.sendPrefixed(player.tr("gathering.editor.node-removed", "block" to GatherRender.blockName(node.block)))
            refresh(player, event.inventory)
            return
        }

        GatherRender.click(player)
        GatherRender.navigate { GatherNodeGUI.show(player, region, node) }
    }

    private fun add(player: Player, region: ResourceRegion, item: ItemStack) {
        if (item.type.isAir) return
        val block = GatherRender.blockOf(item) ?: run {
            player.sendPrefixed(player.tr("gathering.editor.not-a-block"))
            return
        }

        region.nodeFor(block)?.let { existing ->
            GatherRender.navigate { GatherNodeGUI.show(player, region, existing) }
            return
        }

        val node = ResourceNode(
            block = block,
            category = GatherCategory.of(block),
            regrowSeconds = GatheringSettings.snapshot.defaultRegrow,
        )
        region.nodes += node
        GatherManager.save()
        player.sendPrefixed(player.tr("gathering.editor.node-added", "block" to GatherRender.blockName(block)))
        GatherRender.navigate { GatherNodeGUI.show(player, region, node) }
    }

    private fun icon(player: Player, node: ResourceNode): ItemStack = GatherRender.card(
        GatherRender.blockIcon(node.block),
        player.tr("gui.gather-nodes.name", "block" to GatherRender.blockName(node.block)),
        GatherNodeGUI.summary(player, node) + listOf(
            "",
            player.tr("gui.gather-nodes.click"),
            player.tr("gui.gather-nodes.shift-click"),
        ),
    )

    companion object {
        const val ID = "gather-nodes"
        private const val BACK_OFFSET = 3
        private const val ADD_OFFSET = 5

        fun show(player: Player, region: ResourceRegion): Boolean {
            GatherRender.setContext(player, GatherRender.Context(region.id))
            return GUIManager.open(player, ID)
        }
    }
}
