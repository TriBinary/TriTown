package net.trilleo.mc.plugins.tritown.gathering

import com.palmergames.bukkit.towny.TownyAPI
import net.trilleo.mc.plugins.tritown.config.GatheringSettings
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import java.util.*

/**
 * Whether a box an administrator drew can be a region: small enough, clear of
 * every other region, and owned — at its four corners and its middle — by a
 * single town, which becomes the region's.
 */
object AreaCheck {

    sealed interface Result {
        data class Ok(val townId: UUID) : Result
        data object NoSelection : Result
        data class TooLarge(val volume: Long, val max: Long) : Result
        data object NotOneTown : Result
        data class Overlaps(val id: String) : Result
    }

    fun check(area: Cuboid?, except: String? = null): Result {
        area ?: return Result.NoSelection
        val world = area.bukkitWorld() ?: return Result.NoSelection

        val max = GatheringSettings.snapshot.maxVolume
        if (area.volume > max) return Result.TooLarge(area.volume, max)

        GatherManager.overlapping(area, except)?.let { return Result.Overlaps(it.id) }

        val towns = area.footprint(world).map { TownyAPI.getInstance().getTown(it)?.uuid }.toSet()
        val town = towns.singleOrNull() ?: return Result.NotOneTown
        return Result.Ok(town)
    }

    fun message(sender: CommandSender, result: Result): String = when (result) {
        is Result.Ok -> ""
        Result.NoSelection -> sender.tr("gathering.area.no-selection")
        is Result.TooLarge -> sender.tr("gathering.area.too-large", "volume" to result.volume, "max" to result.max)
        Result.NotOneTown -> sender.tr("gathering.area.not-town")
        is Result.Overlaps -> sender.tr("gathering.area.overlaps", "id" to result.id)
    }
}
