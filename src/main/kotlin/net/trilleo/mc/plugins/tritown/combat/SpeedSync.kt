package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.config.CombatSettings
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player

/**
 * Turns a player's Speed stat into movement.
 *
 * Speed is the one stat that shows between players — movement cannot tell who
 * is chasing whom — so it is capped at `combat.speed-cap`, short of a Speed II
 * potion. It is a transient modifier, never saved with the player, so nothing
 * of it outlives TriTown.
 */
object SpeedSync {

    private val KEY = NamespacedKey("tritown", "speed")

    fun sync(player: Player) {
        val attribute = player.getAttribute(Attribute.MOVEMENT_SPEED) ?: return
        val wanted = if (Combat.isActive(player.world)) {
            PlayerStats.sheet(player)[Stat.SPEED].coerceIn(0.0, CombatSettings.snapshot.speedCap) / 100.0
        } else 0.0

        val current = attribute.getModifier(KEY)
        if (current?.amount == wanted || (current == null && wanted == 0.0)) return
        current?.let(attribute::removeModifier)
        if (wanted > 0.0) {
            attribute.addTransientModifier(AttributeModifier(KEY, wanted, AttributeModifier.Operation.ADD_SCALAR))
        }
    }

    fun clear(player: Player) {
        player.getAttribute(Attribute.MOVEMENT_SPEED)?.getModifier(KEY)?.let {
            player.getAttribute(Attribute.MOVEMENT_SPEED)?.removeModifier(it)
        }
    }
}
