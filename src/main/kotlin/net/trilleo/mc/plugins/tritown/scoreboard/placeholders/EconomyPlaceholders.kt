package net.trilleo.mc.plugins.tritown.scoreboard.placeholders

import net.trilleo.mc.plugins.tritown.economy.BaltopCache
import net.trilleo.mc.plugins.tritown.scoreboard.PlaceholderEngine
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.TownyUtil

/** Markers describing the viewer's own wallet. */
object EconomyPlaceholders {

    fun register() {
        PlaceholderEngine.register("balance") { context ->
            if (EconomyUtil.isAvailable) TownyUtil.money(EconomyUtil.balance(context.player)) else context.none()
        }

        PlaceholderEngine.register("balance_raw") { context ->
            if (EconomyUtil.isAvailable) EconomyUtil.balance(context.player).toString() else context.none()
        }

        // Read straight off the cached leaderboard, which the flush task rebuilds.
        // Asking for a fresh ranking here would sort every account on the main
        // thread once per player per refresh.
        PlaceholderEngine.register("baltop_rank") { context ->
            val position = BaltopCache.current.entries.indexOfFirst { it.uuid == context.player.uniqueId }
            if (position >= 0) (position + 1).toString() else context.none()
        }
    }
}
