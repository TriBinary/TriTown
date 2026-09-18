package net.trilleo.mc.plugins.tritown.scoreboard.placeholders

import com.palmergames.bukkit.towny.TownyAPI
import com.palmergames.bukkit.towny.TownySettings
import com.palmergames.bukkit.towny.`object`.Town
import net.trilleo.mc.plugins.tritown.scoreboard.PlaceholderEngine
import net.trilleo.mc.plugins.tritown.scoreboard.PlayerContext
import net.trilleo.mc.plugins.tritown.utils.TownyUtil
import net.trilleo.mc.plugins.tritown.utils.tr

/** Markers describing the viewer's own town, and the clock the town runs on. */
object TownPlaceholders {

    fun register() {
        town("town") { TownyUtil.name(it.name) }
        town("town_level") { TownySettings.getTownLevelNumber(it).toString() }
        town("town_residents") { it.numResidents.toString() }
        town("town_online") { TownyAPI.getInstance().getOnlinePlayersInTown(it).size.toString() }
        town("town_claims") { it.numTownBlocks.toString() }
        town("town_claims_max") { TownyUtil.text(it.maxTownBlocksAsAString) }
        town("town_claims_free") { it.availableTownBlocks().toString() }
        town("town_bank") { TownyUtil.balance(it) }
        town("town_upkeep") { TownyUtil.money(TownyUtil.townUpkeep(it)) }
        town("town_mayor") { TownyUtil.name(it.mayor.name) }

        PlaceholderEngine.register("town_warning") { context ->
            context.town?.let { warning(context, it) } ?: context.none()
        }

        PlaceholderEngine.register("newday") { context ->
            TownyUtil.duration(context.player, TownyUtil.secondsUntilNewDay())
        }
    }

    /** Registers a marker that needs a town, showing the "none" text for a player without one. */
    private fun town(name: String, value: (Town) -> String) {
        PlaceholderEngine.register(name) { context -> context.town?.let(value) ?: context.none() }
    }

    /**
     * The most pressing thing wrong with [town], worst first.
     *
     * Only one is ever shown: a ruined town is also overclaimed and cannot pay
     * its upkeep, and a sidebar row listing all three would say nothing useful.
     */
    private fun warning(context: PlayerContext, town: Town): String {
        val player = context.player
        return when {
            town.isRuined -> player.tr("scoreboard.warning.ruined")
            town.isBankrupt -> player.tr("scoreboard.warning.bankrupt")
            TownyUtil.cannotAffordUpkeep(town) -> player.tr("scoreboard.warning.upkeep-short")
            town.isOverClaimed -> player.tr("scoreboard.warning.overclaimed")
            town.isConquered -> player.tr("scoreboard.warning.conquered")
            else -> player.tr("scoreboard.warning.none")
        }
    }
}
