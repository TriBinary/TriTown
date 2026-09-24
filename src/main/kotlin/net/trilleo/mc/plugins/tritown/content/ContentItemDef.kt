package net.trilleo.mc.plugins.tritown.content

/**
 * An item defined in `items.yml`: a material mobs drop, an essence, and so on.
 *
 * Every one is the same inert base item wearing the look of the vanilla item
 * [model] names, so it cannot be crafted with, placed, eaten or used as what it
 * looks like. Its name and lore are `item.<id>.name` and `item.<id>.lore` in the
 * language files.
 *
 * @param model a vanilla item's id, such as `gunpowder`, whose look it takes
 */
data class ContentItemDef(val id: String, val model: String, val rarity: Rarity, val glint: Boolean) {

    val nameKey: String
        get() = "$KEY_PREFIX$id.name"

    val loreKey: String
        get() = "$KEY_PREFIX$id.lore"

    companion object {
        /** Where content items' names live in the language files, which the language tests know is built at runtime. */
        const val KEY_PREFIX = "item."

        /** What an id may look like: it is also a translation key segment and a command argument. */
        val ID = Regex("[a-z0-9][a-z0-9-]*")
    }
}
