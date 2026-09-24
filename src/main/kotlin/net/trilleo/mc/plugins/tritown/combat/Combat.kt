package net.trilleo.mc.plugins.tritown.combat

import net.trilleo.mc.plugins.tritown.config.CombatSettings
import org.bukkit.World

object Combat {

    /** Whether hits in [world] are worked out in RPG numbers. Every combat handler asks this first. */
    fun isActive(world: World): Boolean {
        if (!CombatSettings.isLoaded) return false
        val settings = CombatSettings.snapshot
        return settings.enabled && world.name.lowercase() !in settings.disabledWorlds
    }
}
