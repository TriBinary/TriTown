package net.trilleo.mc.plugins.tritown.shops

/**
 * One shop: a title, a gate, and the entries it offers.
 *
 * The [id] is what NPC bindings and purchase counters are keyed by, so it never
 * changes once a shop exists; [displayName] is what players read and can be
 * edited freely. That name is written by an administrator as MiniMessage rather
 * than being a translation key, because a server's own shops are named in the
 * server's own words.
 *
 * @param npcIds FancyNpcs ids, not names, so renaming an NPC does not break the binding
 */
data class ShopDefinition(
    val id: String,
    var displayName: String,
    var gate: ShopGate = ShopGate.OPEN,
    val entries: MutableList<ShopEntry> = mutableListOf(),
    val npcIds: MutableSet<String> = linkedSetOf(),
) {

    /** The entry with [entryId], or `null` when it has been removed since the menu was opened. */
    fun entry(entryId: String): ShopEntry? = entries.firstOrNull { it.id == entryId }

    companion object {
        /** Whether [id] is usable: lowercase letters, digits, dashes and underscores only. */
        fun isValidId(id: String): Boolean = id.isNotEmpty() && id.length <= 32 && ID_PATTERN.matches(id)

        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9_-]*")
    }
}
