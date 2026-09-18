package net.trilleo.mc.plugins.tritown.scoreboard.placeholders

import com.palmergames.bukkit.towny.TownySettings
import com.palmergames.bukkit.towny.`object`.Nation
import net.trilleo.mc.plugins.tritown.scoreboard.PlaceholderEngine
import net.trilleo.mc.plugins.tritown.utils.TownyUtil

/** Markers describing the viewer's own nation. */
object NationPlaceholders {

    fun register() {
        nation("nation") { TownyUtil.name(it.name) }
        nation("nation_level") { TownySettings.getNationLevelNumber(it).toString() }
        nation("nation_towns") { it.numTowns.toString() }
        nation("nation_residents") { it.numResidents.toString() }
        nation("nation_bank") { TownyUtil.balance(it) }
        nation("nation_upkeep") { TownyUtil.money(TownyUtil.nationUpkeep(it)) }
        nation("nation_king") { TownyUtil.name(it.king.name) }
        nation("nation_capital") { TownyUtil.name(it.capital.name) }
    }

    /** Registers a marker that needs a nation, showing the "none" text for a player without one. */
    private fun nation(name: String, value: (Nation) -> String) {
        PlaceholderEngine.register(name) { context -> context.nation?.let(value) ?: context.none() }
    }
}
