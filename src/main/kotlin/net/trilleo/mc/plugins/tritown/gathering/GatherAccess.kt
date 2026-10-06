package net.trilleo.mc.plugins.tritown.gathering

import com.palmergames.bukkit.towny.TownyAPI
import net.trilleo.mc.plugins.tritown.protection.Protection
import net.trilleo.mc.plugins.tritown.utils.TownyUtil
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Who may gather in a region: the residents of the town that owns it, on
 * ground the town still owns.
 *
 * Towny is asked afresh every time, so a player who leaves the town, or a claim
 * the town gives up, stops gathering on the spot.
 */
object GatherAccess {

    /** Gathering in any region, whatever town the player belongs to. */
    const val BYPASS_PERMISSION = "tritown.gather.bypass"

    /** Why a player may not gather somewhere. */
    enum class Refusal { CLOSED, NOT_RESIDENT, UNCLAIMED }

    /** Why [player] may not gather in [region] at [at], or `null` when they may. */
    fun refusal(player: Player, region: ResourceRegion, at: Location): Refusal? {
        if (!region.enabled) return Refusal.CLOSED
        if (TownyAPI.getInstance().getTown(at)?.uuid != region.townId) return Refusal.UNCLAIMED
        if (player.hasPermission(BYPASS_PERMISSION) || isResident(player, region)) return null
        return Refusal.NOT_RESIDENT
    }

    fun isResident(player: Player, region: ResourceRegion): Boolean =
        TownyAPI.getInstance().getResident(player)?.townOrNull?.uuid == region.townId

    /** The name of the town that owns [region], safe inside MiniMessage. */
    fun townName(player: Player, region: ResourceRegion): String =
        TownyAPI.getInstance().getTown(region.townId)?.name?.let(TownyUtil::name) ?: player.tr("common.unknown")

    /** Tells [player], in the action bar, why they cannot gather here. */
    fun hint(player: Player, region: ResourceRegion, refusal: Refusal) {
        Protection.hint(player, message(player, region, refusal))
    }

    fun message(player: Player, region: ResourceRegion, refusal: Refusal): String = when (refusal) {
        Refusal.CLOSED -> player.tr("gathering.refuse.closed")
        Refusal.NOT_RESIDENT -> player.tr("gathering.refuse.resident", "town" to townName(player, region))
        Refusal.UNCLAIMED -> player.tr("gathering.refuse.unclaimed")
    }
}
