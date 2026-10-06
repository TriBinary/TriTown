package net.trilleo.mc.plugins.tritown.guis.gathering

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.gathering.*
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A resource's two lists: the extra items a harvest may give, each with its
 * chance, and the blocks it may grow back as, each with its weight.
 *
 * Either is added to by clicking an item in your own inventory, which is
 * copied and never taken, the way a shop entry is added.
 */
class GatherItemsGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.gather-drops.title",
    rows = 6,
    layout = PagedLayout.FRAMED,
) {

    enum class Mode { DROPS, POOL }

    private val modes = ConcurrentHashMap<UUID, Mode>()

    override fun title(player: Player): Component = ComponentUtil.parse(
        player.tr(if (modeOf(player) == Mode.DROPS) "gui.gather-drops.title" else "gui.gather-pool.title")
    )

    override fun getItems(player: Player): List<ItemStack> {
        val node = GatherRender.node(player) ?: return emptyList()
        return when (modeOf(player)) {
            Mode.DROPS -> node.drops.map { drop(player, it) }
            Mode.POOL -> node.pool.map { pool(player, node, it) }
        }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> {
        val drops = modeOf(player) == Mode.DROPS
        return mapOf(
            BACK_OFFSET to GatherRender.button(
                player, Material.ARROW, "gui.gather-node.back-node", listOf(player.tr("gui.gather-node.back-node-lore")),
            ),
            HELP_OFFSET to GatherRender.button(
                player, Material.PAPER,
                if (drops) "gui.gather-drops.add" else "gui.gather-pool.add",
                listOf(player.tr(if (drops) "gui.gather-drops.add-lore" else "gui.gather-pool.add-lore")),
            ),
        )
    }

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        if (offset != BACK_OFFSET) return
        val region = GatherRender.region(player) ?: return
        val node = GatherRender.node(player) ?: return
        GatherRender.click(player)
        GatherRender.navigate { GatherNodeGUI.show(player, region, node) }
    }

    override fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player
        if (player != null && event.clickedInventory === player.inventory) {
            event.isCancelled = true
            if (!GatherRender.mayEdit(player)) return
            val node = GatherRender.node(player) ?: return
            val item = event.currentItem?.takeUnless { it.type.isAir } ?: return
            add(player, node, item)
            refresh(player, event.view.topInventory)
            return
        }
        super.onClick(event)
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        if (!GatherRender.mayEdit(player)) return
        val region = GatherRender.region(player) ?: return
        val node = GatherRender.node(player) ?: return
        val index = contentIndex(event, page) ?: return
        val mode = modeOf(player)
        val size = if (mode == Mode.DROPS) node.drops.size else node.pool.size
        if (index >= size) return

        if (event.click == ClickType.SHIFT_LEFT) {
            if (mode == Mode.DROPS) node.drops.removeAt(index) else node.pool.removeAt(index)
            GatherManager.save()
            refresh(player, event.inventory)
            return
        }

        GatherRender.navigate {
            player.closeInventory()
            val question = if (mode == Mode.DROPS) "gathering.editor.prompt-chance" else "gathering.editor.prompt-weight"
            ChatPrompt.ask(player, player.tr(question)) { input ->
                if (!apply(node, mode, index, input)) player.sendPrefixed(player.tr("common.invalid-amount"))
                show(player, region, node, mode)
            }
        }
    }

    private fun apply(node: ResourceNode, mode: Mode, index: Int, input: String): Boolean {
        when (mode) {
            Mode.DROPS -> {
                val chance = input.removeSuffix("%").trim().toDoubleOrNull()?.takeIf { it > 0.0 && it <= 100.0 }
                    ?: return false
                node.drops.getOrNull(index)?.chance = chance
            }

            Mode.POOL -> {
                val weight = input.trim().toIntOrNull()?.takeIf { it in 1..MAX_WEIGHT } ?: return false
                node.pool.getOrNull(index)?.weight = weight
            }
        }
        GatherManager.save()
        return true
    }

    private fun add(player: Player, node: ResourceNode, item: ItemStack) {
        when (modeOf(player)) {
            Mode.DROPS -> node.drops += ResourceDrop(item.clone(), DEFAULT_CHANCE)
            Mode.POOL -> {
                val block = GatherRender.blockOf(item) ?: run {
                    player.sendPrefixed(player.tr("gathering.editor.not-a-block"))
                    return
                }
                if (node.pool.any { it.block == block }) return
                node.pool += PoolEntry(block, 1)
            }
        }
        GatherManager.save()
        GatherRender.click(player)
    }

    private fun drop(player: Player, drop: ResourceDrop): ItemStack = LoreUtil.withLore(
        drop.item.clone(),
        listOf(
            player.tr("gui.gather-drops.chance", "chance" to formatChance(drop.chance)),
            "",
            player.tr("gui.gather-drops.click"),
            player.tr("gui.gather-items.shift-click"),
        ),
    )

    private fun pool(player: Player, node: ResourceNode, entry: PoolEntry): ItemStack {
        val total = node.pool.sumOf { it.weight }.coerceAtLeast(1)
        return GatherRender.card(
            GatherRender.blockIcon(entry.block),
            player.tr("gui.gather-nodes.name", "block" to GatherRender.blockName(entry.block)),
            listOf(
                player.tr(
                    "gui.gather-pool.weight",
                    "weight" to entry.weight, "chance" to formatChance(entry.weight * 100.0 / total),
                ),
                "",
                player.tr("gui.gather-pool.click"),
                player.tr("gui.gather-items.shift-click"),
            ),
        )
    }

    private fun formatChance(chance: Double): String = "%.1f".format(Locale.ROOT, chance)

    private fun modeOf(player: Player): Mode = modes[player.uniqueId] ?: Mode.DROPS

    companion object {
        const val ID = "gather-items"
        private const val BACK_OFFSET = 3
        private const val HELP_OFFSET = 5
        private const val DEFAULT_CHANCE = 100.0
        private const val MAX_WEIGHT = 10_000

        fun show(player: Player, region: ResourceRegion, node: ResourceNode, mode: Mode): Boolean {
            val gui = GUIManager.getGUI(ID) as? GatherItemsGUI ?: return false
            gui.modes[player.uniqueId] = mode
            GatherRender.setContext(player, GatherRender.Context(region.id, nodeId = node.id))
            return GUIManager.open(player, ID)
        }
    }
}
