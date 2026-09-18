package net.trilleo.mc.plugins.tritown.commands.economy

import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.EconomyContext
import net.trilleo.mc.plugins.tritown.economy.EconomyResult
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.economy.MoneyAccount
import net.trilleo.mc.plugins.tritown.economy.TransactionReason
import net.trilleo.mc.plugins.tritown.enums.AccountType
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
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
            sender.sendPrefixed(sender.tr("command.pay.players-only"))
            return true
        }

        if (args.size < 2) {
            sender.sendPrefixed(sender.tr("command.pay.usage"))
            return true
        }

        val currency = primaryCurrency()
        val amount = parseAmount(args[1], currency) ?: run {
            sender.sendPrefixed(sender.tr("common.invalid-amount"))
            return true
        }

        val minimum = currency.of(EconomySettings.snapshot.minimumPayment)
        if (amount < minimum) {
            sender.sendPrefixed(sender.tr("command.pay.minimum", "amount" to display(minimum, currency)))
            return true
        }

        val recipient = resolveRecipient(sender, args[0]) ?: return true
        if (recipient.uuid == player.uniqueId) {
            sender.sendPrefixed(sender.tr("command.pay.self"))
            return true
        }
        if (recipient.type.isGovernment) {
            sender.sendPrefixed(sender.tr("command.pay.government"))
            return true
        }

        val from = EconomyService.ensurePlayerAccount(player) ?: run {
            sender.sendPrefixed(sender.tr("command.pay.no-account"))
            return true
        }

        val result = EconomyContext.command(TransactionReason.PAYMENT) {
            EconomyService.transfer(from, recipient, currency, amount)
        }

        when (result) {
            is EconomyResult.Success -> {
                val paid = display(result.moved, currency)
                sender.sendPrefixed(
                    sender.tr(
                        "command.pay.sent",
                        "amount" to paid,
                        "name" to displayName(recipient),
                        "balance" to display(result.balance, currency),
                    )
                )
                Bukkit.getPlayer(recipient.uuid)?.let { target ->
                    target.sendPrefixed(
                        target.tr("command.pay.received", "name" to displayName(from), "amount" to paid)
                    )
                }
                EconomyService.flushAccount(from.uuid)
                EconomyService.flushAccount(recipient.uuid)
            }

            is EconomyResult.Failure -> sender.sendPrefixed(sender.tr("common.error", "message" to sender.tr(result.key)))
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
            sender.sendPrefixed(sender.tr("command.pay.server-account"))
            return null
        }
        return account
    }
}
