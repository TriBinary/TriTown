package net.trilleo.mc.plugins.tritown.commands.items

import net.trilleo.mc.plugins.tritown.content.ContentItems
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * Hands out the items `items.yml` defines, for testing and for rewards the
 * server gives by hand. What does not fit is dropped as the player's own.
 */
class ItemCommand : PluginCommand(
    name = "item",
    description = "Give out the server's own items",
    usage = "/tritown item <give <player> <id> [amount]|list>",
    permission = PERMISSION,
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            GIVE -> give(sender, args)
            LIST -> list(sender)
            else -> sender.sendPrefixed(sender.tr("command.item.usage"))
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        val options = when (args.size) {
            1 -> listOf(GIVE, LIST)
            2 -> if (args[0].equals(GIVE, ignoreCase = true)) Bukkit.getOnlinePlayers().map { it.name } else emptyList()
            3 -> if (args[0].equals(GIVE, ignoreCase = true)) ContentRegistry.items.keys.toList() else emptyList()
            else -> emptyList()
        }
        return options.filter { it.startsWith(args.last(), ignoreCase = true) }
    }

    private fun give(sender: CommandSender, args: Array<out String>) {
        val name = args.getOrNull(1)
        val id = args.getOrNull(2)?.lowercase()
        val amount = args.getOrNull(3)?.toIntOrNull() ?: 1
        if (name == null || id == null || amount !in 1..MAX_AMOUNT) {
            sender.sendPrefixed(sender.tr("command.item.usage"))
            return
        }

        val player = Bukkit.getPlayerExact(name) ?: run {
            sender.sendPrefixed(sender.tr("command.item.not-found", "name" to ComponentUtil.escape(name)))
            return
        }
        val stack = ContentItems.create(id, amount) ?: run {
            sender.sendPrefixed(sender.tr("command.item.unknown", "id" to ComponentUtil.escape(id)))
            return
        }
        InventoryUtil.give(player, listOf(stack))
        sender.sendPrefixed(
            sender.tr("command.item.given", "amount" to amount, "id" to id, "name" to ComponentUtil.escape(player.name))
        )
    }

    private fun list(sender: CommandSender) {
        val ids = ContentRegistry.items.keys
        sender.sendPrefixed(sender.tr("command.item.list", "amount" to ids.size, "ids" to ids.joinToString(", ")))
    }

    companion object {
        const val PERMISSION = "tritown.item.admin"

        private const val GIVE = "give"
        private const val LIST = "list"
        private const val MAX_AMOUNT = 64 * 36
    }
}
