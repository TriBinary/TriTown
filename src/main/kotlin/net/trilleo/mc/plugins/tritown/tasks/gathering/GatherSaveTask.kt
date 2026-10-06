package net.trilleo.mc.plugins.tritown.tasks.gathering

import net.trilleo.mc.plugins.tritown.config.GatheringSettings
import net.trilleo.mc.plugins.tritown.gathering.GatherManager
import net.trilleo.mc.plugins.tritown.registration.PluginTask

/**
 * Writes the blocks harvested in resource regions to disk on an interval, off
 * the main thread. Regions themselves are written the moment they are edited.
 */
class GatherSaveTask : PluginTask(
    delay = saveIntervalTicks(),
    period = saveIntervalTicks(),
    async = true,
) {
    override fun run() {
        GatherManager.flush()
    }
}

private fun saveIntervalTicks(): Long {
    val seconds = if (GatheringSettings.isLoaded) GatheringSettings.snapshot.saveSeconds else 60L
    return seconds * 20L
}
