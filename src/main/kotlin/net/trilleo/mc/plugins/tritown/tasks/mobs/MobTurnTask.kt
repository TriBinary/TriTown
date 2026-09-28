package net.trilleo.mc.plugins.tritown.tasks.mobs

import net.trilleo.mc.plugins.tritown.mobs.AbilityEffects
import net.trilleo.mc.plugins.tritown.mobs.ActiveMobs
import net.trilleo.mc.plugins.tritown.mobs.AffixEffects
import net.trilleo.mc.plugins.tritown.mobs.MobProfiles
import net.trilleo.mc.plugins.tritown.registration.PluginTask

/**
 * Gives every active mob — ranked, custom or with an affix — that has a
 * player nearby its turn twice a second: the particles that show its affixes,
 * a blinker closing the distance, and a custom mob's abilities. One out of
 * everyone's sight does nothing, so a far-off champion costs nothing.
 */
class MobTurnTask : PluginTask(delay = 20L, period = 10L) {

    override fun run() {
        for (mob in ActiveMobs.all()) {
            if (!mob.isValid) {
                ActiveMobs.untrack(mob.uniqueId)
                continue
            }
            if (mob.world.getNearbyPlayers(mob.location, AWAKE_RANGE).isEmpty()) continue
            val profile = MobProfiles.of(mob)
            AffixEffects.tick(mob, profile)
            AbilityEffects.tick(mob, profile)
        }
    }

    private companion object {
        const val AWAKE_RANGE = 32.0
    }
}
