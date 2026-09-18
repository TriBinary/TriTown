package net.trilleo.mc.plugins.tritown.commands.economy

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyResult
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.economy.MoneyAccount
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Administrative access to balances. */
class EconomyAdminCommand : PluginCommand(
    name = "eco",
    description = "Administer player balances",
    usage = "/eco <give|take|set|reset|info|flush> [player] [amount]",
    aliases = listOf("economy"),
    permission = PERMISSION,
    isMainCommand = topLevelAliases(),
) {

    override val extraPermissions = ACTIONS.map { permissionFor(it) }

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (!requireEconomy(sender)) return true

        val action = args.firstOrNull()?.lowercase()
        if (action == null || action !in ACTIONS) {
            sendUsage(sender)
            return true
        }

        if (!sender.hasPermission(permissionFor(action))) {
            sender.sendPrefixed("<red>You don't have permission to use <white>/eco $action</white>!")
            return true
        }

        when (action) {
            "flush" -> flush(sender)
            "info" -> withTarget(sender, args) { info(sender, it) }
            "reset" -> withTarget(sender, args) { reset(sender, it) }
            else -> withAmount(sender, args, action)
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> = when (args.size) {
        1 -> ACTIONS
            .filter { sender.hasPermission(permissionFor(it)) && it.startsWith(args[0], ignoreCase = true) }

        2 -> if (args[0].equals("flush", ignoreCase = true)) {
            emptyList()
        } else {
            EconomyService.suggestAccountNames(args[1], NAME_SUGGESTION_LIMIT)
        }

        3 -> if (args[0].lowercase() in AMOUNT_ACTIONS) {
            listOf("10", "100", "1000").filter { it.startsWith(args[2]) }
        } else {
            emptyList()
        }

        else -> emptyList()
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private fun flush(sender: CommandSender) {
        val plugin = Main.instance
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable { EconomyService.flush() })
        sender.sendPrefixed("Writing changed accounts to disk.")
    }

    private fun info(sender: CommandSender, account: MoneyAccount) {
        sender.sendPrefixed("<gold><bold>${displayName(account)}</bold></gold>")
        sender.sendPrefixed("<gray>UUID:</gray> <white>${account.uuid}</white>")
        sender.sendPrefixed("<gray>Type:</gray> <white>${account.type.name.lowercase()}</white>")
        sender.sendPrefixed("<gray>Created:</gray> <white>${timestamp(account.createdAt)}</white>")
        sender.sendPrefixed("<gray>Updated:</gray> <white>${timestamp(account.updatedAt)}</white>")

        for (currency in CurrencyRegistry.all()) {
            val balance = account.balance(currency.id)
            sender.sendPrefixed("<gray>${currency.plural}:</gray> <white>${display(balance, currency)}</white>")
        }
    }

    private fun reset(sender: CommandSender, account: MoneyAccount) {
        val currency = primaryCurrency()
        val starting = currency.of(EconomySettings.snapshot.startingBalance)
        apply(sender, account, EconomyService.setBalance(account, currency, starting)) {
            "Reset <white>${displayName(account)}</white> to <white>${display(it, currency)}</white>"
        }
    }

    private fun withAmount(sender: CommandSender, args: Array<out String>, action: String) {
        if (args.size < 3) {
            sender.sendPrefixed("<red>Usage: <white>/eco $action <player> <amount></white>")
            return
        }

        val currency = CurrencyRegistry.getOrPrimary(args.getOrNull(3))
        if (args.size > 3 && CurrencyRegistry.get(args[3]) == null) {
            sender.sendPrefixed("<red>Unknown currency <white>${args[3]}</white>.")
            return
        }

        val amount = parseAmount(args[2], currency) ?: run {
            sender.sendPrefixed("<red>That is not a valid amount.")
            return
        }

        // Unlike /pay, an admin may open an account for anyone the server can name.
        val account = EconomyService.ensureAccount(args[1]) ?: run {
            resolveOrTell(sender, args[1])
            return
        }

        val result = when (action) {
            "give" -> EconomyService.deposit(account, currency, amount)
            "take" -> EconomyService.withdraw(account, currency, amount)
            else -> EconomyService.setBalance(account, currency, amount)
        }

        apply(sender, account, result) { balance ->
            val moved = display(amount, currency)
            val target = displayName(account)
            val summary = when (action) {
                "give" -> "Gave <white>$moved</white> to <white>$target</white>"
                "take" -> "Took <white>$moved</white> from <white>$target</white>"
                else -> "Set <white>$target</white> to <white>$moved</white>"
            }
            "$summary. Balance: <white>${display(balance, currency)}</white>"
        }
    }

    // ── Shared ──────────────────────────────────────────────────────────

    private fun withTarget(sender: CommandSender, args: Array<out String>, block: (MoneyAccount) -> Unit) {
        val name = args.getOrNull(1) ?: run {
            sender.sendPrefixed("<red>Usage: <white>/eco ${args[0].lowercase()} <player></white>")
            return
        }
        block(resolveOrTell(sender, name) ?: return)
    }

    /** Reports the outcome, and writes the account out now rather than waiting for the next flush. */
    private fun apply(
        sender: CommandSender,
        account: MoneyAccount,
        result: EconomyResult,
        message: (Money) -> String,
    ) {
        when (result) {
            is EconomyResult.Success -> {
                sender.sendPrefixed(message(result.balance))
                EconomyService.flushAccount(account.uuid)
                Bukkit.getPlayer(account.uuid)?.let { player ->
                    if (player != sender) player.sendPrefixed("<gray>Your balance was changed by an administrator.")
                }
            }

            is EconomyResult.Failure -> sender.sendPrefixed("<red>${result.reason}.")
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendPrefixed("<gold><bold>Economy administration</bold></gold>")
        sender.sendPrefixed("<white>/eco give <player> <amount> [currency]</white> <gray>- add money")
        sender.sendPrefixed("<white>/eco take <player> <amount> [currency]</white> <gray>- remove money")
        sender.sendPrefixed("<white>/eco set <player> <amount> [currency]</white> <gray>- set a balance outright")
        sender.sendPrefixed("<white>/eco reset <player></white> <gray>- back to the starting balance")
        sender.sendPrefixed("<white>/eco info <player></white> <gray>- account details")
        sender.sendPrefixed("<white>/eco flush</white> <gray>- write changed accounts to disk now")
    }

    private fun timestamp(epochMillis: Long): String =
        if (epochMillis <= 0L) "unknown" else TIMESTAMP.format(Instant.ofEpochMilli(epochMillis))

    private companion object {
        const val PERMISSION = "tritown.economy.admin"
        val ACTIONS = listOf("give", "take", "set", "reset", "info", "flush")
        val AMOUNT_ACTIONS = setOf("give", "take", "set")
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault())

        fun permissionFor(action: String) = "$PERMISSION.$action"
    }
}
