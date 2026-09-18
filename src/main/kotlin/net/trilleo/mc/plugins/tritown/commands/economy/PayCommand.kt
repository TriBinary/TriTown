package net.trilleo.mc.plugins.tritown.commands.economy

import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.EconomyContext
import net.trilleo.mc.plugins.tritown.economy.EconomyResult
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.economy.MoneyAccount
import net.trilleo.mc.plugins.tritown.enums.AccountType
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Sends money from one player to another. */
class PayCommand : PluginCommand(
    name = "pay",
    description = "Pay another player",
    usage = "/pay <player> <amount>",
    permission = "tritown.economy.pay",
    isMainCommand = topLevelAliases(),
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (!requireEconomy(sender)) return true

        val player = sender as? Player ?: run {
            sender.sendPrefixed("<red>Only players can pay. Use <white>/eco give</white> from the console.")
            return true
        }

        if (args.size < 2) {
            sender.sendPrefixed("<red>Usage: <white>/pay <player> <amount></white>")
            return true
        }

        val currency = primaryCurrency()
        val amount = parseAmount(args[1], currency) ?: run {
            sender.sendPrefixed("<red>That is not a valid amount.")
            return true
        }

        val minimum = currency.of(EconomySettings.snapshot.minimumPayment)
        if (amount < minimum) {
            sender.sendPrefixed("<red>The smallest payment is <white>${display(minimum, currency)}</white>.")
            return true
        }

        val recipient = resolveRecipient(sender, args[0]) ?: return true
        if (recipient.uuid == player.uniqueId) {
            sender.sendPrefixed("<red>You cannot pay yourself.")
            return true
        }
        if (recipient.type.isGovernment) {
            sender.sendPrefixed("<red>Use Towny's own deposit commands to pay a town or nation bank.")
            return true
        }

        val from = EconomyService.ensurePlayerAccount(player) ?: run {
            sender.sendPrefixed("<red>Your account could not be opened.")
            return true
        }

        val result = EconomyContext.command("Player payment") {
            EconomyService.transfer(from, recipient, currency, amount)
        }

        when (result) {
            is EconomyResult.Success -> {
                val paid = display(result.moved, currency)
                sender.sendPrefixed(
                    "Paid <white>$paid</white> to <white>${displayName(recipient)}</white>. " +
                        "Balance: <white>${display(result.balance, currency)}</white>"
                )
                Bukkit.getPlayer(recipient.uuid)?.sendPrefixed(
                    "<white>${displayName(from)}</white> paid you <white>$paid</white>."
                )
                EconomyService.flushAccount(from.uuid)
                EconomyService.flushAccount(recipient.uuid)
            }

            is EconomyResult.Failure -> sender.sendPrefixed("<red>${result.reason}.")
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> = when (args.size) {
        1 -> Bukkit.getOnlinePlayers()
            .map { it.name }
            .filter { it.startsWith(args[0], ignoreCase = true) && it != (sender as? Player)?.name }

        2 -> listOf("10", "100", "1000").filter { it.startsWith(args[1]) }
        else -> emptyList()
    }

    /**
     * Resolves the recipient without creating anything, so a typo cannot open a
     * wallet that will never be used again.
     */
    private fun resolveRecipient(sender: CommandSender, name: String): MoneyAccount? {
        val account = resolveOrTell(sender, name) ?: return null
        if (account.type == AccountType.SERVER) {
            sender.sendPrefixed("<red>You cannot pay the server account.")
            return null
        }
        return account
    }
}
