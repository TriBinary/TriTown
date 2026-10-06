package net.trilleo.mc.plugins.tritown.commands.gathering

import net.trilleo.mc.plugins.tritown.gathering.GatherManager
import net.trilleo.mc.plugins.tritown.guis.gathering.ResourcesGUI
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Lists the resource regions of the player's town, and what they have gathered.
 *
 * No permission, like `/tritown menu`: what a player sees is already limited
 * to their own town.
 */
class ResourcesCommand : PluginCommand(
    name = "resources",
    description = "See your town's resource regions",
    usage = "/tritown resources",
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.resources.players-only"))
            return true
        }
        if (!GatherManager.isReady) {
            player.sendPrefixed(player.tr("command.gather.unavailable"))
            return true
        }
        if (!ResourcesGUI.hasRegions(player)) {
            player.sendPrefixed(player.tr("command.resources.none"))
            return true
        }

        ResourcesGUI.show(player)
        return true
    }
}
