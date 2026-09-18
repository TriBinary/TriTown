package net.trilleo.mc.plugins.tritown.tasks.economy

import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.registration.PluginTask

/**
 * Writes changed accounts to disk on an interval, off the main thread.
 *
 * The interval bounds how much a hard crash can cost, since `onDisable` does
 * not run when a server is killed. Balances are still written immediately for
 * anything that should not wait, such as an administrator changing one.
 */
class EconomyFlushTask : PluginTask(
    delay = flushIntervalTicks(),
    period = flushIntervalTicks(),
    async = true,
) {
    override fun run() {
        EconomyService.flush()
    }
}

/**
 * The configured flush interval in ticks.
 *
 * Read at construction, which is safe because the settings are loaded during
 * `onLoad`, well before the task registrar builds this task.
 */
private fun flushIntervalTicks(): Long {
    val seconds = if (EconomySettings.isLoaded) EconomySettings.snapshot.flushIntervalSeconds else 60L
    return seconds * 20L
}
