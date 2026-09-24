package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.entity.Player

/** A player's RPG health and Defense above their hotbar. */
object CombatHud {

    fun show(player: Player) {
        val text = player.tr(
            "combat.hud.bar",
            "health" to CombatFormat.health(CombatHealth.current(player)),
            "max" to CombatFormat.number(CombatHealth.max(player)),
            "defense" to CombatFormat.number(PlayerStats.sheet(player)[Stat.DEFENSE]),
        )
        player.sendActionBar(ComponentUtil.parse(text))
    }
}
