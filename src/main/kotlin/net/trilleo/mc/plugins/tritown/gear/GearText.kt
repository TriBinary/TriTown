package net.trilleo.mc.plugins.tritown.gear

import net.kyori.adventure.text.format.NamedTextColor
import net.trilleo.mc.plugins.tritown.content.ContentItems
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.Rarity
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack

/**
 * Gear and content items named in one reader's language, for chat and menus.
 * An item itself is drawn by [GearRender] for every viewer at once; these are
 * for text that only one player reads.
 */
object GearText {

    /** A piece's full name — reforge and all — in its rarity's colour, ready to embed in MiniMessage. */
    fun name(reader: CommandSender, piece: Gear.Piece): String {
        val name = reader.tr(piece.def.nameKey)
        val reforge = piece.data.reforge?.let { ContentRegistry.gear.reforges[it] }?.let { reader.tr(it.key) }
        return colored(piece.data.rarity, if (reforge == null) name else "$reforge $name")
    }

    /** A content item's name in its rarity's colour, or its id if `items.yml` no longer has it. */
    fun item(reader: CommandSender, id: String): String {
        val def = ContentRegistry.items[id] ?: return id
        return colored(def.rarity, reader.tr(def.nameKey))
    }

    fun rarity(reader: CommandSender, rarity: Rarity): String = reader.tr(rarity.key)

    /** What [stack] is — `3x Grave Dust`, or a piece of gear by its name — or `null` if it is neither. */
    fun stack(reader: CommandSender, stack: ItemStack): String? {
        Gear.read(stack)?.let { return name(reader, it) }
        val id = ContentItems.idOf(stack) ?: return null
        return "${stack.amount}x ${item(reader, id)}"
    }

    private fun colored(rarity: Rarity, text: String): String {
        val color = NamedTextColor.NAMES.key(rarity.color) ?: return text
        return "<$color>$text</$color>"
    }
}
