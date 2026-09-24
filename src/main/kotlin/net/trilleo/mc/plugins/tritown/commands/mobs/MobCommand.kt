package net.trilleo.mc.plugins.tritown.commands.mobs

import net.trilleo.mc.plugins.tritown.combat.BalanceSimulator
import net.trilleo.mc.plugins.tritown.combat.Combat
import net.trilleo.mc.plugins.tritown.combat.CombatFormat
import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.mobs.MobZones
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Looking into how dangerous the world is: the mob level where you stand, and
 * what the balance in force makes of each level.
 */
class MobCommand : PluginCommand(
    name = "mob",
    description = "Inspect mob levels and the combat balance",
    usage = "/tritown mob <level|balance>",
    permission = PERMISSION,
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            LEVEL -> level(sender)
            BALANCE -> balance(sender)
            else -> sender.sendPrefixed(sender.tr("command.mob.usage"))
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> =
        if (args.size == 1) listOf(LEVEL, BALANCE).filter { it.startsWith(args[0], ignoreCase = true) } else emptyList()

    /** Why a mob spawning where the sender stands would be the level it would. */
    private fun level(sender: CommandSender) {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.mob.players-only"))
            return
        }
        if (!Combat.isActive(player.world)) {
            player.sendPrefixed(player.tr("command.mob.inactive"))
            return
        }

        val reading = MobZones.read(player.location)
        player.sendPrefixed(player.tr("command.mob.level", "level" to reading.level))
        val reason = when {
            reading.town -> player.tr("command.mob.level-town")
            reading.ring == 0 -> player.tr("command.mob.level-hub")
            else -> player.tr("command.mob.level-ring", "ring" to reading.ring)
        }
        player.sendMessage(ComponentUtil.parse(reason))
        if (reading.night) player.sendMessage(ComponentUtil.parse(player.tr("command.mob.level-night")))
        if (reading.deep) player.sendMessage(ComponentUtil.parse(player.tr("command.mob.level-deep")))
    }

    /** A zombie's health and hit at a spread of levels, and how a diamond kit fares against it. */
    private fun balance(sender: CommandSender) {
        val balance = ContentRegistry.balance
        sender.sendPrefixed(sender.tr("command.mob.balance-header"))
        for (level in TABLE_LEVELS) {
            val row = BalanceSimulator.row(balance, level, BalanceSimulator.Kit.DIAMOND)
            sender.sendMessage(
                ComponentUtil.parse(
                    sender.tr(
                        "command.mob.balance-row",
                        "level" to level,
                        "health" to CombatFormat.number(row.foeHealth),
                        "hit" to CombatFormat.number(row.foeHit),
                        "hits" to row.hitsToKill,
                        "percent" to CombatFormat.number(row.shareTaken * 100.0),
                    )
                )
            )
        }
    }

    companion object {
        const val PERMISSION = "tritown.mob.admin"

        private const val LEVEL = "level"
        private const val BALANCE = "balance"
        private val TABLE_LEVELS = listOf(1, 5, 10, 15, 20, 25, 30, 40, 50, 60)
    }
}
