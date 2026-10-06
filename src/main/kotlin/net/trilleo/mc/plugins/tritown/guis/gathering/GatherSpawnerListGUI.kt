package net.trilleo.mc.plugins.tritown.guis.gathering

import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.gathering.*
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
 * The spawners of a region: pens of animals and arenas of monsters.
 *
 * A spawner is added where the administrator stands, by clicking a spawn egg
 * in their own inventory. The egg is only read, never used up.
 */
class GatherSpawnerListGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.gather-spawners.title",
    rows = 6,
    layout = PagedLayout.FRAMED,
) {

    override fun getItems(player: Player): List<ItemStack> =
        GatherRender.region(player)?.spawners?.map { icon(player, it) }.orEmpty()

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        BACK_OFFSET to GatherRender.button(
            player, Material.ARROW, "gui.gather-region.back-region",
            listOf(player.tr("gui.gather-region.back-region-lore")),
        ),
        ADD_OFFSET to GatherRender.button(
            player, Material.PAPER, "gui.gather-spawners.add",
            listOf(player.tr("gui.gather-spawners.add-lore")),
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
        val spawner = region.spawners.getOrNull(contentIndex(event, page) ?: return) ?: return

        if (event.click == ClickType.SHIFT_LEFT) {
            remove(player, region, spawner)
            refresh(player, event.inventory)
            return
        }

        GatherRender.click(player)
        GatherRender.navigate { GatherSpawnerGUI.show(player, region, spawner) }
    }

    private fun add(player: Player, region: ResourceRegion, item: ItemStack) {
        if (item.type.isAir) return
        val type = GatherRender.eggType(item) ?: run {
            player.sendPrefixed(player.tr("gathering.editor.not-an-egg"))
            return
        }
        val at = player.location
        if (!region.area.contains(at)) {
            player.sendPrefixed(player.tr("gathering.editor.stand-inside"))
            return
        }

        val category = GatherCategory.of(type)
        val spawner = ResourceSpawner(
            type = type,
            category = category,
            x = at.blockX + 0.5,
            y = at.blockY.toDouble(),
            z = at.blockZ + 0.5,
            maxAlive = if (category == GatherCategory.COMBAT) DEFAULT_HOSTILE else DEFAULT_ANIMALS,
            respawnSeconds = DEFAULT_RESPAWN,
            radius = DEFAULT_RADIUS,
        )
        region.spawners += spawner
        GatherManager.save()
        player.sendPrefixed(player.tr("gathering.editor.spawner-added", "mob" to GatherRender.entityName(type)))
        GatherRender.navigate { GatherSpawnerGUI.show(player, region, spawner) }
    }

    private fun icon(player: Player, spawner: ResourceSpawner): ItemStack = GatherRender.card(
        GatherRender.eggOf(spawner.type),
        player.tr("gui.gather-spawners.name", "mob" to GatherRender.entityName(spawner.type)),
        GatherSpawnerGUI.summary(player, spawner) + listOf(
            "",
            player.tr("gui.gather-spawners.click"),
            player.tr("gui.gather-spawners.shift-click"),
        ),
    )

    companion object {
        const val ID = "gather-spawners"
        private const val BACK_OFFSET = 3
        private const val ADD_OFFSET = 5
        private const val DEFAULT_ANIMALS = 4
        private const val DEFAULT_HOSTILE = 3
        private const val DEFAULT_RESPAWN = 30
        private const val DEFAULT_RADIUS = 6

        fun show(player: Player, region: ResourceRegion): Boolean {
            GatherRender.setContext(player, GatherRender.Context(region.id))
            return GUIManager.open(player, ID)
        }

        /** Takes [spawner] out of [region], and its mobs out of the world. */
        fun remove(player: Player, region: ResourceRegion, spawner: ResourceSpawner) {
            Spawners.clear(spawner)
            region.spawners.remove(spawner)
            GatherManager.save()
            player.sendPrefixed(
                player.tr("gathering.editor.spawner-removed", "mob" to GatherRender.entityName(spawner.type))
            )
        }
    }
}
