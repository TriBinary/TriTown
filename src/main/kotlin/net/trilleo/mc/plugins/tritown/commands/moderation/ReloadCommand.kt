package net.trilleo.mc.plugins.tritown.commands.moderation

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

/**
 * Reloads the plugin configuration and translations from disk.
 *
 * Registered as `/tritown reload` and requires the
 * `tritown.reload` permission.
 */
class ReloadCommand(private val plugin: JavaPlugin) : PluginCommand(
    name = "reload",
    description = "Reload the plugin configuration and translations",
    permission = "tritown.reload"
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val main = plugin as? Main
        if (main == null) {
            sender.sendPrefixed(sender.tr("command.reload.error"))
            return true
        }
        main.reload()
        sender.sendPrefixed(sender.tr("command.reload.done"))
        return true
    }
}
