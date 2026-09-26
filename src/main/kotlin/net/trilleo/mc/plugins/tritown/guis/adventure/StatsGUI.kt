package net.trilleo.mc.plugins.tritown.guis.adventure

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.combat.*
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.guis.menu.MainMenuGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A player's combat stats, each broken down by where it comes from.
 *
 * Opened with `/tritown stats [player]` or by clicking the profile in the main
 * menu. It only reads: nothing here changes a stat.
 */
class StatsGUI : PluginGUI(
    id = ID,
    titleKey = "gui.stats.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    /** Whose stats each viewer is looking at. */
    private val targets = ConcurrentHashMap<UUID, UUID>()

    override fun title(player: Player): Component {
        val target = target(player)
        if (target == null || target == player) return super.title(player)
        return ComponentUtil.parse(player.tr("gui.stats.title-other", "name" to ComponentUtil.escape(target.name)))
    }

    override fun setup(player: Player, inventory: Inventory) {
        val target = target(player) ?: player
        val breakdown = StatSources.breakdown(target)
        val sheet = breakdown.values.fold(StatSheet.EMPTY, StatSheet::plus)

        val slots = mutableMapOf(PROFILE_SLOT to profile(player, target), BACK_SLOT to MenuRender.back(player))
        ROWS.forEachIndexed { index, stats ->
            GUIFrame.spacedColumns(stats.size).zip(stats).forEach { (column, stat) ->
                slots[(FIRST_STAT_ROW + index) * ROW_SIZE + column] = stat(player, stat, sheet, breakdown)
            }
        }

        GUIFrame.draw(inventory, slots.keys)
        slots.forEach { (slot, item) -> inventory.setItem(slot, item) }
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== event.view.topInventory || event.rawSlot != BACK_SLOT) return
        val player = event.whoClicked as? Player ?: return
        MenuRender.later(player) { MainMenuGUI.show(player) }
    }

    override fun onClose(event: InventoryCloseEvent) {
        targets.remove(event.player.uniqueId)
    }

    private fun target(viewer: Player): Player? = targets[viewer.uniqueId]?.let(Bukkit::getPlayer)

    private fun profile(viewer: Player, target: Player): ItemStack = MenuRender.head(
        target,
        viewer.tr(
            if (viewer == target) "gui.stats.profile" else "gui.stats.profile-other",
            "name" to ComponentUtil.escape(target.name),
        ),
        listOf(
            viewer.tr(
                "gui.stats.profile-health",
                "health" to CombatFormat.health(CombatHealth.current(target)),
                "max" to CombatFormat.number(CombatHealth.max(target)),
            ),
            "",
            viewer.tr("gui.stats.profile-pvp"),
        ),
    )

    private fun stat(viewer: Player, stat: Stat, sheet: StatSheet, breakdown: Map<StatSources.Source, StatSheet>): ItemStack {
        val lines = mutableListOf(viewer.tr(description(stat)))
        if (stat == Stat.DEFENSE) {
            val reduction = (1.0 - DamageMath.defenseMultiplier(sheet[stat])) * 100.0
            lines += viewer.tr("gui.stats.defense-reduction", "percent" to CombatFormat.number(reduction))
        }

        val sources = breakdown.filterValues { it[stat] != 0.0 }
        if (sources.isNotEmpty()) {
            lines += ""
            sources.forEach { (source, values) ->
                lines += viewer.tr(
                    "gui.stats.source-line",
                    "source" to viewer.tr(source.key),
                    "value" to signed(stat, values[stat], first = source == StatSources.Source.BASE),
                )
            }
        }

        return itemStack(icon(stat)) {
            name(viewer.tr("gui.stats.stat-name", "stat" to viewer.tr(stat.key), "value" to CombatFormat.stat(stat, sheet[stat])))
            flag(ItemFlag.HIDE_ATTRIBUTES)
            meta { lore(LoreUtil.wrapLore(lines.joinToString("<newline>"))) }
        }
    }

    /** A source's share, with a sign unless it is the base the others add to. */
    private fun signed(stat: Stat, value: Double, first: Boolean): String {
        val text = CombatFormat.stat(stat, value)
        return if (first || value < 0.0) text else "+$text"
    }

    private fun description(stat: Stat): String = when (stat) {
        Stat.HEALTH -> "gui.stats.about.health"
        Stat.DEFENSE -> "gui.stats.about.defense"
        Stat.DAMAGE -> "gui.stats.about.damage"
        Stat.STRENGTH -> "gui.stats.about.strength"
        Stat.CRIT_CHANCE -> "gui.stats.about.crit-chance"
        Stat.CRIT_DAMAGE -> "gui.stats.about.crit-damage"
        Stat.SPEED -> "gui.stats.about.speed"
        Stat.VITALITY -> "gui.stats.about.vitality"
        Stat.MAGIC_FIND -> "gui.stats.about.magic-find"
    }

    private fun icon(stat: Stat): Material = when (stat) {
        Stat.HEALTH -> Material.GOLDEN_APPLE
        Stat.DEFENSE -> Material.IRON_CHESTPLATE
        Stat.DAMAGE -> Material.IRON_SWORD
        Stat.STRENGTH -> Material.BLAZE_POWDER
        Stat.CRIT_CHANCE -> Material.SPECTRAL_ARROW
        Stat.CRIT_DAMAGE -> Material.FIRE_CHARGE
        Stat.SPEED -> Material.SUGAR
        Stat.VITALITY -> Material.GLISTERING_MELON_SLICE
        Stat.MAGIC_FIND -> Material.RABBIT_FOOT
    }

    private fun open(viewer: Player, target: Player) {
        targets[viewer.uniqueId] = target.uniqueId
        GUIManager.open(viewer, ID)
    }

    companion object {
        const val ID = "stats"

        private const val ROW_SIZE = 9
        private const val PROFILE_SLOT = 13
        private const val FIRST_STAT_ROW = 2
        private const val BACK_SLOT = 49

        /** Staying alive, then hitting hard, then everything else. */
        private val ROWS = listOf(
            listOf(Stat.HEALTH, Stat.DEFENSE),
            listOf(Stat.DAMAGE, Stat.STRENGTH, Stat.CRIT_CHANCE, Stat.CRIT_DAMAGE),
            listOf(Stat.SPEED, Stat.VITALITY, Stat.MAGIC_FIND),
        )

        /** Opens [target]'s stats for [viewer]. */
        fun show(viewer: Player, target: Player = viewer): Boolean {
            val gui = GUIManager.getGUI(ID) as? StatsGUI ?: return false
            gui.open(viewer, target)
            return true
        }
    }
}
