package net.trilleo.mc.plugins.tritown.scoreboard

import com.palmergames.bukkit.towny.Towny
import com.palmergames.bukkit.towny.huds.HUDManager
import com.palmergames.bukkit.towny.huds.providers.FoliaHUD
import com.palmergames.bukkit.towny.huds.providers.PaperHUD
import com.palmergames.bukkit.towny.huds.providers.ServerHUD
import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.config.ScoreboardSettings
import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import net.trilleo.mc.plugins.tritown.scoreboard.placeholders.EconomyPlaceholders
import net.trilleo.mc.plugins.tritown.scoreboard.placeholders.NationPlaceholders
import net.trilleo.mc.plugins.tritown.scoreboard.placeholders.PlotPlaceholders
import net.trilleo.mc.plugins.tritown.scoreboard.placeholders.TownPlaceholders
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Level

/**
 * Drives the sidebar: who can see one, which board they get, and when it is
 * redrawn.
 *
 * Everything here runs on the main thread. Towny's objects and the scoreboard
 * renderer behind [ServerHUD] are both main-thread only, and every value a
 * board shows is an in-memory read, so there is nothing worth moving off it.
 *
 * Renders are diffed before they are sent. A sidebar that has not changed is
 * never pushed again, which is what stops an idle board flickering and keeps
 * the packet cost proportional to what actually moved.
 */
object ScoreboardService {

    /** Where a player's own on/off choice is kept between sessions. */
    private const val TOGGLE_KEY = "scoreboard.enabled"

    /**
     * The last sidebar sent to a player, text and parsed components alike.
     *
     * The components are kept so that a redraw only re-parses the lines whose
     * text actually changed — when a balance ticks over, the dozen lines around
     * it are reused rather than run through MiniMessage again.
     */
    private data class Shown(val title: String, val lines: List<String>, val components: List<Component>)

    private var plugin: JavaPlugin? = null
    private var hud: ServerHUD? = null

    private val shown = mutableMapOf<UUID, Shown>()
    private val pinned = mutableMapOf<UUID, String>()

    private var pendingRefresh = false
    private var ticks = 0L
    private var titleFrame = 0

    /** Whether the sidebar is running, which it is not when `scoreboard.enabled` is off. */
    val isRunning: Boolean
        get() = hud != null

    /**
     * Registers the sidebar with Towny and shows it to everyone already online.
     *
     * Towny is a hard dependency and therefore always enabled by now, so its
     * `HUDManager` exists and will accept the registration.
     */
    fun start(plugin: JavaPlugin) {
        this.plugin = plugin
        val settings = ScoreboardSettings.snapshot
        if (!settings.enabled) return

        // Registering with no boards would put an empty sidebar on every screen,
        // showing nothing but the objective's placeholder title.
        if (settings.boards.isEmpty()) {
            plugin.logger.warning(
                "No sidebar boards are configured, so no sidebar will be shown. Check the 'scoreboard.boards' " +
                    "section of config.yml, or set 'scoreboard.enabled' to false to switch the sidebar off."
            )
            return
        }

        registerPlaceholders()

        val implementer = TriTownHud()
        val provider = if (Towny.getPlugin().isFolia) FoliaHUD(implementer) else PaperHUD(implementer)
        HUDManager.addHUD(TriTownHud.NAME, provider)
        hud = provider

        Bukkit.getOnlinePlayers().forEach(::show)
    }

    /** Takes every sidebar down and unregisters from Towny. */
    fun stop() {
        hud?.let { provider ->
            Bukkit.getOnlinePlayers().filter(provider::hasPlayer).forEach(provider::toggleOff)
            HUDManager.removeHUD(TriTownHud.NAME)
        }

        hud = null
        shown.clear()
        pinned.clear()
        PlaceholderEngine.clear()
        plugin = null
    }

    /**
     * Applies a reloaded configuration.
     *
     * Restarting outright is deliberate: boards, placeholders and the enabled
     * flag can all have changed, and rebuilding from nothing is the only way to
     * be sure no stale board survives.
     */
    fun reload() {
        val plugin = this.plugin ?: return
        stop()
        start(plugin)
    }

    /** Whether [player] wants to see the sidebar. */
    fun isEnabledFor(player: Player): Boolean =
        PlayerDataManager.get(player).getBoolean(TOGGLE_KEY, ScoreboardSettings.snapshot.defaultOn)

    /** Records whether [player] wants the sidebar, and shows or hides it to match. */
    fun setEnabledFor(player: Player, enabled: Boolean) {
        PlayerDataManager.get(player).set(TOGGLE_KEY, enabled)
        if (enabled) show(player) else hide(player)
    }

    /** Flips [player]'s choice and returns whether the sidebar is now on. */
    fun toggle(player: Player): Boolean {
        val enabled = !isEnabledFor(player)
        setEnabledFor(player, enabled)
        return enabled
    }

    /**
     * Shows the sidebar to [player] if they want it.
     *
     * Toggling through Towny rather than turning the board on directly is what
     * makes TriTown's sidebar and Towny's own HUDs mutually exclusive, since
     * `toggleHUD` takes every other HUD down first.
     */
    fun show(player: Player) {
        val provider = hud ?: return
        if (provider.hasPlayer(player) || !isEnabledFor(player)) return
        raise(player)
    }

    /** Takes [player]'s sidebar down, restoring whatever board they had before. */
    fun hide(player: Player) {
        val provider = hud ?: return
        shown.remove(player.uniqueId)
        if (provider.hasPlayer(player)) provider.toggleOff(player)
    }

    /** Locks [player] to the board named [id], or clears the lock when [id] is `null`. */
    fun pin(player: Player, id: String?) {
        if (id == null) pinned.remove(player.uniqueId) else pinned[player.uniqueId] = id
        shown.remove(player.uniqueId)
        render(player)
    }

    /** Forgets [player]'s remembered render and pinned board once they are gone. */
    fun forget(player: Player) {
        shown.remove(player.uniqueId)
        pinned.remove(player.uniqueId)
    }

    /** Called once a tick by [net.trilleo.mc.plugins.tritown.tasks.scoreboard.ScoreboardRefreshTask]. */
    fun tick() {
        if (!ScoreboardSettings.isLoaded || hud == null) return
        val settings = ScoreboardSettings.snapshot

        ticks++
        val animated = settings.titleFrames.size > 1 && ticks % settings.titleFrameIntervalTicks == 0L
        if (animated) titleFrame++

        if (!animated && ticks % settings.refreshIntervalTicks != 0L) return
        restoreReleased()
        renderAll()
    }

    /**
     * Gives the sidebar back to players who lost it to one of Towny's own HUDs
     * and have since turned that off.
     *
     * Towny takes every other HUD down when one goes up, and tells nobody when
     * it comes back down again, so noticing is the only way to restore the
     * sidebar without making the player rejoin. A player who turned the sidebar
     * off themselves is left alone, and so is one whose board another plugin
     * has taken over, since that leaves them registered here.
     */
    private fun restoreReleased() {
        val provider = hud ?: return
        Bukkit.getOnlinePlayers().forEach { player ->
            if (!provider.hasPlayer(player) && isEnabledFor(player) && !HUDManager.isUsingTownyHUD(player)) {
                raise(player)
            }
        }
    }

    /**
     * Puts a sidebar up for [player] through Towny, so its HUDs stand aside.
     *
     * The remembered render is dropped first. Towny builds a fresh, empty
     * scoreboard on the way up, and a board whose contents happen to match what
     * the player saw last time would otherwise be diffed away and never drawn.
     */
    private fun raise(player: Player) {
        shown.remove(player.uniqueId)
        HUDManager.toggleHUD(player, TriTownHud.NAME)
    }

    /**
     * Redraws every sidebar on the next tick.
     *
     * A burst of Towny events — a claim, the bank moving, a new day — collapses
     * into one redraw, and calling this from an asynchronous event is safe
     * because the work itself is scheduled onto the main thread.
     */
    fun refreshSoon() {
        val plugin = this.plugin ?: return
        if (pendingRefresh || hud == null) return
        pendingRefresh = true
        Bukkit.getScheduler().runTask(plugin, Runnable {
            pendingRefresh = false
            renderAll()
        })
    }

    /** Rebuilds [player]'s sidebar and sends whatever changed. */
    fun render(player: Player) {
        val provider = hud ?: return
        if (!provider.hasPlayer(player)) return

        val settings = ScoreboardSettings.snapshot
        val context = ContextResolver.resolve(player)
        val board = pinned[player.uniqueId]?.let(settings::board) ?: settings.boardFor(context) ?: return

        push(player, provider, BoardRenderer.render(context, board, settings, titleFrame))
    }

    /** Sends [next] to [player], parsing and pushing only what differs from what they already see. */
    private fun push(player: Player, provider: ServerHUD, next: RenderedBoard) {
        val previous = shown[player.uniqueId]
        if (previous != null && previous.title == next.title && previous.lines == next.lines) return

        val components = ArrayList<Component>(next.lines.size)
        next.lines.forEachIndexed { index, text ->
            val reusable = previous != null &&
                index < previous.components.size &&
                previous.lines[index] == text
            components += if (reusable) previous.components[index] else ComponentUtil.parse(text)
        }

        if (previous == null || previous.title != next.title) {
            provider.setTitle(player.uniqueId, ComponentUtil.parse(next.title))
        }

        // A copy, because the renderer reverses the list it is handed and these
        // components are kept for the next redraw.
        provider.setLines(player.uniqueId, ArrayList(components))
        shown[player.uniqueId] = Shown(next.title, next.lines, components)
    }

    private fun renderAll() {
        val provider = hud ?: return
        Bukkit.getOnlinePlayers()
            .filter(provider::hasPlayer)
            .forEach { player ->
                runCatching { render(player) }.onFailure {
                    plugin?.logger?.log(Level.WARNING, "Failed to render the sidebar for ${player.name}", it)
                }
            }
    }

    private fun registerPlaceholders() {
        PlaceholderEngine.clear()
        TownPlaceholders.register()
        NationPlaceholders.register()
        PlotPlaceholders.register()
        EconomyPlaceholders.register()
    }
}
