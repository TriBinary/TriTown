package net.trilleo.mc.plugins.tritown.shops

import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Turns an [ItemStack] into a string a shop file can hold, and back again.
 *
 * Paper's own byte form is used rather than a field-by-field description,
 * because it is the only round-trip that keeps every data component: a renamed,
 * enchanted, custom-model stack, a TriTown custom item, or an item another
 * plugin invented all come back exactly as they went in. Paper stamps the game
 * version into those bytes and upgrades them on the way out, so a shop survives
 * a Minecraft update.
 *
 * Decoding never throws. One unreadable entry must not take a whole shop with
 * it, so a failure is reported as `null` and left for the caller to log and skip.
 */
object ItemCodec {

    /** [item] as Base64 text. */
    fun encode(item: ItemStack): String = Base64.getEncoder().encodeToString(item.serializeAsBytes())

    /** The stack [encoded] holds, or `null` when it cannot be read. */
    fun decode(encoded: String): ItemStack? = runCatching {
        ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded))
    }.getOrNull()

    /** [items] as Base64 text, in order. */
    fun encodeAll(items: List<ItemStack>): List<String> = items.map(::encode)

    /** The stacks [encoded] holds, silently dropping any that cannot be read. */
    fun decodeAll(encoded: List<String>): List<ItemStack> = encoded.mapNotNull(::decode)
}
