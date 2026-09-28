package net.trilleo.mc.plugins.tritown.mobs

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.content.Costume
import net.trilleo.mc.plugins.tritown.content.CostumeSlot
import net.trilleo.mc.plugins.tritown.content.MobKindDef
import net.trilleo.mc.plugins.tritown.gear.Gear
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

/**
 * The custom mobs of `bestiary.yml` as they are in the world: which one a mob
 * is, and dressing it as one.
 *
 * **A costume is only a look.** Every piece a custom mob wears is stripped of
 * its attribute modifiers, so a netherite costume gives it no vanilla armor
 * and a sword no attack damage — its [MobKindDef.defense] and
 * [MobKindDef.damage] are what it has. Every piece has no chance to drop, and a
 * custom mob picks nothing up, so gear only ever leaves a mob through
 * `MobLoot`.
 */
object MobKinds {

    private val SCALE = NamespacedKey("tritown", "kind-scale")
    private val SPEED = NamespacedKey("tritown", "kind-speed")
    private val KNOCKBACK = NamespacedKey("tritown", "kind-knockback")

    /** What [profile]'s kind is, or `null` for an ordinary mob, or one whose kind has left `bestiary.yml`. */
    fun def(profile: MobProfile): MobKindDef? = profile.kind?.let { ContentRegistry.bestiary.kinds[it] }

    /** Dresses a mob that has just become [def]: its costume, and the traits [refresh] keeps. */
    fun dress(mob: LivingEntity, def: MobKindDef) {
        mob.canPickupItems = false
        val equipment = mob.equipment
        if (equipment != null) {
            def.equipment.forEach { (slot, costume) ->
                val item = costume(costume) ?: return@forEach
                equipment.setItem(slot(slot), item)
                equipment.setDropChance(slot(slot), 0f)
            }
        }
        refresh(mob)
    }

    /**
     * Brings the traits [mob]'s kind gives it — size, speed, footing — up to
     * what `bestiary.yml` says now. Run as it is dressed and each time it
     * loads, so an edit reaches a mob the next time it is seen. A mob with no
     * kind loses any it had.
     */
    fun refresh(mob: LivingEntity) {
        val def = def(MobProfiles.of(mob))
        trait(mob, Attribute.SCALE, SCALE, def?.let { it.scale - 1.0 }, AttributeModifier.Operation.ADD_SCALAR)
        trait(
            mob,
            Attribute.MOVEMENT_SPEED,
            SPEED,
            def?.let { it.speed / 100.0 },
            AttributeModifier.Operation.ADD_SCALAR
        )
        trait(
            mob,
            Attribute.KNOCKBACK_RESISTANCE,
            KNOCKBACK,
            def?.let { it.knockback / 100.0 },
            AttributeModifier.Operation.ADD_NUMBER
        )
    }

    private fun trait(
        mob: LivingEntity,
        attribute: Attribute,
        key: NamespacedKey,
        amount: Double?,
        operation: AttributeModifier.Operation,
    ) {
        val instance = mob.getAttribute(attribute) ?: return
        instance.removeModifier(key)
        if (amount != null && amount != 0.0) instance.addModifier(AttributeModifier(key, amount, operation))
    }

    private fun costume(costume: Costume): ItemStack? {
        val stack = when (costume) {
            is Costume.Gear -> ContentRegistry.gear.gear[costume.id]?.let(Gear::costume)
            is Costume.Vanilla -> Material.matchMaterial(costume.material)?.takeIf { it.isItem }?.let(ItemStack::of)
        } ?: return null
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build())
        return stack
    }

    private fun slot(slot: CostumeSlot): EquipmentSlot = when (slot) {
        CostumeSlot.HEAD -> EquipmentSlot.HEAD
        CostumeSlot.CHEST -> EquipmentSlot.CHEST
        CostumeSlot.LEGS -> EquipmentSlot.LEGS
        CostumeSlot.FEET -> EquipmentSlot.FEET
        CostumeSlot.HAND -> EquipmentSlot.HAND
        CostumeSlot.OFF_HAND -> EquipmentSlot.OFF_HAND
    }
}
