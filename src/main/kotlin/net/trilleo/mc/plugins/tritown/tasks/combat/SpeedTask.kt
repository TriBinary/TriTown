package net.trilleo.mc.plugins.tritown.tasks.combat

import net.trilleo.mc.plugins.tritown.combat.SpeedSync
import net.trilleo.mc.plugins.tritown.config.CombatSettings
import net.trilleo.mc.plugins.tritown.registration.PluginTask
import org.bukkit.Bukkit

/** Keeps every player's movement in step with their Speed stat, twice a second. */
class SpeedTask : PluginTask(delay = 20L, period = 10L) {

    override fun run() {
        if (!CombatSettings.isLoaded) return
        Bukkit.getOnlinePlayers().forEach(SpeedSync::sync)
    }
}
