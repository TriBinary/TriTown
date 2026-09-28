package net.trilleo.mc.plugins.tritown.gear

import net.trilleo.mc.plugins.tritown.combat.StatSheet
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.GearDef
import net.trilleo.mc.plugins.tritown.content.GearSlot
import net.trilleo.mc.plugins.tritown.content.Rarity
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

/**
 * Makes gear, keeps it drawn, and says what a piece is worth — the one entry
 * point the rest of TriTown uses.
 *
 * A piece is a stack of its definition's base item with a [GearData] on it.
 * Its stats are never stored: [stats] works them out from the content in
 * force, and [refresh] redraws a piece whose tooltip was drawn from older
 * content, so an edit to `gear.yml` or `balance.yml` reaches every piece.
 */
object Gear {

    /** A new piece of [def] at [rarity], with fresh rolls, or `null` if its base is not an item. */
    fun create(def: GearDef, rarity: Rarity, random: Random = Random): ItemStack? {
        val base = Material.matchMaterial(def.base)?.takeIf { it.isItem } ?: return null
        val stack = ItemStack.of(base)
        val tuning = ContentRegistry.balance.gear
        val data = GearData(
            id = def.id,
            rarity = rarity,
            stars = 0,
            rolls = GearStats.roll(def, tuning, random),
            reforge = null,
            revision = 0,
        )
        return stack.also { save(it, data) }
    }

    /** A piece of [def] as the Forge shows it before it is made: common, with every stat at its average roll. */
    fun preview(def: GearDef): ItemStack? {
        val base = Material.matchMaterial(def.base)?.takeIf { it.isItem } ?: return null
        val average = GearStats.averageRoll(ContentRegistry.balance.gear)
        val data = GearData(def.id, Rarity.COMMON, 0, def.weights.keys.associateWith { average }, null, 0)
        return ItemStack.of(base).also { save(it, data) }
    }

    /**
     * [def]'s look alone, for a custom mob to wear: its base item with its
     * model, trim and dye, and no data, so it is not a piece of gear at all.
     */
    fun costume(def: GearDef): ItemStack? {
        val base = Material.matchMaterial(def.base)?.takeIf { it.isItem } ?: return null
        return ItemStack.of(base).also { GearRender.look(it, def) }
    }

    /** What [stack] is and what it is worth, or `null` if it is not gear the content files still define. */
    fun read(stack: ItemStack?): Piece? {
        val data = GearCodec.read(stack) ?: return null
        val def = ContentRegistry.gear.gear[data.id] ?: return null
        return Piece(def, data)
    }

    /** The stats [stack] gives, or `null` if it is not gear the content files still define. */
    fun stats(stack: ItemStack?): StatSheet? = read(stack)?.stats

    /** Whether [stack] is shot rather than swung: a bow or crossbow, gear or not. */
    fun isRanged(stack: ItemStack?): Boolean {
        if (stack == null || stack.isEmpty) return false
        return read(stack)?.def?.slot == GearSlot.BOW || stack.type == Material.BOW || stack.type == Material.CROSSBOW
    }

    /** Writes [data] onto [stack] and redraws it. */
    fun save(stack: ItemStack, data: GearData) {
        val def = ContentRegistry.gear.gear[data.id] ?: return
        val stamped = data.copy(revision = revision(def))
        GearRender.draw(stack, def, stamped, Piece(def, stamped).stats, reforge(stamped))
    }

    /**
     * Redraws [stack] if the content it was drawn from has changed since.
     * Cheap when nothing has: one read of its data.
     *
     * @return whether it was redrawn
     */
    fun refresh(stack: ItemStack?): Boolean {
        val data = GearCodec.read(stack) ?: return false
        val def = ContentRegistry.gear.gear[data.id] ?: return false
        if (data.revision == revision(def)) return false
        save(stack!!, data)
        return true
    }

    private fun revision(def: GearDef): Int = ContentRegistry.gear.revision(def.id, ContentRegistry.balance.gear)

    private fun reforge(data: GearData) = data.reforge?.let { ContentRegistry.gear.reforges[it] }

    /** A piece of gear: its definition and its data, and what they are worth together. */
    class Piece(val def: GearDef, val data: GearData) {

        val stats: StatSheet by lazy { GearStats.of(def, data, ContentRegistry.balance.gear, reforge(data)) }
    }
}
