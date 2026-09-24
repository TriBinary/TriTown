package net.trilleo.mc.plugins.tritown.tasks.combat

import net.trilleo.mc.plugins.tritown.combat.Combat
import net.trilleo.mc.plugins.tritown.combat.CombatHud
import net.trilleo.mc.plugins.tritown.config.CombatSettings
import net.trilleo.mc.plugins.tritown.registration.PluginTask
import org.bukkit.Bukkit
import org.bukkit.GameMode

/**
 * Redraws every fighting player's health and Defense above their hotbar twice
 * a second, often enough that the line never fades between draws.
 */
class CombatHudTask : PluginTask(delay = 20L, period = 10L) {

    override fun run() {
        if (!CombatSettings.isLoaded || !CombatSettings.snapshot.actionBar) return
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.isDead || player.gameMode !in FIGHTING || !Combat.isActive(player.world)) continue
            CombatHud.show(player)
        }
    }

    private companion object {
        val FIGHTING = setOf(GameMode.SURVIVAL, GameMode.ADVENTURE)
    }
}
