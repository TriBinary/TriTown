package net.trilleo.mc.plugins.tritown.tasks.gathering

import net.trilleo.mc.plugins.tritown.gathering.GatherManager
import net.trilleo.mc.plugins.tritown.gathering.GatherNotices
import net.trilleo.mc.plugins.tritown.gathering.Regrowth
import net.trilleo.mc.plugins.tritown.gathering.Spawners
import net.trilleo.mc.plugins.tritown.registration.PluginTask

/**
 * Once a second: grows harvested nodes back, keeps the spawners stocked, and
 * greets players walking into a region.
 *
 * On the server thread, since all three change the world or talk to players.
 */
class GatherTickTask : PluginTask(delay = 20L, period = 20L) {
    override fun run() {
        if (!GatherManager.isReady) return
        Regrowth.tick()
        Spawners.tick()
        GatherNotices.tick()
    }
}
