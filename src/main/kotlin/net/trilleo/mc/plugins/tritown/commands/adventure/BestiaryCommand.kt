package net.trilleo.mc.plugins.tritown.commands.adventure

import net.trilleo.mc.plugins.tritown.guis.bestiary.BestiaryGUI
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Opens the bestiary. Like `/tritown forge`, it declares no permission: everyone keeps one. */
class BestiaryCommand : PluginCommand(
    name = "bestiary",
    description = "See the server's own mobs you have met",
    usage = "/tritown bestiary",
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.bestiary.players-only"))
            return true
        }
        BestiaryGUI.show(player)
        return true
    }
}
