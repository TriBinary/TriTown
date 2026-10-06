package net.trilleo.mc.plugins.tritown.guis.gathering

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.config.GatheringSettings
import net.trilleo.mc.plugins.tritown.gathering.GatherManager
import net.trilleo.mc.plugins.tritown.gathering.ResourceNode
import net.trilleo.mc.plugins.tritown.gathering.ResourceRegion
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * One resource of a region: what it counts as, how long it takes to grow back,
 * what stands in for it meanwhile, and what it drops.
 */
class GatherNodeGUI : PluginGUI(
    id = ID,
    titleKey = "gui.gather-node.title",
    rows = 6,
) {

    private enum class Button { CATEGORY, REGROW, DEPLETED, VANILLA, DROPS, POOL, DELETE, BACK }

    private val layout: Map<Int, Button> = buildMap {
        listOf(Button.CATEGORY, Button.REGROW, Button.DEPLETED, Button.VANILLA)
            .zip(GUIFrame.spacedColumns(4)).forEach { (button, column) -> put(3 * ROW_SIZE + column, button) }
        listOf(Button.DROPS, Button.POOL, Button.DELETE)
            .zip(GUIFrame.spacedColumns(3)).forEach { (button, column) -> put(4 * ROW_SIZE + column, button) }
        put(BACK_SLOT, Button.BACK)
    }

    override fun title(player: Player): Component {
        val block = GatherRender.node(player)?.block?.let(GatherRender::blockName) ?: ""
        return ComponentUtil.parse(player.tr("gui.gather-node.title", "block" to block))
    }

    override fun setup(player: Player, inventory: Inventory) {
        val node = GatherRender.node(player) ?: return
        GUIFrame.draw(inventory, layout.keys + INFO_SLOT)
        inventory.setItem(
            INFO_SLOT,
            GatherRender.card(
                GatherRender.blockIcon(node.block),
                player.tr("gui.gather-nodes.name", "block" to GatherRender.blockName(node.block)),
                summary(player, node),
            ),
        )
        layout.forEach { (slot, button) -> inventory.setItem(slot, render(player, node, button)) }
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== event.view.topInventory) return
        val player = event.whoClicked as? Player ?: return
        if (!GatherRender.mayEdit(player)) return
        val region = GatherRender.region(player) ?: return
        val node = GatherRender.node(player) ?: return

        when (layout[event.rawSlot]) {
            Button.CATEGORY -> change(player) { node.category = node.category.next() }
            Button.VANILLA -> change(player) { node.vanillaDrops = !node.vanillaDrops }
            Button.REGROW -> promptRegrow(player, region, node)
            Button.DEPLETED -> promptDepleted(player, region, node)
            Button.DROPS -> open(player) { GatherItemsGUI.show(player, region, node, GatherItemsGUI.Mode.DROPS) }
            Button.POOL -> open(player) { GatherItemsGUI.show(player, region, node, GatherItemsGUI.Mode.POOL) }
            Button.DELETE -> if (event.click == ClickType.SHIFT_LEFT) {
                region.nodes.remove(node)
                GatherManager.save()
                player.sendPrefixed(
                    player.tr("gathering.editor.node-removed", "block" to GatherRender.blockName(node.block))
                )
                open(player) { GatherNodeListGUI.show(player, region) }
            }

            Button.BACK -> open(player) { GatherNodeListGUI.show(player, region) }
            null -> Unit
        }
    }

    private fun change(player: Player, edit: () -> Unit) {
        edit()
        GatherManager.save()
        GatherRender.click(player)
        GUIManager.refresh(player)
    }

    private fun open(player: Player, action: () -> Unit) {
        GatherRender.click(player)
        GatherRender.navigate(action)
    }

    private fun promptRegrow(player: Player, region: ResourceRegion, node: ResourceNode) {
        GatherRender.navigate {
            player.closeInventory()
            ChatPrompt.ask(player, player.tr("gathering.editor.prompt-regrow")) { input ->
                val seconds = GatherRender.parseSeconds(input)
                if (seconds == null) player.sendPrefixed(player.tr("common.invalid-amount"))
                else {
                    node.regrowSeconds = seconds
                    GatherManager.save()
                }
                show(player, region, node)
            }
        }
    }

    private fun promptDepleted(player: Player, region: ResourceRegion, node: ResourceNode) {
        GatherRender.navigate {
            player.closeInventory()
            ChatPrompt.ask(player, player.tr("gathering.editor.prompt-depleted")) { input ->
                if (input.equals(DEFAULT_WORD, ignoreCase = true)) {
                    node.depleted = null
                    GatherManager.save()
                } else {
                    val block = Material.matchMaterial(input)?.takeIf { it.isBlock }
                    if (block == null) player.sendPrefixed(player.tr("gathering.editor.not-a-block"))
                    else {
                        node.depleted = block
                        GatherManager.save()
                    }
                }
                show(player, region, node)
            }
        }
    }

    private fun render(player: Player, node: ResourceNode, button: Button): ItemStack = when (button) {
        Button.CATEGORY -> GatherRender.button(
            player, node.category.icon, "gui.gather-node.category",
            listOf(
                player.tr("gui.gather-node.current", "value" to player.tr(node.category.key)),
                player.tr("gui.gather-node.category-lore"),
            ),
        )

        Button.REGROW -> GatherRender.button(
            player, Material.CLOCK, "gui.gather-node.regrow",
            listOf(
                player.tr("gui.gather-node.current", "value" to GatherRender.duration(player, node.regrowSeconds)),
                player.tr("gui.gather-node.regrow-lore"),
            ),
        )

        Button.DEPLETED -> GatherRender.button(
            player, Material.BEDROCK, "gui.gather-node.depleted",
            listOf(
                player.tr("gui.gather-node.current", "value" to depletedName(player, node)),
                player.tr("gui.gather-node.depleted-lore"),
            ),
        )

        Button.VANILLA -> GatherRender.button(
            player, if (node.vanillaDrops) Material.LIME_DYE else Material.GRAY_DYE, "gui.gather-node.vanilla",
            listOf(
                player.tr(if (node.vanillaDrops) "gui.gather-node.vanilla-on" else "gui.gather-node.vanilla-off"),
                player.tr("gui.gather-node.vanilla-lore"),
            ),
        )

        Button.DROPS -> GatherRender.button(
            player, Material.CHEST, "gui.gather-node.drops",
            listOf(player.tr("gui.gather-node.drops-lore", "amount" to node.drops.size)),
        )

        Button.POOL -> GatherRender.button(
            player, Material.DIAMOND_ORE, "gui.gather-node.pool",
            listOf(player.tr("gui.gather-node.pool-lore", "amount" to node.pool.size)),
        )

        Button.DELETE -> GatherRender.button(
            player, Material.LAVA_BUCKET, "gui.gather-node.delete",
            listOf(player.tr("gui.gather-node.delete-lore")),
        )

        Button.BACK -> GatherRender.button(
            player, Material.ARROW, "gui.gather-node.back",
            listOf(player.tr("gui.gather-node.back-lore")),
        )
    }

    companion object {
        const val ID = "gather-node"

        private const val ROW_SIZE = 9
        private const val INFO_SLOT = 13
        private const val BACK_SLOT = 49
        private const val DEFAULT_WORD = "default"

        fun show(player: Player, region: ResourceRegion, node: ResourceNode): Boolean {
            GatherRender.setContext(player, GatherRender.Context(region.id, nodeId = node.id))
            return GUIManager.open(player, ID)
        }

        /** What a resource is set to, a line per setting, for its icon and its menu. */
        fun summary(player: Player, node: ResourceNode): List<String> = listOf(
            player.tr("gui.gather-node.line-category", "value" to player.tr(node.category.key)),
            player.tr("gui.gather-node.line-regrow", "value" to GatherRender.duration(player, node.regrowSeconds)),
            player.tr("gui.gather-node.line-depleted", "value" to depletedName(player, node)),
            player.tr(if (node.vanillaDrops) "gui.gather-node.vanilla-on" else "gui.gather-node.vanilla-off"),
            player.tr("gui.gather-node.line-drops", "amount" to node.drops.size),
            player.tr("gui.gather-node.line-pool", "amount" to node.pool.size),
        )

        private fun depletedName(player: Player, node: ResourceNode): String = when {
            node.growsInStages -> player.tr("gui.gather-node.seedling")
            else -> {
                val block = node.depleted ?: GatheringSettings.snapshot.depletedFor(node.category)
                val name = if (block.isAir) player.tr("gui.gather-node.air") else GatherRender.blockName(block)
                if (node.depleted == null) player.tr("gui.gather-node.default", "value" to name) else name
            }
        }
    }
}
