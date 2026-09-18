package net.trilleo.mc.plugins.tritown.commands.economy

import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Shows a player their balance, or someone else's with the extra permission. */
class BalanceCommand : PluginCommand(
    name = "balance",
    description = "Check your balance",
    usage = "/balance [player]",
    aliases = listOf("bal", "money"),
    permission = "tritown.economy.balance",
    isMainCommand = topLevelAliases(),
) {

    override val extraPermissions = listOf(OTHERS_PERMISSION)

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (!requireEconomy(sender)) return true
        val currency = primaryCurrency()

        if (args.isEmpty()) {
            val player = sender as? Player ?: run {
                sender.sendPrefixed("<red>Specify a player: <white>/balance <player></white>")
                return true
            }
            val balance = EconomyService.balance(player.uniqueId, currency)
            sender.sendPrefixed("Your balance: <white>${display(balance, currency)}</white>")
            return true
        }

        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            sender.sendPrefixed("<red>You don't have permission to check another player's balance!")
            return true
        }

        val account = resolveOrTell(sender, args[0]) ?: return true
        val balance = account.balance(currency.id)
        sender.sendPrefixed("<white>${displayName(account)}</white>: <white>${display(balance, currency)}</white>")
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> =
        if (args.size == 1 && sender.hasPermission(OTHERS_PERMISSION)) {
            EconomyService.suggestAccountNames(args[0], NAME_SUGGESTION_LIMIT)
        } else {
            emptyList()
        }

    private companion object {
        const val OTHERS_PERMISSION = "tritown.economy.balance.others"
    }
}
