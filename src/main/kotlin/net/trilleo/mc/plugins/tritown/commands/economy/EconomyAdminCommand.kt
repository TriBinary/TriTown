package net.trilleo.mc.plugins.tritown.commands.economy

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.*
import net.trilleo.mc.plugins.tritown.guis.economy.TransactionHistoryGUI
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/** Administrative access to balances. */
class EconomyAdminCommand : PluginCommand(
    name = "eco",
    description = "Administer player balances",
    usage = "/eco <give|take|set|reset|info|history|flush> [player] [amount]",
    aliases = listOf("economy"),
    permission = PERMISSION,
    isMainCommand = topLevelAliases(),
) {

    override val extraPermissions = ACTIONS.map { permissionFor(it) } + HISTORY_OTHERS_PERMISSION

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (!requireEconomy(sender)) return true

        val action = args.firstOrNull()?.lowercase()
        if (action == null || action !in ACTIONS) {
            sendUsage(sender)
            return true
        }

        if (!sender.hasPermission(permissionFor(action))) {
            sender.sendPrefixed(sender.tr("command.eco.no-permission-action", "action" to action))
            return true
        }

        when (action) {
            "flush" -> flush(sender)
            "info" -> withTarget(sender, args) { info(sender, it) }
            "history" -> history(sender, args)
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
        sender.sendPrefixed(sender.tr("command.eco.flushing"))
    }

    /** Opens the history view, defaulting to the sender's own account. */
    private fun history(sender: CommandSender, args: Array<out String>) {
        val viewer = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.eco.history.players-only"))
            return
        }

        val account = if (args.size < 2) {
            EconomyService.ensurePlayerAccount(viewer)
        } else {
            if (!sender.hasPermission(HISTORY_OTHERS_PERMISSION)) {
                sender.sendPrefixed(sender.tr("command.eco.history.no-permission-others"))
                return
            }
            resolveOrTell(sender, args[1])
        } ?: return

        val gui = GUIManager.getGUI(TransactionHistoryGUI.ID) as? TransactionHistoryGUI ?: run {
            sender.sendPrefixed(sender.tr("command.eco.history.unavailable"))
            return
        }
        gui.open(viewer, account)
    }

    private fun info(sender: CommandSender, account: MoneyAccount) {
        sender.sendPrefixed(sender.tr("command.eco.info.header", "name" to displayName(account)))
        sender.sendPrefixed(sender.tr("command.eco.info.uuid", "uuid" to account.uuid))
        sender.sendPrefixed(sender.tr("command.eco.info.type", "type" to accountTypeName(sender, account.type)))
        sender.sendPrefixed(sender.tr("command.eco.info.created", "date" to timestamp(sender, account.createdAt)))
        sender.sendPrefixed(sender.tr("command.eco.info.updated", "date" to timestamp(sender, account.updatedAt)))

        for (currency in CurrencyRegistry.all()) {
            val balance = account.balance(currency.id)
            sender.sendPrefixed(
                sender.tr(
                    "command.eco.info.balance",
                    "currency" to currency.plural,
                    "balance" to display(balance, currency),
                )
            )
        }
    }

    private fun reset(sender: CommandSender, account: MoneyAccount) {
        val currency = primaryCurrency()
        val starting = currency.of(EconomySettings.snapshot.startingBalance)
        val result =
            EconomyContext.command(TransactionReason.of(TransactionReason.ADMIN_RESET, "admin" to sender.name)) {
                EconomyService.setBalance(account, currency, starting)
            }
        apply(sender, account, result) {
            sender.tr(
                "command.eco.reset",
                "name" to displayName(account),
                "balance" to display(it, currency),
            )
        }
    }

    private fun withAmount(sender: CommandSender, args: Array<out String>, action: String) {
        if (args.size < 3) {
            sender.sendPrefixed(sender.tr("command.eco.amount-usage", "action" to action))
            return
        }

        val currency = CurrencyRegistry.getOrPrimary(args.getOrNull(3))
        if (args.size > 3 && CurrencyRegistry.get(args[3]) == null) {
            sender.sendPrefixed(sender.tr("command.eco.unknown-currency", "currency" to args[3]))
            return
        }

        val amount = parseAmount(args[2], currency) ?: run {
            sender.sendPrefixed(sender.tr("common.invalid-amount"))
            return
        }

        // Unlike /pay, an admin may open an account for anyone the server can name.
        val account = EconomyService.ensureAccount(args[1]) ?: run {
            resolveOrTell(sender, args[1])
            return
        }

        val result = EconomyContext.command(TransactionReason.of(TransactionReason.ADMIN_SET, "admin" to sender.name)) {
            when (action) {
                "give" -> EconomyService.deposit(account, currency, amount)
                "take" -> EconomyService.withdraw(account, currency, amount)
                else -> EconomyService.setBalance(account, currency, amount)
            }
        }

        apply(sender, account, result) { balance ->
            val key = when (action) {
                "give" -> "command.eco.gave"
                "take" -> "command.eco.took"
                else -> "command.eco.set"
            }
            sender.tr(
                key,
                "amount" to display(amount, currency),
                "name" to displayName(account),
                "balance" to display(balance, currency),
            )
        }
    }

    // ── Shared ──────────────────────────────────────────────────────────

    private fun withTarget(sender: CommandSender, args: Array<out String>, block: (MoneyAccount) -> Unit) {
        val name = args.getOrNull(1) ?: run {
            sender.sendPrefixed(sender.tr("command.eco.target-usage", "action" to args[0].lowercase()))
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
                    if (player != sender) player.sendPrefixed(player.tr("command.eco.changed-by-admin"))
                }
            }

            is EconomyResult.Failure -> sender.sendPrefixed(
                sender.tr(
                    "common.error",
                    "message" to sender.tr(result.key)
                )
            )
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendPrefixed(sender.tr("command.eco.usage.header"))
        sender.sendPrefixed(sender.tr("command.eco.usage.give"))
        sender.sendPrefixed(sender.tr("command.eco.usage.take"))
        sender.sendPrefixed(sender.tr("command.eco.usage.set"))
        sender.sendPrefixed(sender.tr("command.eco.usage.reset"))
        sender.sendPrefixed(sender.tr("command.eco.usage.info"))
        sender.sendPrefixed(sender.tr("command.eco.usage.history"))
        sender.sendPrefixed(sender.tr("command.eco.usage.flush"))
    }

    private fun timestamp(sender: CommandSender, epochMillis: Long): String =
        if (epochMillis <= 0L) sender.tr("common.unknown") else TIMESTAMP.format(Instant.ofEpochMilli(epochMillis))

    private companion object {
        const val PERMISSION = "tritown.economy.admin"
        const val HISTORY_OTHERS_PERMISSION = "tritown.economy.admin.history.others"
        val ACTIONS = listOf("give", "take", "set", "reset", "info", "history", "flush")
        val AMOUNT_ACTIONS = setOf("give", "take", "set")
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault())

        fun permissionFor(action: String) = "$PERMISSION.$action"
    }
}
