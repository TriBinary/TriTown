package net.trilleo.mc.plugins.tritown.tasks.mobs

import net.trilleo.mc.plugins.tritown.mobs.AffixEffects
import net.trilleo.mc.plugins.tritown.mobs.MobProfiles
import net.trilleo.mc.plugins.tritown.mobs.RankedMobs
import net.trilleo.mc.plugins.tritown.registration.PluginTask

/**
 * Gives every ranked mob with a player nearby its turn twice a second: the
 * particles that show its affixes, and a blinker closing the distance. One out
 * of everyone's sight does nothing, so a far-off champion costs nothing.
 */
class AffixTask : PluginTask(delay = 20L, period = 10L) {

    override fun run() {
        for (mob in RankedMobs.all()) {
            if (!mob.isValid) {
                RankedMobs.untrack(mob.uniqueId)
                continue
            }
            if (mob.world.getNearbyPlayers(mob.location, AWAKE_RANGE).isEmpty()) continue
            AffixEffects.tick(mob, MobProfiles.of(mob))
        }
    }

    private companion object {
        const val AWAKE_RANGE = 32.0
    }
}
