package net.trilleo.mc.plugins.tritown.tasks.shop

import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.registration.PluginTask
import net.trilleo.mc.plugins.tritown.shops.ShopManager

/**
 * Writes shop stock and statistics to disk on an interval, off the main thread.
 *
 * Definitions do not wait for this — an edit is written the moment it is made —
 * so the interval only bounds how many purchase counters a hard crash can cost.
 * Rewriting the file on every purchase would be the alternative, and a busy
 * shop would then write it hundreds of times a minute for no gain.
 */
class ShopSaveTask : PluginTask(
    delay = saveIntervalTicks(),
    period = saveIntervalTicks(),
    async = true,
) {
    override fun run() {
        ShopManager.flush()
    }
}

/**
 * The configured save interval in ticks.
 *
 * Read at construction, which is safe because the shop settings are loaded in
 * `onEnable` before the task registrar runs.
 */
private fun saveIntervalTicks(): Long {
    val seconds = if (ShopSettings.isLoaded) ShopSettings.snapshot.saveIntervalSeconds else 60L
    return seconds * 20L
}
