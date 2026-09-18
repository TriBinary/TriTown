package net.trilleo.mc.plugins.tritown.commands.scoreboard

import net.trilleo.mc.plugins.tritown.config.ScoreboardSettings
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.scoreboard.ScoreboardService
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Turns the sidebar on and off, and pins a board for testing.
 *
 * Registered as `/tritown scoreboard`. It declares no permission on purpose:
 * [net.trilleo.mc.plugins.tritown.registration.PermissionRegistrar] registers
 * every declared node as operator-only, which is the wrong default for a
 * command every player is meant to use. Pinning a board is the exception and
 * checks [ADMIN_PERMISSION] itself.
 */
class ScoreboardCommand : PluginCommand(
    name = "scoreboard",
    description = "Show or hide the sidebar",
    usage = "/tritown scoreboard [on|off|board <id>]",
) {

    override val extraPermissions = listOf(ADMIN_PERMISSION)

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendPrefixed(sender.tr("command.scoreboard.players-only"))
            return true
        }

        if (!ScoreboardService.isRunning) {
            sender.sendPrefixed(sender.tr("command.scoreboard.unavailable"))
            return true
        }

        when (args.firstOrNull()?.lowercase()) {
            null -> report(sender, ScoreboardService.toggle(sender))
            "on" -> {
                ScoreboardService.setEnabledFor(sender, true)
                report(sender, true)
            }

            "off" -> {
                ScoreboardService.setEnabledFor(sender, false)
                report(sender, false)
            }

            "board" -> pin(sender, args.getOrNull(1))
            else -> sender.sendPrefixed(sender.tr("command.scoreboard.usage"))
        }

        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        val admin = sender.hasPermission(ADMIN_PERMISSION)

        return when {
            args.size == 1 -> (listOf("on", "off") + if (admin) listOf("board") else emptyList())
                .filter { it.startsWith(args[0].lowercase()) }

            args.size == 2 && args[0].equals("board", ignoreCase = true) && admin ->
                (ScoreboardSettings.snapshot.boards.map { it.id } + RESET)
                    .filter { it.startsWith(args[1], ignoreCase = true) }

            else -> emptyList()
        }
    }

    private fun report(player: Player, enabled: Boolean) {
        player.sendPrefixed(player.tr(if (enabled) "command.scoreboard.shown" else "command.scoreboard.hidden"))
    }

    private fun pin(player: Player, id: String?) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendPrefixed(player.tr("command.no-permission"))
            return
        }

        if (id == null || id.equals(RESET, ignoreCase = true)) {
            ScoreboardService.pin(player, null)
            player.sendPrefixed(player.tr("command.scoreboard.unpinned"))
            return
        }

        val board = ScoreboardSettings.snapshot.board(id)
        if (board == null) {
            player.sendPrefixed(player.tr("command.scoreboard.unknown-board", "board" to ComponentUtil.escape(id)))
            return
        }

        ScoreboardService.pin(player, board.id)
        player.sendPrefixed(player.tr("command.scoreboard.pinned", "board" to ComponentUtil.escape(board.id)))
    }

    companion object {

        /** Lets a moderator pin one board, to check a layout without arranging the situation that triggers it. */
        const val ADMIN_PERMISSION: String = "tritown.scoreboard.admin"

        /** The argument that clears a pinned board. */
        private const val RESET = "auto"
    }
}
