package net.trilleo.mc.plugins.tritown.guis.gathering

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.gathering.*
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * What every gathering menu draws with, and what each administrator is
 * editing as they move between them.
 *
 * The menus close and reopen as an administrator goes from a region to one of
 * its resources and back, and a chat prompt closes them altogether, so where
 * they are is kept here rather than in any one menu.
 */
object GatherRender {

    /** Every editor click re-checks this, not only the command that opened the menu. */
    const val EDIT_PERMISSION = "${GatherEditors.ADMIN_PERMISSION}.edit"

    data class Context(val regionId: String, val nodeId: String? = null, val spawnerId: String? = null)

    private val contexts = ConcurrentHashMap<UUID, Context>()
    private val click = Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f)

    fun setContext(player: Player, context: Context) {
        contexts[player.uniqueId] = context
    }

    fun region(player: Player): ResourceRegion? = contexts[player.uniqueId]?.regionId?.let(GatherManager::get)

    fun node(player: Player): ResourceNode? {
        val context = contexts[player.uniqueId] ?: return null
        return context.nodeId?.let { region(player)?.node(it) }
    }

    fun spawner(player: Player): ResourceSpawner? {
        val context = contexts[player.uniqueId] ?: return null
        return context.spawnerId?.let { region(player)?.spawner(it) }
    }

    /** Whether [player] may still edit; closes their menu when they may not. */
    fun mayEdit(player: Player): Boolean {
        if (player.hasPermission(EDIT_PERMISSION)) return true
        navigate { player.closeInventory() }
        return false
    }

    /** Opens the next menu on the following tick, as every menu reached by a click must. */
    fun navigate(open: () -> Unit) {
        Bukkit.getScheduler().runTask(Main.instance, Runnable { open() })
    }

    fun click(player: Player) = player.playSound(click)

    fun button(player: Player, material: Material, nameKey: String, lore: List<String>): ItemStack =
        itemStack(material) {
            name(player.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(lore.joinToString("<newline>"))) }
        }

    fun card(material: Material, name: String, lines: List<String>, glow: Boolean = false): ItemStack =
        itemStack(material) {
            name(name)
            meta {
                lore(LoreUtil.wrapLore(lines.joinToString("<newline>")))
                if (glow) setEnchantmentGlintOverride(true)
            }
        }

    /** The categories [region] offers, joined into one line. */
    fun categories(player: Player, region: ResourceRegion): String =
        region.categories().joinToString(player.tr("gathering.list-separator")) { player.tr(it.key) }
            .ifEmpty { player.tr("common.none") }

    /** What a region is shown as: the icon of the first thing it offers. */
    fun icon(region: ResourceRegion): Material = region.categories().firstOrNull()?.icon ?: Material.GRASS_BLOCK

    /** An item that shows [block], or a stand-in for a block that has no item form, like a crop. */
    fun blockIcon(block: Material): Material = when {
        block.isItem && !block.isAir -> block
        else -> CROP_ITEMS[block] ?: Material.STRUCTURE_VOID
    }

    fun eggOf(type: EntityType): Material = Material.matchMaterial("${type.name}_SPAWN_EGG") ?: Material.SPAWNER

    /** The mob a spawn egg calls up, or `null` when [item] is not one. */
    fun eggType(item: ItemStack): EntityType? {
        val name = item.type.name
        if (!name.endsWith(EGG_SUFFIX)) return null
        return EntityType.entries.firstOrNull { it.name == name.removeSuffix(EGG_SUFFIX) }
    }

    /**
     * The block an item stands for, when it can be a resource: the block
     * itself, or the crop a seed or vegetable is planted as.
     */
    fun blockOf(item: ItemStack): Material? = SEED_BLOCKS[item.type] ?: item.type.takeIf { it.isBlock && !it.isAir }

    /** A block's name in [player]'s own client language. */
    fun blockName(block: Material): String = "<lang:${block.translationKey()}>"

    fun entityName(type: EntityType): String = "<lang:${type.translationKey()}>"

    fun coordinates(player: Player, region: ResourceRegion): String {
        val area = region.area
        return player.tr(
            "gathering.coordinates",
            "world" to area.world,
            "x" to (area.minX + area.maxX) / 2,
            "y" to area.minY,
            "z" to (area.minZ + area.maxZ) / 2,
        )
    }

    /** [seconds] as minutes and seconds, or seconds alone under a minute. */
    fun duration(player: Player, seconds: Int): String =
        if (seconds < 60) player.tr("gathering.time-seconds", "seconds" to seconds)
        else player.tr("gathering.time", "minutes" to seconds / 60, "seconds" to seconds % 60)

    /** A number of seconds an administrator typed: `90`, `90s`, `5m` or `2h`. */
    fun parseSeconds(input: String): Int? {
        val text = input.trim().lowercase()
        val unit = when (text.lastOrNull()) {
            's' -> 1
            'm' -> 60
            'h' -> 3600
            else -> null
        }
        val number = (if (unit != null) text.dropLast(1) else text).trim().toIntOrNull() ?: return null
        val seconds = number.toLong() * (unit ?: 1)
        return seconds.takeIf { it in 1..MAX_SECONDS }?.toInt()
    }

    private const val MAX_SECONDS = 7L * 24 * 3600
    private const val EGG_SUFFIX = "_SPAWN_EGG"

    private val SEED_BLOCKS = mapOf(
        Material.WHEAT_SEEDS to Material.WHEAT,
        Material.CARROT to Material.CARROTS,
        Material.POTATO to Material.POTATOES,
        Material.BEETROOT_SEEDS to Material.BEETROOTS,
        Material.COCOA_BEANS to Material.COCOA,
        Material.SWEET_BERRIES to Material.SWEET_BERRY_BUSH,
        Material.TORCHFLOWER_SEEDS to Material.TORCHFLOWER_CROP,
        Material.PITCHER_POD to Material.PITCHER_CROP,
    )

    private val CROP_ITEMS = SEED_BLOCKS.entries.associate { (item, block) -> block to item }
}
