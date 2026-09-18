package net.trilleo.mc.plugins.tritown.commands.economy

import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.BaltopCache
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
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
            sender.sendPrefixed(sender.tr("command.baltop.empty"))
            return true
        }

        val pages = ceil(snapshot.entries.size / perPage.toDouble()).toInt()
        val page = (args.firstOrNull()?.toIntOrNull() ?: 1).coerceIn(1, pages)
        val currency = primaryCurrency()

        sender.sendPrefixed(sender.tr("command.baltop.header", "page" to page, "pages" to pages))
        snapshot.entries
            .asSequence()
            .drop((page - 1) * perPage)
            .take(perPage)
            .forEachIndexed { offset, entry ->
                val rank = (page - 1) * perPage + offset + 1
                val tag = if (entry.type.isGovernment) {
                    sender.tr("command.baltop.tag", "type" to accountTypeName(sender, entry.type))
                } else {
                    ""
                }
                sender.sendPrefixed(
                    sender.tr(
                        "command.baltop.entry",
                        "rank" to rank,
                        "name" to displayName(entry.name, entry.type),
                        "tag" to tag,
                        "balance" to display(entry.balance, currency),
                    )
                )
            }
        sender.sendPrefixed(sender.tr("command.baltop.updated", "age" to describeAge(sender, snapshot.refreshedAt)))
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
    private fun describeAge(sender: CommandSender, refreshedAt: Long): String {
        if (refreshedAt == 0L) return sender.tr("command.baltop.age.now")
        val seconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - refreshedAt)
        return when {
            seconds < 10L -> sender.tr("command.baltop.age.now")
            seconds < 60L -> sender.tr("command.baltop.age.seconds", "seconds" to seconds)
            seconds < 120L -> sender.tr("command.baltop.age.minute")
            else -> sender.tr("command.baltop.age.minutes", "minutes" to seconds / 60L)
        }
    }
}
