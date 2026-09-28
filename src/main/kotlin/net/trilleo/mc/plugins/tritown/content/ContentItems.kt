package net.trilleo.mc.plugins.tritown.content

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tritown.registration.PluginItem
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.Lang
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * Makes and recognises the items `items.yml` defines.
 *
 * Every one is an echo shard wearing the look of the vanilla item its
 * definition names. An echo shard's only use is crafting a recovery compass,
 * which `ContentItemListener` refuses, so a content item does nothing but be
 * itself. It carries its id under [PluginItem.ITEM_ID_KEY] like every other
 * custom item.
 *
 * Its name and lore are written in the item language ([Lang.item]), so every
 * copy is identical and they stack wherever they came from. Each carries a
 * stamp of what it was drawn from, and [refresh] redraws one drawn from an
 * older definition or language (see [ItemRedraw]).
 */
object ContentItems {

    val BASE: Material = Material.ECHO_SHARD

    private val REVISION = NamespacedKey("tritown", "revision")

    /** [amount] of the item [id] names, or `null` if `items.yml` has no such item. */
    fun create(id: String, amount: Int = 1): ItemStack? {
        val def = ContentRegistry.items[id] ?: return null
        return ItemStack.of(BASE, amount).also { draw(it, def) }
    }

    /**
     * Redraws [stack] if it is a content item drawn from an older definition or
     * item language. Cheap when it is not: one read of its data. An item whose
     * definition has left `items.yml` is left exactly as it is.
     *
     * @return whether it was redrawn
     */
    fun refresh(stack: ItemStack?): Boolean {
        val def = idOf(stack)?.let { ContentRegistry.items[it] } ?: return false
        if (stack!!.persistentDataContainer.get(REVISION, PersistentDataType.INTEGER) == revision(def)) return false
        draw(stack, def)
        return true
    }

    /**
     * The id of the content item [stack] is, or `null` if it is not one — even
     * one whose definition has since been taken out of `items.yml`, so it stays
     * as inert as the day it dropped. Read without cloning its meta.
     */
    fun idOf(stack: ItemStack?): String? {
        if (stack == null || stack.isEmpty || stack.type != BASE) return null
        return stack.persistentDataContainer.get(PluginItem.ITEM_ID_KEY, PersistentDataType.STRING)
    }

    private fun draw(stack: ItemStack, def: ContentItemDef) {
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(Key.MINECRAFT_NAMESPACE, def.model))
        stack.setData(DataComponentTypes.ITEM_NAME, text(def.nameKey).applyFallbackStyle(def.rarity.color))
        stack.setData(
            DataComponentTypes.LORE,
            ItemLore.lore(
                listOf(
                    text(def.loreKey).applyFallbackStyle(NamedTextColor.GRAY),
                    Component.empty(),
                    text(def.rarity.key),
                ).map { it.decoration(TextDecoration.ITALIC, false) }
            ),
        )
        if (def.glint) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        } else stack.resetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)
        stack.editPersistentDataContainer {
            it.set(PluginItem.ITEM_ID_KEY, PersistentDataType.STRING, def.id)
            it.set(REVISION, PersistentDataType.INTEGER, revision(def))
        }
    }

    private fun text(key: String): Component = ComponentUtil.parse(Lang.item(key))

    /** A stamp of what [def] is drawn from, built from text so it is the same from one start to the next. */
    private fun revision(def: ContentItemDef): Int = 31 * def.toString().hashCode() + Lang.itemRevision
}
