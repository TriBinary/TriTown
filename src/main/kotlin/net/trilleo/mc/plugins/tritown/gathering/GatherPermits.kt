package net.trilleo.mc.plugins.tritown.gathering

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The one gathering action each player has been cleared for this tick.
 *
 * A town can deny its own residents the right to build, destroy and use, and
 * gathering has to work anyway, without handing those rights out. So TriTown
 * decides first, at `LOWEST`, and leaves a permit for exactly that player,
 * that block and that tick. When Towny then asks its own permission events,
 * [net.trilleo.mc.plugins.tritown.listeners.gathering.GatherTownyListener]
 * lifts the refusal only where a permit matches. Every other action in the
 * town — the next block over, the same block a tick later — Towny judges as
 * it always would.
 */
object GatherPermits {

    private data class Permit(val key: BlockKey, val tick: Int)

    private val permits = ConcurrentHashMap<UUID, Permit>()

    fun grant(player: Player, at: Location) {
        permits[player.uniqueId] = Permit(BlockKey.of(at), Bukkit.getCurrentTick())
    }

    /** Whether [player] was cleared to act at [at] this tick. */
    fun isGranted(player: Player, at: Location): Boolean {
        val permit = permits[player.uniqueId] ?: return false
        if (permit.tick != Bukkit.getCurrentTick()) {
            permits.remove(player.uniqueId, permit)
            return false
        }
        return permit.key == BlockKey.of(at)
    }

    fun forget(player: Player) {
        permits.remove(player.uniqueId)
    }
}
