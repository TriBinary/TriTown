package net.trilleo.mc.plugins.tritown.combat

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Every online player's [StatSheet], built by [StatSources] and kept until
 * something changes it.
 *
 * `StatListener` drops a sheet when the player's equipment or effects change.
 * A sheet also expires after [MAX_AGE] ticks regardless, which catches what no
 * event reports — a plugin swapping an item in, a stack landing in the held
 * slot — at the price of one rebuild a second.
 */
object PlayerStats {

    private const val MAX_AGE = 20

    private class Cached(val sheet: StatSheet, val tick: Int)

    private val sheets = ConcurrentHashMap<UUID, Cached>()

    fun sheet(player: Player): StatSheet {
        val now = Bukkit.getCurrentTick()
        sheets[player.uniqueId]?.takeIf { now - it.tick < MAX_AGE }?.let { return it.sheet }
        return StatSources.total(player).also { sheets[player.uniqueId] = Cached(it, now) }
    }

    fun invalidate(player: Player) {
        sheets.remove(player.uniqueId)
    }

    /** Drops every sheet, after a reload may have changed what the sources are worth. */
    fun invalidateAll() {
        sheets.clear()
    }
}
