package net.trilleo.mc.plugins.tritown.commands.adventure

import net.trilleo.mc.plugins.tritown.combat.Combat
import net.trilleo.mc.plugins.tritown.guis.adventure.StatsGUI
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Opens a player's combat stats: your own, or anyone else's who is online.
 *
 * It declares no permission, the way `/tritown storage` does: everyone has
 * stats, and whether combat runs at all is `combat.enabled`.
 */
class StatsCommand : PluginCommand(
    name = "stats",
    description = "See your combat stats",
    usage = "/tritown stats [player]",
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.stats.players-only"))
            return true
        }
        if (!Combat.isActive(player.world)) {
            player.sendPrefixed(player.tr("command.stats.unavailable"))
            return true
        }

        val target = args.firstOrNull()?.let { name ->
            Bukkit.getPlayerExact(name)?.takeIf(player::canSee) ?: run {
                player.sendPrefixed(player.tr("command.stats.not-found", "name" to ComponentUtil.escape(name)))
                return true
            }
        } ?: player
        StatsGUI.show(player, target)
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size != 1) return emptyList()
        val viewer = sender as? Player
        return Bukkit.getOnlinePlayers()
            .filter { viewer == null || viewer.canSee(it) }
            .map { it.name }
            .filter { it.startsWith(args[0], ignoreCase = true) }
    }
}
