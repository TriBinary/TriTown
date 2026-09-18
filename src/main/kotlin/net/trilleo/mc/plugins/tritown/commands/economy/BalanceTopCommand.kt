package net.trilleo.mc.plugins.tritown.commands.economy

import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.BaltopCache
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import org.bukkit.command.CommandSender
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** Lists the richest accounts, a page at a time. */
class BalanceTopCommand : PluginCommand(
    name = "baltop",
    description = "List the richest players",
    usage = "/baltop [page]",
    aliases = listOf("balancetop"),
    permission = "tritown.economy.baltop",
    isMainCommand = topLevelAliases(),
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (!requireEconomy(sender)) return true

        val settings = EconomySettings.snapshot
        val perPage = settings.baltopSize.coerceIn(1, 45)
        val snapshot = BaltopCache.current

        if (snapshot.entries.isEmpty()) {
            sender.sendPrefixed("<gray>Nobody has any money yet.")
            return true
        }

        val pages = ceil(snapshot.entries.size / perPage.toDouble()).toInt()
        val page = (args.firstOrNull()?.toIntOrNull() ?: 1).coerceIn(1, pages)
        val currency = primaryCurrency()

        sender.sendPrefixed("<gold><bold>Richest accounts</bold></gold> <gray>(page $page/$pages)")
        snapshot.entries
            .asSequence()
            .drop((page - 1) * perPage)
            .take(perPage)
            .forEachIndexed { offset, entry ->
                val rank = (page - 1) * perPage + offset + 1
                val tag = if (entry.type.isGovernment) " <gray>[${entry.type.name.lowercase()}]</gray>" else ""
                sender.sendPrefixed(
                    "<gray>$rank.</gray> <white>${displayName(entry.name, entry.type)}</white>$tag " +
                        "<dark_gray>-</dark_gray> <white>${display(entry.balance, currency)}</white>"
                )
            }
        sender.sendPrefixed("<dark_gray>Updated ${describeAge(snapshot.refreshedAt)}.")
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> =
        if (args.size == 1) listOf("1", "2", "3").filter { it.startsWith(args[0]) } else emptyList()

    /**
     * How long ago the leaderboard was built.
     *
     * It is rebuilt by the background flush, so it is deliberately a little
     * behind; saying so is better than implying it is live.
     */
    private fun describeAge(refreshedAt: Long): String {
        if (refreshedAt == 0L) return "just now"
        val seconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - refreshedAt)
        return when {
            seconds < 10L -> "just now"
            seconds < 60L -> "$seconds seconds ago"
            seconds < 120L -> "a minute ago"
            else -> "${seconds / 60L} minutes ago"
        }
    }
}
