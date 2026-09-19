package net.trilleo.mc.plugins.tritown.tasks.economy

import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.BaltopCache
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyPulse
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.registration.PluginTask

/**
 * Writes changed accounts to disk on an interval, measures the economy and
 * rebuilds the balance leaderboard, all off the main thread.
 *
 * The interval bounds how much a hard crash can cost, since `onDisable` does
 * not run when a server is killed. Balances are still written immediately for
 * anything that should not wait, such as an administrator changing one.
 *
 * The measurement and the leaderboard are done here because this task is
 * already walking every account, which keeps both the sorting and the
 * statistics off the main thread entirely.
 */
class EconomyFlushTask : PluginTask(
    delay = flushIntervalTicks(),
    period = flushIntervalTicks(),
    async = true,
) {
    override fun run() {
        if (!EconomyService.isReady) return

        // Measured before the flush, so the sample this round takes is written
        // in the same pass rather than waiting for the next one.
        if (CurrencyRegistry.isLoaded) EconomyPulse.sample(EconomyService.ledger, CurrencyRegistry.primary)

        EconomyService.flush()

        if (CurrencyRegistry.isLoaded) {
            BaltopCache.rebuild(
                ledger = EconomyService.ledger,
                currency = CurrencyRegistry.primary,
                includeGovernments = EconomySettings.snapshot.baltopIncludeTowns,
            )
        }
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
