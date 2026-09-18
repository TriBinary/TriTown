package net.trilleo.mc.plugins.tritown.tasks.scoreboard

import net.trilleo.mc.plugins.tritown.registration.PluginTask
import net.trilleo.mc.plugins.tritown.scoreboard.ScoreboardService

/**
 * Drives the sidebar's clock.
 *
 * The task itself runs every tick and decides internally how often to redraw,
 * rather than being scheduled at the configured interval. A [PluginTask]'s
 * period is fixed when it is constructed and tasks are not re-registered on
 * `/tritown reload`, so a task scheduled at the configured rate would be stuck
 * at whatever the rate was at startup. Ticking every time and counting instead
 * lets `scoreboard.refresh-interval` take effect on a reload.
 *
 * The delay covers the first second of a start-up, while Towny is still
 * settling and no player has a sidebar yet.
 */
class ScoreboardRefreshTask : PluginTask(delay = 20L, period = 1L) {

    override fun run() {
        ScoreboardService.tick()
    }
}
