package net.trilleo.mc.plugins.tritown.scoreboard.placeholders

import com.palmergames.bukkit.towny.TownyAPI
import com.palmergames.bukkit.towny.`object`.TownBlock
import com.palmergames.bukkit.towny.`object`.WorldCoord
import net.trilleo.mc.plugins.tritown.scoreboard.PlaceholderEngine
import net.trilleo.mc.plugins.tritown.utils.TownyUtil

/** Markers describing the claim the viewer is standing in, which may belong to anyone. */
object PlotPlaceholders {

    fun register() {
        PlaceholderEngine.register("plot_town") { context ->
            context.plotTown?.let { TownyUtil.name(it.name) } ?: context.none()
        }

        PlaceholderEngine.register("plot_nation") { context ->
            context.plotTown?.nationOrNull?.let { TownyUtil.name(it.name) } ?: context.none()
        }

        PlaceholderEngine.register("plot_owner") { context ->
            context.plotOwner?.let { TownyUtil.name(it.name) } ?: context.none()
        }

        plot("plot_type") { TownyUtil.name(it.type.formattedName) }

        PlaceholderEngine.register("plot_price") { context ->
            val block = context.townBlock
            if (block != null && block.isForSale) TownyUtil.money(block.plotPrice) else context.none()
        }

        PlaceholderEngine.register("plot_coords") { context ->
            val coord = WorldCoord.parseWorldCoord(context.player)
            "${coord.x}, ${coord.z}"
        }

        // Read from the location rather than the town block, because Towny folds
        // world-level forces and the wilderness defaults into these two.
        PlaceholderEngine.register("plot_pvp") { context ->
            TownyUtil.onOff(context.player, TownyAPI.getInstance().isPVP(context.player.location))
        }

        PlaceholderEngine.register("plot_mobs") { context ->
            TownyUtil.onOff(context.player, TownyAPI.getInstance().areMobsEnabled(context.player.location))
        }
    }

    /** Registers a marker that needs a claim, showing the "none" text out in the wilderness. */
    private fun plot(name: String, value: (TownBlock) -> String) {
        PlaceholderEngine.register(name) { context -> context.townBlock?.let(value) ?: context.none() }
    }
}
