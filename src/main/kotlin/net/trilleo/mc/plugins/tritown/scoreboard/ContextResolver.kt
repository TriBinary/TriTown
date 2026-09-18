package net.trilleo.mc.plugins.tritown.scoreboard

import com.palmergames.bukkit.towny.TownyAPI
import org.bukkit.entity.Player

/** Builds the [PlayerContext] a sidebar render works from. */
object ContextResolver {

    /**
     * Reads [player]'s current Towny situation.
     *
     * Every lookup here is an in-memory read of Towny's own objects, which is
     * why the sidebar can afford to do this once per player per refresh instead
     * of caching anything.
     */
    fun resolve(player: Player): PlayerContext {
        val api = TownyAPI.getInstance()
        val resident = api.getResident(player)
        val townBlock = api.getTownBlock(player.location)

        return PlayerContext(
            player = player,
            resident = resident,
            town = resident?.townOrNull,
            nation = resident?.nationOrNull,
            townBlock = townBlock,
            plotTown = townBlock?.townOrNull,
            plotOwner = townBlock?.residentOrNull,
        )
    }
}
