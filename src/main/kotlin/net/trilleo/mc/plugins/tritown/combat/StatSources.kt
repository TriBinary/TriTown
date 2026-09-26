package net.trilleo.mc.plugins.tritown.combat

import io.papermc.paper.datacomponent.DataComponentTypes
import net.trilleo.mc.plugins.tritown.content.Balance
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.gear.Gear
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType

/**
 * Where a player's stats come from, and the only place a [StatSheet] is built.
 *
 * A piece of TriTown gear gives the stats [Gear] works out for it. Vanilla
 * items count too: a worn piece of armor is Defense by its armor and
 * toughness, and the held weapon is Damage by its attack damage, both through
 * `balance.yml`. That is what keeps level-1 play with vanilla gear vanilla. A
 * piece of gear's own vanilla armor or attack damage never counts twice: it
 * only matters between players.
 */
object StatSources {

    /** A place stats come from, as the stats menu breaks them down. */
    enum class Source {
        BASE, ARMOR, WEAPON, EFFECTS;

        /** The translation key naming the source, spelled out so the language test can see it. */
        val key: String
            get() = when (this) {
                BASE -> "gui.stats.source.base"
                ARMOR -> "gui.stats.source.armor"
                WEAPON -> "gui.stats.source.weapon"
                EFFECTS -> "gui.stats.source.effects"
            }
    }

    private val ARMOR_SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    fun total(player: Player): StatSheet = breakdown(player).values.fold(StatSheet.EMPTY, StatSheet::plus)

    /** What each source gives [player]; a source with nothing to give is left out. */
    fun breakdown(player: Player): Map<Source, StatSheet> {
        val balance = ContentRegistry.balance
        return linkedMapOf(
            Source.BASE to base(balance),
            Source.ARMOR to armor(player, balance),
            Source.WEAPON to weapon(player, balance),
            Source.EFFECTS to effects(player, balance),
        ).filterValues { !it.isEmpty }
    }

    private fun base(balance: Balance): StatSheet = StatSheet.of(
        Stat.HEALTH to balance.player.health,
        Stat.CRIT_CHANCE to balance.player.critChance,
        Stat.CRIT_DAMAGE to balance.player.critDamage,
    )

    private fun armor(player: Player, balance: Balance): StatSheet {
        val equipment = player.equipment
        return ARMOR_SLOTS.map { slot ->
            val item = equipment.getItem(slot)
            Gear.read(item)?.takeIf { it.def.slot.isArmor }?.stats ?: StatSheet.of(
                Stat.DEFENSE to DamageMath.armorDefense(
                    added(item, Attribute.ARMOR, slot),
                    added(item, Attribute.ARMOR_TOUGHNESS, slot),
                    balance,
                )
            )
        }.fold(StatSheet.EMPTY, StatSheet::plus)
    }

    /**
     * The held weapon's stats if it is gear, or else the lens's worth for every
     * point of attack damage it adds to the bare hand; and Magic Find for its
     * Looting either way.
     */
    private fun weapon(player: Player, balance: Balance): StatSheet {
        val item = player.inventory.itemInMainHand
        val looting = StatSheet.of(
            Stat.MAGIC_FIND to item.getEnchantmentLevel(Enchantment.LOOTING) * balance.vanilla.lootingMagicFind,
        )
        val gear = Gear.read(item)?.takeIf { !it.def.slot.isArmor }?.stats
        return looting + (gear ?: StatSheet.of(Stat.DAMAGE to attackDamage(item) * balance.lens))
    }

    /** What [item] adds to the bare hand's attack damage, its material's default included. */
    fun attackDamage(item: ItemStack?): Double = added(item, Attribute.ATTACK_DAMAGE, EquipmentSlot.HAND)

    private fun effects(player: Player, balance: Balance): StatSheet {
        val strength = player.getPotionEffect(PotionEffectType.STRENGTH)?.let { it.amplifier + 1 } ?: 0
        val weakness = player.getPotionEffect(PotionEffectType.WEAKNESS)?.let { it.amplifier + 1 } ?: 0
        return StatSheet.of(
            Stat.STRENGTH to strength * balance.effects.strengthPerLevel - weakness * balance.effects.weaknessPerLevel
        )
    }

    /**
     * What [item] adds to [attribute] when it is in [slot]: the sum of its
     * flat modifiers for that slot, its material's defaults included.
     */
    private fun added(item: ItemStack?, attribute: Attribute, slot: EquipmentSlot): Double {
        if (item == null || item.isEmpty) return 0.0
        val modifiers = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS) ?: return 0.0
        return modifiers.modifiers()
            .filter { entry ->
                entry.attribute() == attribute &&
                        entry.modifier().operation == AttributeModifier.Operation.ADD_NUMBER &&
                        entry.modifier().slotGroup.test(slot)
            }
            .sumOf { it.modifier().amount }
    }
}
