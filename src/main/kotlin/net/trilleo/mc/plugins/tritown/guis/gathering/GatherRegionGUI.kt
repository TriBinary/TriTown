package net.trilleo.mc.plugins.tritown.guis.gathering

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.gathering.*
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * One resource region: what it offers, and everything about it an
 * administrator can change — its resources, its spawners, its name and its
 * bounds, and whether it is open.
 */
class GatherRegionGUI : PluginGUI(
    id = ID,
    titleKey = "gui.gather-region.title",
    rows = 6,
) {

    private enum class Button { RESOURCES, SPAWNERS, RENAME, TOGGLE, OUTLINE, REGROW, RESIZE, BACK }

    private val layout: Map<Int, Button> = buildMap {
        val columns = GUIFrame.spacedColumns(4)
        listOf(Button.RESOURCES, Button.SPAWNERS, Button.RENAME, Button.TOGGLE)
            .zip(columns).forEach { (button, column) -> put(FIRST_ROW * ROW_SIZE + column, button) }
        listOf(Button.OUTLINE, Button.REGROW, Button.RESIZE)
            .zip(GUIFrame.spacedColumns(3)).forEach { (button, column) -> put(SECOND_ROW * ROW_SIZE + column, button) }
        put(BACK_SLOT, Button.BACK)
    }

    override fun title(player: Player): Component {
        val name = GatherRender.region(player)?.name ?: ""
        return ComponentUtil.parse(player.tr("gui.gather-region.title", "name" to name))
    }

    override fun setup(player: Player, inventory: Inventory) {
        val region = GatherRender.region(player) ?: return
        GUIFrame.draw(inventory, layout.keys + INFO_SLOT)
        inventory.setItem(INFO_SLOT, info(player, region))
        layout.forEach { (slot, button) -> inventory.setItem(slot, render(player, region, button)) }
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== event.view.topInventory) return
        val player = event.whoClicked as? Player ?: return
        if (!GatherRender.mayEdit(player)) return
        val region = GatherRender.region(player) ?: return

        when (layout[event.rawSlot]) {
            Button.RESOURCES -> open(player) { GatherNodeListGUI.show(player, region) }
            Button.SPAWNERS -> open(player) { GatherSpawnerListGUI.show(player, region) }
            Button.RENAME -> rename(player, region)
            Button.TOGGLE -> {
                region.enabled = !region.enabled
                GatherManager.save()
                GatherRender.click(player)
                GUIManager.refresh(player)
            }

            Button.OUTLINE -> {
                GatherRender.navigate { player.closeInventory() }
                GatherEditors.outline(player, region.area, OUTLINE_SECONDS)
                player.sendPrefixed(player.tr("gathering.editor.outlined", "seconds" to OUTLINE_SECONDS))
            }

            Button.REGROW -> {
                val count = Regrowth.regrowAll(region)
                player.sendPrefixed(player.tr("gathering.editor.regrown", "amount" to count))
                GatherRender.click(player)
                GUIManager.refresh(player)
            }

            Button.RESIZE -> resize(player, region)
            Button.BACK -> open(player) { GatherListGUI.show(player) }
            null -> Unit
        }
    }

    private fun open(player: Player, action: () -> Unit) {
        GatherRender.click(player)
        GatherRender.navigate(action)
    }

    private fun rename(player: Player, region: ResourceRegion) {
        GatherRender.navigate {
            player.closeInventory()
            ChatPrompt.ask(player, player.tr("gathering.editor.prompt-name")) { input ->
                region.name = input
                GatherManager.save()
                show(player, region)
            }
        }
    }

    private fun resize(player: Player, region: ResourceRegion) {
        val area = GatherEditors.selection(player).area()
        when (val result = AreaCheck.check(area, except = region.id)) {
            is AreaCheck.Result.Ok -> {
                if (result.townId != region.townId) {
                    player.sendPrefixed(player.tr("gathering.area.other-town"))
                    return
                }
                Regrowth.regrowAll(region)
                region.area = area!!
                GatherManager.save()
                player.sendPrefixed(player.tr("gathering.editor.resized", "volume" to area.volume))
                GUIManager.refresh(player)
            }

            else -> player.sendPrefixed(AreaCheck.message(player, result))
        }
    }

    private fun info(player: Player, region: ResourceRegion): ItemStack {
        val area = region.area
        val lines = listOf(
            player.tr("gui.gather-list.id", "id" to region.id),
            player.tr("gui.gather-list.town", "town" to GatherAccess.townName(player, region)),
            GatherRender.coordinates(player, region),
            player.tr(
                "gui.gather-region.size",
                "x" to area.maxX - area.minX + 1, "y" to area.maxY - area.minY + 1, "z" to area.maxZ - area.minZ + 1,
            ),
            player.tr("gui.gather-list.offers", "categories" to GatherRender.categories(player, region)),
            player.tr("gui.gather-region.regrowing", "amount" to GatherManager.depletedIn(region).size),
        )
        return GatherRender.card(GatherRender.icon(region), region.name, lines)
    }

    private fun render(player: Player, region: ResourceRegion, button: Button): ItemStack = when (button) {
        Button.RESOURCES -> GatherRender.button(
            player, Material.IRON_PICKAXE, "gui.gather-region.resources",
            listOf(player.tr("gui.gather-region.resources-lore", "amount" to region.nodes.size)),
        )

        Button.SPAWNERS -> GatherRender.button(
            player, Material.SPAWNER, "gui.gather-region.spawners",
            listOf(player.tr("gui.gather-region.spawners-lore", "amount" to region.spawners.size)),
        )

        Button.RENAME -> GatherRender.button(
            player, Material.NAME_TAG, "gui.gather-region.rename",
            listOf(player.tr("gui.gather-region.rename-lore")),
        )

        Button.TOGGLE -> GatherRender.button(
            player, if (region.enabled) Material.LIME_DYE else Material.GRAY_DYE,
            if (region.enabled) "gui.gather-region.enabled" else "gui.gather-region.disabled",
            listOf(player.tr("gui.gather-region.toggle-lore")),
        )

        Button.OUTLINE -> GatherRender.button(
            player, Material.SPYGLASS, "gui.gather-region.outline",
            listOf(player.tr("gui.gather-region.outline-lore")),
        )

        Button.REGROW -> GatherRender.button(
            player, Material.BONE_MEAL, "gui.gather-region.regrow",
            listOf(player.tr("gui.gather-region.regrow-lore")),
        )

        Button.RESIZE -> GatherRender.button(
            player, Material.BLAZE_ROD, "gui.gather-region.resize",
            listOf(player.tr("gui.gather-region.resize-lore")),
        )

        Button.BACK -> GatherRender.button(
            player, Material.ARROW, "gui.gather-region.back",
            listOf(player.tr("gui.gather-region.back-lore")),
        )
    }

    companion object {
        const val ID = "gather-region"

        private const val ROW_SIZE = 9
        private const val INFO_SLOT = 13
        private const val FIRST_ROW = 3
        private const val SECOND_ROW = 4
        private const val BACK_SLOT = 49
        private const val OUTLINE_SECONDS = 15

        fun show(player: Player, region: ResourceRegion): Boolean {
            GatherRender.setContext(player, GatherRender.Context(region.id))
            return GUIManager.open(player, ID)
        }
    }
}
