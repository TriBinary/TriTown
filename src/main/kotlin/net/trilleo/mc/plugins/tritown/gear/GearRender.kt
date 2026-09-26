package net.trilleo.mc.plugins.tritown.gear

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.DyedItemColor
import io.papermc.paper.datacomponent.item.ItemArmorTrim
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tritown.combat.CombatFormat
import net.trilleo.mc.plugins.tritown.combat.Stat
import net.trilleo.mc.plugins.tritown.combat.StatSheet
import net.trilleo.mc.plugins.tritown.content.GearDef
import net.trilleo.mc.plugins.tritown.content.Rarity
import net.trilleo.mc.plugins.tritown.content.ReforgeDef
import net.trilleo.mc.plugins.tritown.utils.LangTranslator
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.trim.ArmorTrim

/**
 * Draws a piece of gear: its name, its tooltip and its look.
 *
 * Every word is a [LangTranslator] key, so each player reads the piece in
 * their own language, and numbers are plain text beside them. Drawing works
 * on the stack in place and leaves alone what a player gave it — enchantments,
 * an anvil name — so a redraw after a content edit loses nothing.
 */
object GearRender {

    private val RARE_ENOUGH_TO_GLOW = Rarity.EPIC

    fun draw(stack: ItemStack, def: GearDef, data: GearData, stats: StatSheet, reforge: ReforgeDef?) {
        stack.setData(DataComponentTypes.ITEM_NAME, name(def, data, reforge))
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore(def, data, stats).map(::plain)))
        stack.setData(DataComponentTypes.UNBREAKABLE)
        stack.setData(
            DataComponentTypes.TOOLTIP_DISPLAY,
            TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.UNBREAKABLE, DataComponentTypes.ATTRIBUTE_MODIFIERS)
                .build(),
        )

        if (def.model != null) {
            stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(Key.MINECRAFT_NAMESPACE, def.model))
        } else stack.resetData(DataComponentTypes.ITEM_MODEL)

        val trim = def.trim?.let(::trim)
        if (trim != null) {
            stack.setData(DataComponentTypes.TRIM, ItemArmorTrim.itemArmorTrim(trim).build())
        } else stack.resetData(DataComponentTypes.TRIM)

        if (def.dye != null) {
            stack.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.fromRGB(def.dye)))
        } else stack.resetData(DataComponentTypes.DYED_COLOR)

        if (data.rarity >= RARE_ENOUGH_TO_GLOW) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        } else stack.resetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)

        GearCodec.write(stack, data)
    }

    /** The piece's name, after its reforge's if it has one, in its rarity's colour. */
    private fun name(def: GearDef, data: GearData, reforge: ReforgeDef?): Component {
        val name = LangTranslator.component(def.nameKey)
        val full = if (reforge == null) name else LangTranslator.component(reforge.key).append(Component.space()).append(name)
        return full.color(data.rarity.color).decoration(TextDecoration.ITALIC, false)
    }

    private fun lore(def: GearDef, data: GearData, stats: StatSheet): List<Component> = buildList {
        Stat.entries.filter { stats[it] != 0.0 }.forEach { stat ->
            add(
                LangTranslator.component(stat.key)
                    .append(Component.text(" +" + CombatFormat.stat(stat, stats[stat]), NamedTextColor.WHITE))
            )
        }
        add(Component.empty())
        add(LangTranslator.component(def.loreKey).color(NamedTextColor.GRAY))
        add(Component.empty())
        add(
            LangTranslator.component("gear.pvp").color(NamedTextColor.DARK_GRAY)
                .append(Component.space())
                .append(Component.translatable(baseTranslationKey(def), NamedTextColor.DARK_GRAY))
        )
        add(footer(def, data))
    }

    /** `★★★ RARE WEAPON`: the piece's stars, then its rarity and slot in the rarity's colour. */
    private fun footer(def: GearDef, data: GearData): Component {
        val stars = if (data.stars > 0) Component.text("★".repeat(data.stars) + " ", NamedTextColor.GOLD) else Component.empty()
        return stars
            .append(LangTranslator.component(data.rarity.key))
            .append(Component.space())
            .append(LangTranslator.component(def.slot.key).color(data.rarity.color).decorate(TextDecoration.BOLD))
    }

    /** The game's own key for the base item's name, which each client fills in itself. */
    private fun baseTranslationKey(def: GearDef): String =
        Material.matchMaterial(def.base)?.translationKey() ?: def.base.lowercase()

    private fun trim(trim: GearDef.Trim): ArmorTrim? {
        val material = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(trim.material)) ?: return null
        val pattern = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(trim.pattern)) ?: return null
        return ArmorTrim(material, pattern)
    }

    private fun plain(line: Component): Component = line.decoration(TextDecoration.ITALIC, false)
}
