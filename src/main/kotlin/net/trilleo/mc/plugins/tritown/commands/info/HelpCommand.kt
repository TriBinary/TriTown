package net.trilleo.mc.plugins.tritown.commands.info

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tritown.registration.CommandRegistrar
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.Lang
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender

class HelpCommand : PluginCommand(
    name = "help",
    description = "Show all available commands"
) {
    companion object {
        // Matches the total width of the header: "========= TriTown Commands ========="
        private const val HEADER_WIDTH = 36
    }

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val categorized = CommandRegistrar.getCommandsByCategory()

        // Header
        sender.sendMessage(
            Component.text("=========", NamedTextColor.GOLD)
                .append(
                    Component.text(" ${sender.tr("command.help.header")} ", NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD)
                )
                .append(Component.text("=========", NamedTextColor.GOLD))
        )

        for ((category, commands) in categorized) {
            // Category header
            sender.sendMessage(
                Component.text("» ", NamedTextColor.GOLD)
                    .append(
                        Component.text(
                            Lang.find(sender, "command.category.${category.lowercase()}") ?: category,
                            NamedTextColor.YELLOW
                        )
                            .decorate(TextDecoration.BOLD)
                    )
            )

            // Commands in this category, sorted alphabetically
            for (info in commands) {
                val commandText = if (info.isSubCommand) {
                    "/tritown ${info.command.name}"
                } else {
                    "/${info.command.name}"
                }
                sender.sendMessage(
                    Component.text("  $commandText", NamedTextColor.GREEN)
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(
                            Component.text(
                                Lang.find(sender, "command.${info.command.name}.description")
                                    ?: info.command.description,
                                NamedTextColor.GRAY
                            )
                        )
                )
            }
        }

        // Footer
        sender.sendMessage(Component.text("=".repeat(HEADER_WIDTH), NamedTextColor.GOLD))

        return true
    }
}