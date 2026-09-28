package net.trilleo.mc.plugins.tritown.commands.adventure

import net.trilleo.mc.plugins.tritown.combat.Combat
import net.trilleo.mc.plugins.tritown.guis.forge.ForgeGUI
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Opens the Forge. It declares no permission, the way `/tritown stats` does:
 * everyone can craft, and whether combat runs at all is `combat.enabled`.
 */
class ForgeCommand : PluginCommand(
    name = "forge",
    description = "Craft and improve gear",
    usage = "/tritown forge",
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.forge.players-only"))
            return true
        }
        if (!Combat.isActive(player.world)) {
            player.sendPrefixed(player.tr("command.forge.unavailable"))
            return true
        }
        ForgeGUI.show(player)
        return true
    }
}
