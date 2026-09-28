package net.trilleo.mc.plugins.tritown.guis.bestiary

import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.MobKindDef
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.gear.GearText
import net.trilleo.mc.plugins.tritown.guis.menu.MainMenuGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.mobs.BestiaryRecords
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/**
 * Every custom mob of `bestiary.yml`, variants first and then bosses, as the
 * viewer knows it.
 *
 * One they have not slain yet is a blank page that only says where to look.
 * One they have shows everything: what it is, where it lives or how it is
 * summoned, its abilities and affixes, how many they have slain, and which of
 * its own loot they have found. It reads, it never acts.
 */
class BestiaryGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.bestiary.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    override fun getItems(player: Player): List<ItemStack> =
        kinds().map { kind ->
            val record = BestiaryRecords.of(player, kind.id)
            if (record.kills > 0) page(player, kind, record) else blank(player, kind)
        }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> =
        mapOf(MenuRender.BACK_OFFSET to MenuRender.back(player), MenuRender.EXTRA_OFFSET to progress(player))

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        if (offset == MenuRender.BACK_OFFSET) MenuRender.later(player) { MainMenuGUI.show(player) }
    }

    private fun page(viewer: Player, kind: MobKindDef, record: BestiaryRecords.Record): ItemStack {
        val boss = kind.boss
        val lines = buildList {
            add(viewer.tr("gui.bestiary.lore", "lore" to viewer.tr(kind.loreKey)))
            add("")
            add(where(viewer, kind))
            if (kind.abilities.isNotEmpty() || boss?.phases.orEmpty().any { it.abilities.isNotEmpty() }) {
                val abilities = (kind.abilities + boss?.phases.orEmpty().flatMap { it.abilities }).distinct()
                add(
                    viewer.tr(
                        "gui.bestiary.abilities",
                        "abilities" to abilities.joinToString(", ") { viewer.tr(it.key) })
                )
            }
            if (kind.affixes.isNotEmpty()) {
                add(
                    viewer.tr(
                        "gui.bestiary.affixes",
                        "affixes" to kind.affixes.sortedBy { it.ordinal }.joinToString(", ") { viewer.tr(it.key) })
                )
            }
            add("")
            add(viewer.tr("gui.bestiary.kills", "amount" to record.kills, "stars" to "★".repeat(stars(record.kills))))

            val own = kind.loot.map(BestiaryRecords::idOf).distinct()
            val found = own.filter { it in record.drops }
            if (own.isNotEmpty()) add(viewer.tr("gui.bestiary.drops", "found" to found.size, "total" to own.size))
            found.forEach { id -> add(viewer.tr("gui.bestiary.drop", "name" to dropName(viewer, id))) }
        }
        return itemStack(icon(kind)) {
            name(
                viewer.tr(
                    if (boss != null) "gui.bestiary.boss" else "gui.bestiary.variant",
                    "name" to viewer.tr(kind.nameKey)
                )
            )
            meta {
                lore(LoreUtil.wrapLore(lines.joinToString("<newline>")))
                if (boss != null) setEnchantmentGlintOverride(true)
            }
        }
    }

    private fun blank(viewer: Player, kind: MobKindDef): ItemStack = itemStack(Material.INK_SAC) {
        name(viewer.tr("gui.bestiary.unknown"))
        meta {
            lore(
                LoreUtil.wrapLore(
                    listOf(
                        viewer.tr("gui.bestiary.unknown-lore"),
                        "",
                        where(viewer, kind)
                    ).joinToString("<newline>")
                )
            )
        }
    }

    /** Where a variant lives, or where a boss answers its sigil and at what level. */
    private fun where(viewer: Player, kind: MobKindDef): String {
        val boss = kind.boss
        if (boss != null) {
            return viewer.tr(
                "gui.bestiary.summoned",
                "level" to boss.level,
                "sigil" to GearText.item(viewer, boss.sigil),
            )
        }
        val spawn = kind.spawn ?: return ""
        return viewer.tr(
            "gui.bestiary.lives",
            "worlds" to worlds(viewer, spawn.worlds),
            "min" to spawn.minLevel,
            "max" to spawn.maxLevel,
        )
    }

    private fun worlds(viewer: Player, names: Set<String>): String {
        if (names.isEmpty()) return viewer.tr("gui.bestiary.world.any")
        return names.joinToString(", ") { name ->
            when (name) {
                "normal" -> viewer.tr("gui.bestiary.world.normal")
                "nether" -> viewer.tr("gui.bestiary.world.nether")
                "the_end" -> viewer.tr("gui.bestiary.world.end")
                else -> name
            }
        }
    }

    private fun dropName(viewer: Player, id: String): String {
        val gear = BestiaryRecords.gearId(id) ?: return GearText.item(viewer, id)
        return ContentRegistry.gear.gear[gear]?.let { viewer.tr(it.nameKey) } ?: gear
    }

    /** How many of the viewer's pages are filled in, on every page of the menu. */
    private fun progress(viewer: Player): ItemStack {
        val kinds = kinds()
        val found = kinds.count { BestiaryRecords.of(viewer, it.id).kills > 0 }
        return itemStack(Material.WRITABLE_BOOK) {
            name(viewer.tr("gui.bestiary.progress", "found" to found, "total" to kinds.size))
            meta { lore(LoreUtil.wrapLore(viewer.tr("gui.bestiary.progress-lore"))) }
        }
    }

    private fun kinds(): List<MobKindDef> =
        ContentRegistry.bestiary.kinds.values.sortedBy { if (it.boss != null) 1 else 0 }

    private fun icon(kind: MobKindDef): Material = Material.matchMaterial("${kind.base}_SPAWN_EGG") ?: Material.BOOK

    /** A star for every tenfold of kills: 10, 100, 1,000. */
    private fun stars(kills: Int): Int = STAR_KILLS.count { kills >= it }

    companion object {
        const val ID = "bestiary"

        private val STAR_KILLS = listOf(10, 100, 1000)

        fun show(player: Player): Boolean = GUIManager.open(player, ID)
    }
}
