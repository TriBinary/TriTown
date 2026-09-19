package net.trilleo.mc.plugins.tritown.guis.admin

import net.trilleo.mc.plugins.tritown.enums.StatsWindow
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * How far back each administrator is looking.
 *
 * Held here rather than in one menu because the overview and the breakdown show
 * the same figures at different depths: switching to the last week in one and
 * then opening the other should not quietly go back to the last day.
 *
 * The choice is deliberately not persisted — it is a way of looking at the
 * panel, not a setting — and is dropped when the last of the panel's menus is
 * closed.
 */
object PanelState {

    private val windows = ConcurrentHashMap<UUID, StatsWindow>()

    /** The window [player] is looking at. */
    fun window(player: Player): StatsWindow = windows[player.uniqueId] ?: StatsWindow.DAY

    /** Moves [player] to the next window, or the previous one when [forward] is false. */
    fun cycle(player: Player, forward: Boolean): StatsWindow {
        val next = if (forward) window(player).next() else window(player).previous()
        windows[player.uniqueId] = next
        return next
    }

    /** Forgets what [player] was looking at. */
    fun forget(player: Player) {
        windows.remove(player.uniqueId)
    }
}
