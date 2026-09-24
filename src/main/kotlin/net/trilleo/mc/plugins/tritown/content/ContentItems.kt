package net.trilleo.mc.plugins.tritown.content

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tritown.registration.PluginItem
import net.trilleo.mc.plugins.tritown.utils.LangTranslator
import org.bukkit.Material
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
 * Its name and lore are translation keys, not text: each player reads them in
 * their own language (see [LangTranslator]), and two copies are always
 * identical, so they stack wherever they came from.
 */
object ContentItems {

    val BASE: Material = Material.ECHO_SHARD

    /** [amount] of the item [id] names, or `null` if `items.yml` has no such item. */
    fun create(id: String, amount: Int = 1): ItemStack? {
        val def = ContentRegistry.items[id] ?: return null
        val stack = ItemStack.of(BASE, amount)

        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(Key.MINECRAFT_NAMESPACE, def.model))
        stack.setData(
            DataComponentTypes.ITEM_NAME,
            LangTranslator.component(def.nameKey).color(def.rarity.color),
        )
        stack.setData(
            DataComponentTypes.LORE,
            ItemLore.lore(
                listOf(
                    LangTranslator.component(def.loreKey).color(NamedTextColor.GRAY),
                    Component.empty(),
                    LangTranslator.component(def.rarity.key),
                ).map { it.decoration(TextDecoration.ITALIC, false) }
            ),
        )
        if (def.glint) stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        stack.editPersistentDataContainer { it.set(PluginItem.ITEM_ID_KEY, PersistentDataType.STRING, def.id) }
        return stack
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
}
