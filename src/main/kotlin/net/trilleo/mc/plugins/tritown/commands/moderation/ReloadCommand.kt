package net.trilleo.mc.plugins.tritown.commands.moderation

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

/**
 * Reloads the plugin configuration from disk.
 *
 * Registered as `/tritown reload` and requires the
 * `tritown.reload` permission.
 */
class ReloadCommand(private val plugin: JavaPlugin) : PluginCommand(
    name = "reload",
    description = "Reload the plugin configuration",
    permission = "tritown.reload"
) {
    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val main = plugin as? Main
        if (main == null) {
            sender.sendPrefixed("<red>Error: Plugin instance type mismatch. Unable to reload configuration.")
            return true
        }
        main.reload()
        sender.sendPrefixed("Configuration reloaded!")
        return true
    }
}
