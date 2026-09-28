package net.trilleo.mc.plugins.tritown.scoreboard.placeholders

import net.trilleo.mc.plugins.tritown.combat.*
import net.trilleo.mc.plugins.tritown.mobs.MobZones
import net.trilleo.mc.plugins.tritown.scoreboard.PlaceholderEngine
import net.trilleo.mc.plugins.tritown.scoreboard.PlayerContext
import net.trilleo.mc.plugins.tritown.utils.tr

/** The player's RPG numbers, and how dangerous the ground they stand on is. */
object CombatPlaceholders {

    fun register() {
        PlaceholderEngine.register("health") { context ->
            active(context) { CombatFormat.health(CombatHealth.current(context.player)) }
        }

        PlaceholderEngine.register("max_health") { context ->
            active(context) { CombatFormat.number(CombatHealth.max(context.player)) }
        }

        PlaceholderEngine.register("defense") { context ->
            active(context) { CombatFormat.number(PlayerStats.sheet(context.player)[Stat.DEFENSE]) }
        }

        PlaceholderEngine.register("mob_level") { context ->
            active(context) { MobZones.levelAt(context.player.location).toString() }
        }

        // A whole translated fragment rather than a bare value, so a line can
        // carry it and read naturally when combat is off and it is empty.
        PlaceholderEngine.register("danger") { context ->
            if (Combat.isActive(context.player.world)) {
                context.player.tr("scoreboard.danger", "level" to MobZones.levelAt(context.player.location))
            } else ""
        }
    }

    private fun active(context: PlayerContext, value: () -> String): String =
        if (Combat.isActive(context.player.world)) value() else context.none()
}
