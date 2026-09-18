package net.trilleo.mc.plugins.tritown.scoreboard

import com.palmergames.bukkit.towny.huds.HUDImplementer
import com.palmergames.bukkit.towny.huds.providers.HUD

/**
 * TriTown's sidebar, described to Towny.
 *
 * Towny already owns a sidebar renderer for its own plot and map HUDs, and it
 * accepts other plugins' HUDs through `HUDManager.addHUD`. Registering there
 * rather than driving `org.bukkit.scoreboard` directly means Towny's own
 * mutual exclusion applies: turning on `/towny plot perm hud` turns TriTown's
 * sidebar off and the other way round, instead of the two fighting over the
 * one sidebar slot a player has.
 *
 * Both consumers ignore the plot Towny passes and re-read the player's
 * situation from scratch, because a plot change is only one of the things that
 * can alter what the sidebar says.
 */
class TriTownHud : HUDImplementer {

    private val hud = HUD(
        NAME,
        OBJECTIVE,
        { player -> ScoreboardService.render(player) },
        { player, _ -> ScoreboardService.render(player) },
    )

    override fun getHUD(): HUD = hud

    companion object {

        /** The name TriTown's HUD is registered under with Towny. */
        const val NAME: String = "tritown"

        /**
         * The scoreboard objective name.
         *
         * Kept under sixteen characters, which is the limit Minecraft imposes on
         * an objective name, and distinct from Towny's `PLOT_PERM_OBJ` and
         * `MAP_HUD_OBJ` so the takeover check can tell the boards apart.
         */
        const val OBJECTIVE: String = "TRITOWN_SB_OBJ"
    }
}
